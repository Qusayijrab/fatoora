package com.fatoora.invoicing.tax;

import com.fatoora.invoicing.tax.TaxRateDtos.TaxRateRequest;
import com.fatoora.invoicing.tax.TaxRateDtos.TaxRateResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tax-rates")
public class TaxRateController {

    private final TaxRateService service;

    public TaxRateController(TaxRateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaxRateResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxRateResponse create(@Valid @RequestBody TaxRateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TaxRateResponse update(@PathVariable UUID id, @Valid @RequestBody TaxRateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public TaxRateResponse archive(@PathVariable UUID id) {
        return service.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    public TaxRateResponse restore(@PathVariable UUID id) {
        return service.setArchived(id, false);
    }
}
