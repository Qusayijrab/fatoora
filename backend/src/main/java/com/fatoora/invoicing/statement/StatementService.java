package com.fatoora.invoicing.statement;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.document.Party;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import com.fatoora.invoicing.pdf.DocumentView;
import com.fatoora.invoicing.pdf.PdfRenderer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statement of account (كشف حساب) for one client, one currency and a period: the balance
 * brought forward, then every invoice and refund (debit) and every payment and credit
 * note (credit) with a running balance. Draft and void invoices are not on it.
 */
@Service
public class StatementService {

    /** One line. Amounts in minor units of the statement currency. */
    record Entry(LocalDate date, int order, String document, String details, long debit, long credit) {}

    public record Statement(Client client, String currency, LocalDate from, LocalDate to, long opening,
            List<Entry> entries, long debits, long credits, long closing, List<String> otherCurrencies) {}

    private final JdbcTemplate jdbc;
    private final ClientService clients;
    private final OrganizationService organizations;
    private final PdfRenderer renderer;
    private final Clock clock;

    public StatementService(JdbcTemplate jdbc, ClientService clients, OrganizationService organizations,
            PdfRenderer renderer, Clock clock) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.organizations = organizations;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID clientId, LocalDate from, LocalDate to, String currency) {
        UUID org = TenantContext.require().organizationId();
        Client client = clients.find(clientId);
        Organization o = organizations.currentEntity();
        LocalDate today = organizations.today(o, clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("The start date must be on or before the end date");
        }
        String cur = currency != null && !currency.isBlank() ? currency.trim().toUpperCase()
                : client.getCurrency() != null ? client.getCurrency() : o.getDefaultCurrency();

        List<Entry> all = new ArrayList<>();
        jdbc.query("""
                SELECT i.kind, i.issue_date, i.number, i.total_minor, i.notes, ci.number
                FROM invoices i LEFT JOIN invoices ci ON ci.id = i.credited_invoice_id
                WHERE i.organization_id = ? AND i.client_id = ? AND i.currency = ?
                  AND i.status NOT IN ('DRAFT', 'VOID') AND i.issue_date <= ?
                """, rs -> {
            boolean credit = "CREDIT_NOTE".equals(rs.getString(1));
            long total = rs.getLong(4);
            all.add(new Entry(rs.getObject(2, LocalDate.class), credit ? 2 : 0, rs.getString(3),
                    credit ? "Credit note" + (rs.getString(6) == null ? "" : " for " + rs.getString(6)) : "Invoice",
                    credit ? 0 : total, credit ? total : 0));
        }, org, clientId, cur, end);
        jdbc.query("""
                SELECT p.kind, p.paid_on, i.number, p.amount_minor, p.method, p.reference
                FROM payments p JOIN invoices i ON i.id = p.invoice_id
                WHERE p.organization_id = ? AND i.client_id = ? AND p.currency = ? AND p.paid_on <= ?
                """, rs -> {
            boolean refund = "REFUND".equals(rs.getString(1));
            long amount = rs.getLong(4);
            String method = rs.getString(5).toLowerCase().replace('_', ' ');
            String ref = rs.getString(6);
            all.add(new Entry(rs.getObject(2, LocalDate.class), refund ? 3 : 1, rs.getString(3),
                    (refund ? "Refund, " : "Payment, ") + method + (ref == null ? "" : " (" + ref + ")"),
                    refund ? amount : 0, refund ? 0 : amount));
        }, org, clientId, cur, end);
        all.sort(Comparator.comparing(Entry::date).thenComparing(Entry::order).thenComparing(Entry::document));

        long opening = 0;
        long debits = 0;
        long credits = 0;
        List<Entry> inPeriod = new ArrayList<>();
        for (Entry e : all) {
            if (e.date().isBefore(start)) {
                opening += e.debit() - e.credit();
            } else {
                inPeriod.add(e);
                debits += e.debit();
                credits += e.credit();
            }
        }
        List<String> others = jdbc.queryForList("""
                SELECT DISTINCT currency FROM invoices
                WHERE organization_id = ? AND client_id = ? AND currency <> ? AND status NOT IN ('DRAFT', 'VOID')
                ORDER BY currency
                """, String.class, org, clientId, cur);
        return new Statement(client, cur, start, end, opening, inPeriod, debits, credits, opening + debits - credits, others);
    }

    @Transactional(readOnly = true)
    public PdfFile pdf(UUID clientId, LocalDate from, LocalDate to, String currency) {
        Statement s = statement(clientId, from, to, currency);
        Organization org = organizations.currentEntity();
        String cur = s.currency();
        List<Row> rows = new ArrayList<>();
        long running = s.opening();
        for (Entry e : s.entries()) {
            running += e.debit() - e.credit();
            rows.add(new Row(DocumentView.date(e.date()), e.document(), e.details(),
                    e.debit() == 0 ? "" : DocumentView.amount(Money.ofMinor(e.debit(), cur)),
                    e.credit() == 0 ? "" : DocumentView.amount(Money.ofMinor(e.credit(), cur)),
                    signed(running, cur)));
        }
        View view = new View(DocumentView.party(Party.of(org), org.getName()), DocumentView.party(Party.of(s.client()),
                s.client().getName()), cur, DocumentView.date(s.from()), DocumentView.date(s.to()),
                DocumentView.date(organizations.today(org, clock)), signed(s.opening(), cur), rows,
                DocumentView.amount(Money.ofMinor(s.debits(), cur)), DocumentView.amount(Money.ofMinor(s.credits(), cur)),
                DocumentView.money(Money.ofMinor(Math.abs(s.closing()), cur)), s.closing() < 0, s.otherCurrencies());
        String name = s.client().getName().replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("(^-|-$)", "");
        return new PdfFile("Statement-" + name + "-" + s.from() + "-to-" + s.to() + ".pdf",
                renderer.renderObject("statement", view));
    }

    private static String signed(long minor, String currency) {
        String v = DocumentView.amount(Money.ofMinor(Math.abs(minor), currency));
        return minor < 0 ? "(" + v + ")" : v;
    }

    /** What the template prints; everything pre-formatted. */
    public record Row(String date, String document, String details, String debit, String credit, String balance) {}

    public record View(DocumentView.PartyView seller, DocumentView.PartyView client, String currency, String from,
            String to, String printed, String opening, List<Row> rows, String debits, String credits, String closing,
            boolean closingInClientsFavour, List<String> otherCurrencies) {}
}
