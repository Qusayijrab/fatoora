package com.fatoora.invoicing;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against a throwaway Testcontainers Postgres (no docker compose needed). */
public class TestInvoicingApplication {

    public static void main(String[] args) {
        SpringApplication.from(InvoicingApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
