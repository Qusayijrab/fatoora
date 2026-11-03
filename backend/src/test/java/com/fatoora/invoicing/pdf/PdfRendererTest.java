package com.fatoora.invoicing.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fatoora.invoicing.pdf.DocumentView.LabelValue;
import com.fatoora.invoicing.pdf.DocumentView.LineView;
import com.fatoora.invoicing.pdf.DocumentView.PartyView;
import com.fatoora.invoicing.pdf.DocumentView.TotalRow;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Renders a bilingual invoice without Spring or a database. */
class PdfRendererTest {

    static DocumentView sample() {
        return new DocumentView(
                "Invoice", "فاتورة", "INV-2026-0042", "PAID", "PAID", "مدفوعة", "#2f7a52", false,
                new PartyView("Al-Nada Trading", List.of("Rainbow Street 12", "Amman, Jordan", "Tax no. 0123456  |  الرقم الضريبي")),
                new PartyView("شركة البتراء للتصميم", List.of("Aqaba, Jordan")),
                List.of(new LabelValue("Issued", "تاريخ الإصدار", "3 Nov 2026"), new LabelValue("Due", "تاريخ الاستحقاق", "3 Dec 2026")),
                List.of(new LineView("Brand identity design — تصميم الهوية", "1", "850.000", "16%", "850.000"),
                        new LineView("Bilingual website copy", "12 hour", "25.000", "16%", "300.000")),
                List.of(new TotalRow("Subtotal", "المجموع الفرعي", "1,150.000", false),
                        new TotalRow("Tax", "الضريبة", "184.000", false),
                        new TotalRow("Total", "الإجمالي", "JOD 1,334.000", false),
                        new TotalRow("Balance due", "الرصيد المستحق", "JOD 0.000", true)),
                "Thank you for your business.\nشكراً لتعاملكم معنا", "Bank transfer within 30 days.", "JOD");
    }

    @Test
    void rendersABilingualPdfWithEmbeddedArabicFont() throws Exception {
        byte[] pdf = new PdfRenderer().render("document", sample());

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);

            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("INV-2026-0042", "Al-Nada Trading", "JOD 1,334.000", "184.000", "Balance due");

            Set<String> fonts = new HashSet<>();
            PDPage page = doc.getPage(0);
            for (var name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                fonts.add(font.getName());
            }
            assertThat(fonts).anyMatch(f -> f.contains("Tajawal"));

            // A PNG preview for humans: target/pdf-preview/invoice.png
            Path dir = Files.createDirectories(Path.of("target", "pdf-preview"));
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", new File(dir.toFile(), "invoice.png"));
        }
    }
}
