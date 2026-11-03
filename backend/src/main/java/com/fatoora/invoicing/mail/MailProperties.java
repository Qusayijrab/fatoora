package com.fatoora.invoicing.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.mail.*}: the From address for everything Fatoora sends, and the public URL
 * of the web app (used for links in emails, such as "view and pay online").
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String publicUrl) {}
