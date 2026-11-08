package com.fatoora.invoicing.report;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports for the business and its accountant, in the base currency. Foreign-currency
 * documents are converted with the rate frozen on each one (amount x total_base /
 * total), like the dashboard, so a report for a past period never changes.
 */
@Service
public class ReportService {

    /** What each client owes, by how late it is (organization's today). */
    public record AgingRow(UUID clientId, String client, MoneyDto notDue, MoneyDto days1to30, MoneyDto days31to60,
            MoneyDto days61to90, MoneyDto over90, MoneyDto total) {}

    public record Aging(String baseCurrency, LocalDate asOf, List<AgingRow> rows, AgingRow totals) {}

    /** Per tax rate: the taxable amount and the tax, net of credit notes. */
    public record TaxRow(String rate, String name, MoneyDto taxable, MoneyDto tax) {}

    public record TaxSummary(String baseCurrency, LocalDate from, LocalDate to, List<TaxRow> rows, MoneyDto taxable,
            MoneyDto tax, int invoices, int creditNotes) {}

    /** Balance of an open invoice in base minor units, at its own frozen rate. */
    private static final String BALANCE_BASE = """
            ROUND((i.total_minor - i.amount_paid_minor - i.amount_credited_minor + i.amount_refunded_minor)::numeric
                  * i.total_base_minor / NULLIF(i.total_minor, 0))""";

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, OrganizationService organizations, Clock clock) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Aging aging() {
        UUID org = TenantContext.require().organizationId();
        Organization o = organizations.currentEntity();
        String base = o.getDefaultCurrency();
        LocalDate today = organizations.today(o, clock);
        List<AgingRow> rows = new ArrayList<>();
        long[] sum = new long[6];
        jdbc.query("""
                SELECT i.client_id, MAX(COALESCE(i.bill_to_name, c.name)),
                  COALESCE(SUM(CASE WHEN i.due_date IS NULL OR i.due_date >= ? THEN %1$s END), 0),
                  COALESCE(SUM(CASE WHEN ? - i.due_date BETWEEN 1 AND 30 THEN %1$s END), 0),
                  COALESCE(SUM(CASE WHEN ? - i.due_date BETWEEN 31 AND 60 THEN %1$s END), 0),
                  COALESCE(SUM(CASE WHEN ? - i.due_date BETWEEN 61 AND 90 THEN %1$s END), 0),
                  COALESCE(SUM(CASE WHEN ? - i.due_date > 90 THEN %1$s END), 0)
                FROM invoices i JOIN clients c ON c.id = i.client_id
                WHERE i.organization_id = ? AND i.kind = 'INVOICE' AND i.base_currency = ?
                  AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                GROUP BY i.client_id
                """.formatted(BALANCE_BASE), rs -> {
            long[] v = new long[6];
            for (int k = 0; k < 5; k++) {
                v[k] = rs.getLong(3 + k);
                v[5] += v[k];
                sum[k] += v[k];
            }
            sum[5] += v[5];
            rows.add(row(rs.getObject(1, UUID.class), rs.getString(2), v, base));
        }, today, today, today, today, today, org, base);
        rows.sort((a, b) -> Long.compare(b.total().amountMinor(), a.total().amountMinor()));
        return new Aging(base, today, rows, row(null, "Total", sum, base));
    }

    @Transactional(readOnly = true)
    public TaxSummary tax(LocalDate from, LocalDate to) {
        UUID org = TenantContext.require().organizationId();
        Organization o = organizations.currentEntity();
        String base = o.getDefaultCurrency();
        LocalDate end = to == null ? organizations.today(o, clock) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("The start date must be on or before the end date");
        }
        List<TaxRow> rows = new ArrayList<>();
        long[] sum = new long[2];
        jdbc.query("""
                SELECT l.tax_rate, MAX(t.name),
                  SUM(CASE WHEN i.kind = 'CREDIT_NOTE' THEN -1 ELSE 1 END
                      * ROUND(l.subtotal_minor::numeric * i.total_base_minor / NULLIF(i.total_minor, 0))),
                  SUM(CASE WHEN i.kind = 'CREDIT_NOTE' THEN -1 ELSE 1 END
                      * ROUND(l.tax_minor::numeric * i.total_base_minor / NULLIF(i.total_minor, 0)))
                FROM invoice_lines l
                JOIN invoices i ON i.id = l.invoice_id
                LEFT JOIN tax_rates t ON t.id = l.tax_rate_id
                WHERE i.organization_id = ? AND i.base_currency = ? AND i.status NOT IN ('DRAFT', 'VOID')
                  AND i.issue_date BETWEEN ? AND ?
                GROUP BY l.tax_rate
                ORDER BY l.tax_rate DESC
                """, rs -> {
            BigDecimal rate = rs.getBigDecimal(1);
            long taxable = rs.getLong(3);
            long tax = rs.getLong(4);
            sum[0] += taxable;
            sum[1] += tax;
            String name = rate.signum() == 0 ? "No tax" : rs.getString(2) == null ? "Tax" : rs.getString(2);
            rows.add(new TaxRow(rate.stripTrailingZeros().toPlainString(), name, money(taxable, base), money(tax, base)));
        }, org, base, start, end);
        int[] counts = new int[2];
        jdbc.query("""
                SELECT i.kind, COUNT(*) FROM invoices i
                WHERE i.organization_id = ? AND i.base_currency = ? AND i.status NOT IN ('DRAFT', 'VOID')
                  AND i.issue_date BETWEEN ? AND ?
                GROUP BY i.kind
                """, rs -> {
            counts["CREDIT_NOTE".equals(rs.getString(1)) ? 1 : 0] = rs.getInt(2);
        }, org, base, start, end);
        return new TaxSummary(base, start, end, rows, money(sum[0], base), money(sum[1], base), counts[0], counts[1]);
    }

    // ---- CSV (UTF-8 with a byte-order mark so Excel shows Arabic names correctly) ----

    public String agingCsv() {
        Aging a = aging();
        StringBuilder csv = new StringBuilder("﻿Client,Not yet due,1-30 days,31-60 days,61-90 days,Over 90 days,Total ("
                + a.baseCurrency() + ")\r\n");
        for (AgingRow r : a.rows()) {
            line(csv, r.client(), r.notDue(), r.days1to30(), r.days31to60(), r.days61to90(), r.over90(), r.total());
        }
        AgingRow t = a.totals();
        line(csv, "Total", t.notDue(), t.days1to30(), t.days31to60(), t.days61to90(), t.over90(), t.total());
        return csv.toString();
    }

    public String taxCsv(LocalDate from, LocalDate to) {
        TaxSummary s = tax(from, to);
        StringBuilder csv = new StringBuilder("﻿Rate (%),Name,Taxable (" + s.baseCurrency() + "),Tax (" + s.baseCurrency()
                + ")\r\n");
        for (TaxRow r : s.rows()) {
            csv.append(cell(r.rate())).append(',').append(cell(r.name())).append(',').append(r.taxable().amount())
                    .append(',').append(r.tax().amount()).append("\r\n");
        }
        csv.append(",Total,").append(s.taxable().amount()).append(',').append(s.tax().amount()).append("\r\n");
        return csv.toString();
    }

    private static void line(StringBuilder csv, String first, MoneyDto... values) {
        csv.append(cell(first));
        for (MoneyDto v : values) {
            csv.append(',').append(v.amount());
        }
        csv.append("\r\n");
    }

    /**
     * Quotes a CSV cell when needed, and neutralises spreadsheet formulas (a client named
     * "=HYPERLINK(...)" must not run as a formula when the accountant opens the file).
     */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r") || !v.equals(value)
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static AgingRow row(UUID clientId, String name, long[] v, String base) {
        return new AgingRow(clientId, name, money(v[0], base), money(v[1], base), money(v[2], base), money(v[3], base),
                money(v[4], base), money(v[5], base));
    }

    private static MoneyDto money(long minor, String currency) {
        return MoneyDto.from(Money.ofMinor(minor, currency));
    }
}
