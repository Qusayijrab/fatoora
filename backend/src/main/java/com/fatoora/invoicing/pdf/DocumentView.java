package com.fatoora.invoicing.pdf;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.document.LineFields;
import com.fatoora.invoicing.document.Party;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the PDF template prints, already formatted as strings. The template does no
 * arithmetic and no formatting: what you see here is exactly what lands on paper.
 */
public record DocumentView(
        String titleEn,
        String titleAr,
        String number,
        String status,
        String stampLabel,
        String stampLabelAr,
        String stampColor,
        boolean draft,
        PartyView seller,
        PartyView billTo,
        List<LabelValue> dates,
        List<LineView> lines,
        List<TotalRow> totals,
        String notes,
        String terms,
        String currency) {

    public record PartyView(String name, List<String> lines) {}

    public record LabelValue(String en, String ar, String value) {}

    public record LineView(String description, String quantity, String unitPrice, String tax, String amount) {}

    public record TotalRow(String en, String ar, String value, boolean strong) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    /** "1250.500" -> "1,250.500"; keeps exactly the currency's decimals. */
    public static String amount(Money m) {
        return grouped(m.toMajor(), m.decimals());
    }

    public static String money(Money m) {
        return m.currency() + " " + amount(m);
    }

    static String grouped(BigDecimal value, int decimals) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.').append("0".repeat(decimals));
        }
        DecimalFormat format = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(value);
    }

    public static PartyView party(Party p, String fallbackName) {
        if (p == null || p.getName() == null) {
            return new PartyView(fallbackName, List.of());
        }
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, p.getAddressLine1());
        addIfPresent(lines, p.getAddressLine2());
        String cityLine = String.join(" ", nonBlank(p.getPostalCode(), p.getCity())).trim();
        addIfPresent(lines, String.join(", ", nonBlank(cityLine, p.getRegion(), countryName(p.getCountry()))));
        if (p.getTaxId() != null) {
            lines.add("Tax no. " + p.getTaxId() + "  |  الرقم الضريبي");
        }
        addIfPresent(lines, p.getEmail());
        addIfPresent(lines, p.getPhone());
        return new PartyView(p.getName(), lines);
    }

    public static LineView line(LineFields l, String currency) {
        String qty = l.getQuantity().stripTrailingZeros().toPlainString();
        String rate = l.getTaxRate().stripTrailingZeros().toPlainString();
        return new LineView(
                l.getDescription(),
                l.getUnit() == null ? qty : qty + " " + l.getUnit(),
                amount(l.unitPrice(currency)),
                "0".equals(rate) ? "—" : rate + "%",
                amount(l.subtotal(currency)));
    }

    private static String countryName(String code) {
        if (code == null) {
            return null;
        }
        String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
        return name.isBlank() ? code : name;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v);
            }
        }
        return out;
    }
}
