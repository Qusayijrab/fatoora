package com.fatoora.invoicing.dashboard;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The money picture of one organization, all in its base currency.
 *
 * <p>Foreign-currency invoices are converted with the rate frozen when they were issued
 * (amount x total_base / total, per invoice), never with today's rate: the dashboard for
 * last March shows the same numbers forever. Plain SQL, always filtered by the current
 * organization's id.
 */
@Service
public class OverviewService {

    public record MonthPoint(String month, MoneyDto invoiced, MoneyDto collected) {}

    public record AgingBucket(String label, MoneyDto amount, long count) {}

    public record ClientBalance(UUID clientId, String name, MoneyDto outstanding, long invoices) {}

    public record Activity(String summary, String entityType, UUID entityId, Instant at) {}

    public record Overview(
            String baseCurrency,
            MoneyDto outstanding,
            long openInvoices,
            MoneyDto overdue,
            long overdueInvoices,
            MoneyDto collectedThisMonth,
            MoneyDto invoicedThisMonth,
            long drafts,
            long quotesAwaitingAnswer,
            List<MonthPoint> months,
            List<AgingBucket> aging,
            List<ClientBalance> topClients,
            List<Activity> activity) {}

    /** Balance of one invoice converted with its own frozen rate, in base minor units. */
    private static final String BALANCE_BASE = """
            ROUND((i.total_minor - i.amount_paid_minor - i.amount_credited_minor + i.amount_refunded_minor)::numeric
                  * i.total_base_minor / NULLIF(i.total_minor, 0))""";

    private static final String OPEN = """
            i.organization_id = ? AND i.kind = 'INVOICE' AND i.base_currency = ?
            AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')""";

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private final Clock clock;

    public OverviewService(JdbcTemplate jdbc, OrganizationService organizations, Clock clock) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        UUID org = TenantContext.require().organizationId();
        Organization o = organizations.currentEntity();
        String base = o.getDefaultCurrency();
        LocalDate today = organizations.today(o, clock);
        YearMonth thisMonth = YearMonth.from(today);

        long[] open = sumCount("SELECT COALESCE(SUM(" + BALANCE_BASE + "), 0), COUNT(*) FROM invoices i WHERE " + OPEN,
                org, base);
        long[] overdue = sumCount("SELECT COALESCE(SUM(" + BALANCE_BASE + "), 0), COUNT(*) FROM invoices i WHERE " + OPEN
                + " AND i.status = 'OVERDUE'", org, base);

        // Last 12 months, oldest first, with zero months included.
        YearMonth first = thisMonth.minusMonths(11);
        Map<YearMonth, long[]> series = new LinkedHashMap<>();
        for (int k = 0; k < 12; k++) {
            series.put(first.plusMonths(k), new long[2]);
        }
        jdbc.query("""
                SELECT date_trunc('month', i.issue_date)::date, SUM(i.total_base_minor)
                FROM invoices i
                WHERE i.organization_id = ? AND i.kind = 'INVOICE' AND i.base_currency = ?
                  AND i.status NOT IN ('DRAFT', 'VOID') AND i.issue_date >= ?
                GROUP BY 1
                """, rs -> {
            long[] point = series.get(YearMonth.from(rs.getObject(1, LocalDate.class)));
            if (point != null) {
                point[0] = rs.getLong(2);
            }
        }, org, base, first.atDay(1));
        jdbc.query("""
                SELECT date_trunc('month', p.paid_on)::date,
                       SUM(CASE WHEN p.kind = 'REFUND' THEN -1 ELSE 1 END
                           * ROUND(p.amount_minor::numeric * i.total_base_minor / NULLIF(i.total_minor, 0)))
                FROM payments p JOIN invoices i ON i.id = p.invoice_id
                WHERE p.organization_id = ? AND i.base_currency = ? AND p.paid_on >= ?
                GROUP BY 1
                """, rs -> {
            long[] point = series.get(YearMonth.from(rs.getObject(1, LocalDate.class)));
            if (point != null) {
                point[1] = rs.getLong(2);
            }
        }, org, base, first.atDay(1));
        List<MonthPoint> months = new ArrayList<>();
        series.forEach((m, v) -> months.add(new MonthPoint(m.toString(), money(v[0], base), money(v[1], base))));
        long[] current = series.get(thisMonth);

        // Aging of what is still owed, by days past due (organization time zone).
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (String label : List.of("Not yet due", "1–30 days", "31–60 days", "61–90 days", "Over 90 days")) {
            buckets.put(label, new long[2]);
        }
        jdbc.query("SELECT CASE WHEN i.due_date IS NULL OR i.due_date >= ? THEN 0 "
                + "WHEN ? - i.due_date <= 30 THEN 1 WHEN ? - i.due_date <= 60 THEN 2 "
                + "WHEN ? - i.due_date <= 90 THEN 3 ELSE 4 END AS bucket, "
                + "COALESCE(SUM(" + BALANCE_BASE + "), 0), COUNT(*) FROM invoices i WHERE " + OPEN + " GROUP BY 1",
                rs -> {
                    long[] b = new ArrayList<>(buckets.values()).get(rs.getInt(1));
                    b[0] = rs.getLong(2);
                    b[1] = rs.getLong(3);
                }, today, today, today, today, org, base);
        List<AgingBucket> aging = new ArrayList<>();
        buckets.forEach((label, v) -> aging.add(new AgingBucket(label, money(v[0], base), v[1])));

        List<ClientBalance> topClients = jdbc.query("SELECT i.client_id, MAX(i.bill_to_name), SUM(" + BALANCE_BASE + ") AS owed, "
                + "COUNT(*) FROM invoices i WHERE " + OPEN + " GROUP BY i.client_id ORDER BY owed DESC LIMIT 5",
                (rs, n) -> new ClientBalance(rs.getObject(1, UUID.class), rs.getString(2), money(rs.getLong(3), base),
                        rs.getLong(4)), org, base);

        long drafts = count("SELECT COUNT(*) FROM invoices WHERE organization_id = ? AND status = 'DRAFT' AND kind = 'INVOICE'", org);
        long awaiting = count("SELECT COUNT(*) FROM quotes WHERE organization_id = ? AND status = 'SENT'", org);
        List<Activity> activity = jdbc.query("""
                SELECT summary, entity_type, entity_id, created_at FROM audit_events
                WHERE organization_id = ? ORDER BY created_at DESC LIMIT 8
                """, (rs, n) -> new Activity(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getTimestamp(4).toInstant()), org);

        return new Overview(base, money(open[0], base), open[1], money(overdue[0], base), overdue[1],
                money(current[1], base), money(current[0], base), drafts, awaiting, months, aging, topClients, activity);
    }

    private long[] sumCount(String sql, Object... args) {
        return jdbc.queryForObject(sql, (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}, args);
    }

    private long count(String sql, UUID org) {
        Long value = jdbc.queryForObject(sql, Long.class, org);
        return value == null ? 0 : value;
    }

    private static MoneyDto money(long minor, String currency) {
        return MoneyDto.from(Money.ofMinor(minor, currency));
    }
}
