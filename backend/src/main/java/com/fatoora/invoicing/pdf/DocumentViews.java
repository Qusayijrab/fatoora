package com.fatoora.invoicing.pdf;

import com.fatoora.invoicing.document.Party;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceStatus;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.pdf.DocumentView.LabelValue;
import com.fatoora.invoicing.pdf.DocumentView.TotalRow;
import com.fatoora.invoicing.quote.Quote;
import com.fatoora.invoicing.quote.QuoteStatus;
import java.util.ArrayList;
import java.util.List;

/** Builds the printable view of invoices, credit notes and quotes. */
public final class DocumentViews {

    private DocumentViews() {}

    public static DocumentView of(Invoice i, Organization liveSeller) {
        boolean creditNote = i.getKind() == InvoiceKind.CREDIT_NOTE;
        boolean draft = i.getStatus() == InvoiceStatus.DRAFT;
        // Drafts show the live seller and client; issued documents print their frozen snapshot.
        Party seller = i.getSeller() != null ? i.getSeller() : Party.of(liveSeller);
        Party billTo = i.getBillTo() != null ? i.getBillTo() : Party.of(i.getClient());

        List<LabelValue> dates = new ArrayList<>();
        dates.add(new LabelValue("Issued", "تاريخ الإصدار", DocumentView.date(i.getIssueDate())));
        if (i.getDueDate() != null) {
            dates.add(new LabelValue("Due", "تاريخ الاستحقاق", DocumentView.date(i.getDueDate())));
        }
        if (creditNote && i.getCreditedInvoice() != null) {
            dates.add(new LabelValue("Credits", "يخص", i.getCreditedInvoice().getNumber()));
        }

        List<TotalRow> totals = new ArrayList<>();
        totals.add(new TotalRow("Subtotal", "المجموع الفرعي", DocumentView.amount(i.getSubtotal()), false));
        totals.add(new TotalRow("Tax", "الضريبة", DocumentView.amount(i.getTax()), false));
        totals.add(new TotalRow("Total", "الإجمالي", DocumentView.money(i.getTotal()), !creditNote && draft || creditNote));
        if (!creditNote && !draft) {
            if (i.getAmountPaid().isPositive()) {
                totals.add(new TotalRow("Paid", "المدفوع", "− " + DocumentView.amount(i.getAmountPaid()), false));
            }
            if (i.getAmountCredited().isPositive()) {
                totals.add(new TotalRow("Credited", "إشعارات دائنة", "− " + DocumentView.amount(i.getAmountCredited()), false));
            }
            totals.add(new TotalRow("Balance due", "الرصيد المستحق",
                    i.getStatus() == InvoiceStatus.VOID ? "—" : DocumentView.money(i.balanceDue()), true));
        }

        String[] stamp = invoiceStamp(i.getStatus());
        return new DocumentView(
                creditNote ? "Credit note" : "Invoice",
                creditNote ? "إشعار دائن" : "فاتورة",
                i.getNumber(), i.getStatus().name(), stamp[0], stamp[1], stamp[2], draft,
                DocumentView.party(seller, liveSeller.getName()),
                DocumentView.party(billTo, i.getClient().getName()),
                dates,
                i.getLines().stream().map(l -> DocumentView.line(l, i.getCurrency())).toList(),
                totals, i.getNotes(), i.getTerms(), i.getCurrency());
    }

    public static DocumentView of(Quote q, Organization seller) {
        Party billTo = q.getBillTo() != null ? q.getBillTo() : Party.of(q.getClient());
        List<LabelValue> dates = new ArrayList<>();
        dates.add(new LabelValue("Date", "التاريخ", DocumentView.date(q.getIssueDate())));
        if (q.getValidUntil() != null) {
            dates.add(new LabelValue("Valid until", "صالح حتى", DocumentView.date(q.getValidUntil())));
        }
        List<TotalRow> totals = List.of(
                new TotalRow("Subtotal", "المجموع الفرعي", DocumentView.amount(q.getSubtotal()), false),
                new TotalRow("Tax", "الضريبة", DocumentView.amount(q.getTax()), false),
                new TotalRow("Total", "الإجمالي", DocumentView.money(q.getTotal()), true));
        String[] stamp = quoteStamp(q.getStatus());
        return new DocumentView("Quote", "عرض سعر", q.getNumber(), q.getStatus().name(), stamp[0], stamp[1], stamp[2],
                q.getStatus() == QuoteStatus.DRAFT, DocumentView.party(Party.of(seller), seller.getName()),
                DocumentView.party(billTo, q.getClient().getName()), dates,
                q.getLines().stream().map(l -> DocumentView.line(l, q.getCurrency())).toList(),
                totals, q.getNotes(), q.getTerms(), q.getCurrency());
    }

    // Stamp inks match the app's document-state colors.
    private static final String GREEN = "#2f7a52";
    private static final String TEAL = "#0e7490";
    private static final String RED = "#b5322f";
    private static final String GRAPHITE = "#71717a";
    private static final String AMBER = "#a8741c";

    private static String[] invoiceStamp(InvoiceStatus s) {
        return switch (s) {
            case DRAFT, ISSUED -> new String[] {null, null, null};
            case PARTIALLY_PAID -> new String[] {"PARTLY PAID", "مدفوعة جزئياً", TEAL};
            case PAID -> new String[] {"PAID", "مدفوعة", GREEN};
            case OVERDUE -> new String[] {"OVERDUE", "متأخرة", RED};
            case VOID -> new String[] {"VOID", "ملغاة", GRAPHITE};
        };
    }

    private static String[] quoteStamp(QuoteStatus s) {
        return switch (s) {
            case DRAFT, SENT -> new String[] {null, null, null};
            case ACCEPTED -> new String[] {"ACCEPTED", "مقبول", GREEN};
            case REJECTED -> new String[] {"REJECTED", "مرفوض", RED};
            case EXPIRED -> new String[] {"EXPIRED", "منتهي", AMBER};
        };
    }
}
