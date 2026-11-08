package com.fatoora.invoicing.dashboard;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.currency.FxService;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.recurring.RecurringTemplate;
import com.fatoora.invoicing.recurring.RecurringTemplateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money expected in over the next {@link #WEEKS} weeks, in the base currency.
 *
 * <ul>
 *   <li>Open invoices: their balance, expected on the due date plus that client's usual
 *       lateness (median days late over their paid invoices, once they have at least
 *       {@link #MIN_HISTORY}). Anything already late counts in the first week.</li>
 *   <li>Active recurring invoices: each run in the window, expected after its payment
 *       terms plus the same lateness. Foreign currencies use today's rate; without one
 *       the run is left out and counted in {@code skippedRecurring}.</li>
 * </ul>
 * A projection, not a promise: the dashboard says how it is computed.
 */
@Service
public class ForecastService {

    static final int WEEKS = 12;
    static final int MIN_HISTORY = 3;

    public record Week(LocalDate start, MoneyDto invoiced, MoneyDto recurring) {}

    public record Forecast(String baseCurrency, List<Week> weeks, MoneyDto total, MoneyDto alreadyLate,
            int clientsWithHistory, int skippedRecurring) {}

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private final RecurringTemplateRepository templates;
    private final FxService fx;
    private final Clock clock;

    public ForecastService(JdbcTemplate jdbc, OrganizationService organizations, RecurringTemplateRepository templates,
            FxService fx, Clock clock) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.templates = templates;
        this.fx = fx;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Forecast forecast() {
        UUID org = TenantContext.require().organizationId();
        Organization o = organizations.currentEntity();
        String base = o.getDefaultCurrency();
        LocalDate today = organizations.today(o, clock);
        LocalDate end = today.plusWeeks(WEEKS);
        long[] invoiced = new long[WEEKS];
        long[] recurring = new long[WEEKS];
        Map<UUID, Integer> lateness = lateness(org);

        // Open invoices, converted with their own frozen rate.
        long[] late = {0};
        jdbc.query("""
                SELECT i.client_id, i.due_date,
                       ROUND((i.total_minor - i.amount_paid_minor - i.amount_credited_minor + i.amount_refunded_minor)::numeric
                             * i.total_base_minor / NULLIF(i.total_minor, 0))
                FROM invoices i
                WHERE i.organization_id = ? AND i.kind = 'INVOICE' AND i.base_currency = ?
                  AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                """, rs -> {
            UUID client = rs.getObject(1, UUID.class);
            LocalDate due = rs.getObject(2, LocalDate.class);
            long amount = rs.getLong(3);
            LocalDate expected = (due == null ? today : due).plusDays(lateness.getOrDefault(client, 0));
            if (expected.isBefore(today)) {
                late[0] += amount;
                expected = today;
            }
            add(invoiced, today, expected, end, amount);
        }, org, base);

        // Recurring runs inside the window.
        int skipped = 0;
        for (RecurringTemplate t : templates.findAll()) {
            if (t.getStatus() != RecurringTemplate.Status.ACTIVE || t.getNextRunDate() == null) {
                continue;
            }
            Money total = t.getTotal();
            Long perRun = toBase(total, base, today);
            int delay = t.getPaymentTermsDays() + lateness.getOrDefault(t.getClient().getId(), 0);
            for (int k = 0; ; k++) {
                LocalDate run = t.getFrequency().nthDate(t.getStartDate(), t.getRunCount() + k, t.getIntervalCount());
                if (run.isAfter(end) || (t.getEndDate() != null && run.isAfter(t.getEndDate()))) {
                    break;
                }
                LocalDate expected = run.plusDays(delay);
                if (expected.isBefore(end)) {
                    if (perRun == null) {
                        skipped++;
                    } else {
                        add(recurring, today, expected.isBefore(today) ? today : expected, end, perRun);
                    }
                }
            }
        }

        List<Week> weeks = new ArrayList<>(WEEKS);
        long sum = 0;
        for (int w = 0; w < WEEKS; w++) {
            weeks.add(new Week(today.plusWeeks(w), money(invoiced[w], base), money(recurring[w], base)));
            sum += invoiced[w] + recurring[w];
        }
        return new Forecast(base, weeks, money(sum, base), money(late[0], base), lateness.size(), skipped);
    }

    /** Median days late per client over their paid invoices; only clients with enough history. */
    private Map<UUID, Integer> lateness(UUID org) {
        Map<UUID, Integer> out = new HashMap<>();
        jdbc.query("""
                SELECT client_id, percentile_disc(0.5) WITHIN GROUP (ORDER BY days_late), COUNT(*)
                FROM (SELECT i.client_id, GREATEST(0, MAX(p.paid_on) - i.due_date) AS days_late
                      FROM invoices i JOIN payments p ON p.invoice_id = i.id AND p.kind = 'PAYMENT'
                      WHERE i.organization_id = ? AND i.kind = 'INVOICE' AND i.status = 'PAID' AND i.due_date IS NOT NULL
                      GROUP BY i.id, i.client_id, i.due_date) paid
                GROUP BY client_id
                HAVING COUNT(*) >= ?
                """, rs -> {
            out.put(rs.getObject(1, UUID.class), Math.min(90, rs.getInt(2)));
        }, org, MIN_HISTORY);
        return out;
    }

    private Long toBase(Money amount, String base, LocalDate today) {
        if (amount.currency().equals(base)) {
            return amount.amountMinor();
        }
        return fx.find(amount.currency(), base, today).map(r -> amount.convert(base, r.value()).amountMinor()).orElse(null);
    }

    private static void add(long[] buckets, LocalDate today, LocalDate when, LocalDate end, long amount) {
        if (!when.isBefore(end)) {
            return;
        }
        int week = (int) (ChronoUnit.DAYS.between(today, when) / 7);
        buckets[Math.max(0, Math.min(WEEKS - 1, week))] += amount;
    }

    private static MoneyDto money(long minor, String currency) {
        return MoneyDto.from(Money.ofMinor(minor, currency));
    }
}
