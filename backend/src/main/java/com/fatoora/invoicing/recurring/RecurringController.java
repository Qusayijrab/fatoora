package com.fatoora.invoicing.recurring;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.jobs.JobsService;
import com.fatoora.invoicing.recurring.RecurringDtos.RecurringRequest;
import com.fatoora.invoicing.recurring.RecurringDtos.RecurringResponse;
import com.fatoora.invoicing.recurring.RecurringDtos.RunResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring")
public class RecurringController {

    private final RecurringService service;
    private final JobsService jobs;

    public RecurringController(RecurringService service, JobsService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @GetMapping
    public List<RecurringResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RecurringResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse create(@Valid @RequestBody RecurringRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RecurringResponse update(@PathVariable UUID id, @Valid @RequestBody RecurringRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/pause")
    public RecurringResponse pause(@PathVariable UUID id) {
        return service.pause(id);
    }

    @PostMapping("/{id}/resume")
    public RecurringResponse resume(@PathVariable UUID id) {
        return service.resume(id);
    }

    @PostMapping("/{id}/end")
    public RecurringResponse end(@PathVariable UUID id) {
        return service.end(id);
    }

    @GetMapping("/{id}/runs")
    public List<RunResponse> runs(@PathVariable UUID id) {
        return service.runs(id);
    }

    /** Generates the next period's invoice right now (same path as the scheduled job). */
    @PostMapping("/{id}/generate-now")
    public Map<String, UUID> generateNow(@PathVariable UUID id) {
        UUID invoiceId = jobs.generateNow(id);
        if (invoiceId == null) {
            throw ApiException.conflict("Nothing to generate");
        }
        return Map.of("invoiceId", invoiceId);
    }
}
