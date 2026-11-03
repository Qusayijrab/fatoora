package com.fatoora.invoicing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InvoicingApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoicingApplication.class, args);
    }
}
