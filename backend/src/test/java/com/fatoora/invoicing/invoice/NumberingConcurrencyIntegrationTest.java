package com.fatoora.invoicing.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fatoora.invoicing.IntegrationTest;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CLAUDE.md rule 3: numbers must be sequential and unique even when many invoices are
 * issued at the same moment. 20 drafts, issued by 10 threads released together.
 */
class NumberingConcurrencyIntegrationTest extends IntegrationTest {

    @Autowired
    InvoiceService invoices;

    @Test
    void concurrentIssuesGetUniqueGaplessNumbers() throws Exception {
        Account a = register("Concurrency Co");
        String client = createClient(a, "Busy Client");
        List<UUID> drafts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            drafts.add(UUID.fromString(read(postJson("/api/v1/invoices", a.token(),
                    document(client, "JOD", line("Line " + i, "1", "10", null))), "$.id")));
        }

        CurrentTenant tenant = new CurrentTenant(a.orgId(), a.userId(), Role.OWNER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (UUID id : drafts) {
                Callable<String> issue = () -> {
                    TenantContext.set(tenant);
                    try {
                        start.await();
                        return invoices.issue(id).number();
                    } finally {
                        TenantContext.clear();
                    }
                };
                results.add(pool.submit(issue));
            }
            start.countDown();

            List<String> numbers = new ArrayList<>();
            for (Future<String> f : results) {
                numbers.add(f.get());
            }
            Collections.sort(numbers);

            List<String> expected = IntStream.rangeClosed(1, 20).mapToObj(n -> "INV-2026-%04d".formatted(n)).toList();
            assertThat(numbers).containsExactlyElementsOf(expected);
        } finally {
            pool.shutdownNow();
        }
    }
}
