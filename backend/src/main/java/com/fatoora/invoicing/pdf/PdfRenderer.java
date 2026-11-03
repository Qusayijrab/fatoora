package com.fatoora.invoicing.pdf;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.TextDirection;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * HTML template -> PDF (CLAUDE.md: OpenHTMLtoPDF, must support Arabic/RTL).
 *
 * <ul>
 *   <li>Templates are strict XHTML, rendered by a dedicated Thymeleaf engine in XML mode,
 *       because the PDF engine parses XML.</li>
 *   <li>Fonts are embedded (Tajawal, OFL): one family with Latin and Arabic glyphs, so a
 *       client name like "شركة الندى" prints correctly on any machine.</li>
 *   <li>ICU's bidi splitter/reorderer handle mixed Arabic + English text and join Arabic
 *       letters into their connected forms.</li>
 * </ul>
 */
@Component
public class PdfRenderer {

    private static final String FONT_DIR = "fonts/tajawal/";

    private final TemplateEngine templates;

    public PdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        // SpringTemplateEngine evaluates expressions with Spring EL (no OGNL dependency).
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setEnableSpringELCompiler(true);
        this.templates = engine;
        this.templates.setTemplateResolver(resolver);
    }

    /** Renders templates/pdf/{template}.html with the given view as variable "doc". */
    public byte[] render(String template, DocumentView view) {
        Context context = new Context();
        context.setVariable("doc", view);
        String html = templates.process(template, context);
        return toPdf(html);
    }

    byte[] toPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(TextDirection.LTR);
            font(builder, "Tajawal-Regular.ttf", 400);
            font(builder, "Tajawal-Medium.ttf", 500);
            font(builder, "Tajawal-Bold.ttf", 700);
            builder.withHtmlContent(xhtml, "classpath:/templates/pdf/");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render PDF", e);
        }
    }

    private static void font(PdfRendererBuilder builder, String file, int weight) {
        builder.useFont(() -> {
            try {
                return new ClassPathResource(FONT_DIR + file).getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "Tajawal", weight, FontStyle.NORMAL, true);
    }
}
