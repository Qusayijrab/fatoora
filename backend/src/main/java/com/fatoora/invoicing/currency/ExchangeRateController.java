package com.fatoora.invoicing.currency;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.organization.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manual exchange rates (admins) and a preview of the rate an invoice would freeze today. */
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    public record RateRequest(
            @NotNull @CurrencyCode String currency,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 10) BigDecimal rate,
            LocalDate effectiveDate) {}

    public record RateResponse(UUID id, String currency, String baseCurrency, String rate, LocalDate effectiveDate) {
        static RateResponse from(ExchangeRate r) {
            return new RateResponse(r.getId(), r.getCurrency(), r.getBaseCurrency(),
                    r.getRate().stripTrailingZeros().toPlainString(), r.getEffectiveDate());
        }
    }

    public record RatePreview(String currency, String baseCurrency, String rate, String source, LocalDate date) {}

    private final ExchangeRateRepository rates;
    private final FxService fx;
    private final OrganizationService organizations;
    private final AuditService audit;
    private final Clock clock;

    public ExchangeRateController(ExchangeRateRepository rates, FxService fx, OrganizationService organizations,
            AuditService audit, Clock clock) {
        this.rates = rates;
        this.fx = fx;
        this.organizations = organizations;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RateResponse> list() {
        TenantContext.require();
        return rates.findAllByOrderByCurrencyAscEffectiveDateDesc().stream().map(RateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RateResponse create(@Valid @RequestBody RateRequest request) {
        TenantContext.require().requireRole(Role.ADMIN);
        var org = organizations.currentEntity();
        if (request.currency().equals(org.getDefaultCurrency())) {
            throw ApiException.badRequest(request.currency() + " is already your base currency");
        }
        LocalDate from = request.effectiveDate() != null ? request.effectiveDate() : organizations.today(org, clock);
        ExchangeRate saved = rates.save(new ExchangeRate(request.currency(), org.getDefaultCurrency(), request.rate(), from));
        audit.record("exchange_rate", saved.getId(), "created",
                "Rate 1 " + saved.getCurrency() + " = " + saved.getRate().stripTrailingZeros().toPlainString() + " "
                        + saved.getBaseCurrency() + " from " + from,
                Map.of("rate", saved.getRate().toPlainString()));
        return RateResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        TenantContext.require().requireRole(Role.ADMIN);
        ExchangeRate rate = rates.findById(id).orElseThrow(() -> ApiException.notFound("Rate not found"));
        audit.record("exchange_rate", rate.getId(), "deleted", "Rate for " + rate.getCurrency() + " removed "
                + "(invoices already issued keep their frozen rate)", Map.of());
        rates.delete(rate);
    }

    /** The rate an invoice in {@code currency} would freeze if issued today (or on {@code date}). */
    @GetMapping("/preview")
    @Transactional(readOnly = true)
    public RatePreview preview(@RequestParam String currency, @RequestParam(required = false) LocalDate date) {
        TenantContext.require();
        var org = organizations.currentEntity();
        LocalDate on = date != null ? date : organizations.today(org, clock);
        var rate = fx.find(currency, org.getDefaultCurrency(), on);
        return new RatePreview(currency, org.getDefaultCurrency(),
                rate.map(r -> r.value().stripTrailingZeros().toPlainString()).orElse(null),
                rate.map(FxService.Rate::source).orElse(null), on);
    }
}
