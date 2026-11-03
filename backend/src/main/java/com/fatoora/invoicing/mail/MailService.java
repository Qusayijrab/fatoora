package com.fatoora.invoicing.mail;

import com.fatoora.invoicing.common.error.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends HTML emails rendered from templates/mail/*.html. SMTP is Mailpit locally (open
 * http://localhost:8025 to read what was sent) and any SMTP service in production.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public record Attachment(String filename, byte[] content, String contentType) {}

    public record OutgoingMail(
            String to, String replyTo, String senderName, String subject, String template, Map<String, Object> model,
            Attachment attachment) {}

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final MailProperties properties;

    public MailService(JavaMailSender sender, TemplateEngine templates, MailProperties properties) {
        this.sender = sender;
        this.templates = templates;
        this.properties = properties;
    }

    public void send(OutgoingMail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, mail.attachment() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.from(), mail.senderName(), StandardCharsets.UTF_8.name()));
            helper.setTo(mail.to());
            if (mail.replyTo() != null) {
                helper.setReplyTo(mail.replyTo());
            }
            helper.setSubject(mail.subject());
            Context context = new Context();
            context.setVariables(mail.model());
            context.setVariable("publicUrl", properties.publicUrl());
            helper.setText(templates.process("mail/" + mail.template(), context), true);
            if (mail.attachment() != null) {
                helper.addAttachment(mail.attachment().filename(), new ByteArrayResource(mail.attachment().content()),
                        mail.attachment().contentType());
            }
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("Sending email to {} failed", mail.to(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The email could not be sent. Please try again in a moment.");
        }
    }
}
