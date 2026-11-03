package com.fatoora.invoicing.tax;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Role;
import com.fatoora.invoicing.tax.TaxRateDtos.TaxRateRequest;
import com.fatoora.invoicing.tax.TaxRateDtos.TaxRateResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Any member can read tax rates; only admins and owners change them. */
@Service
public class TaxRateService {

    private final TaxRateRepository rates;
    private final AuditService audit;

    public TaxRateService(TaxRateRepository rates, AuditService audit) {
        this.rates = rates;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TaxRateResponse> list(boolean includeArchived) {
        TenantContext.require();
        var list = includeArchived ? rates.findAllByOrderByArchivedAscNameAsc() : rates.findByArchivedFalseOrderByNameAsc();
        return list.stream().map(TaxRateResponse::from).toList();
    }

    @Transactional
    public TaxRateResponse create(TaxRateRequest request) {
        TenantContext.require().requireRole(Role.ADMIN);
        if (rates.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("A tax rate with this name already exists");
        }
        TaxRate rate = rates.save(new TaxRate(request.name(), request.rate()));
        if (request.isDefault()) {
            makeDefault(rate);
        }
        audit.record("tax_rate", rate.getId(), "created", "Tax rate " + rate.getName() + " created",
                Map.of("rate", rate.getRate().toPlainString()));
        return TaxRateResponse.from(rate);
    }

    @Transactional
    public TaxRateResponse update(UUID id, TaxRateRequest request) {
        TenantContext.require().requireRole(Role.ADMIN);
        TaxRate rate = find(id);
        if (!rate.getName().equalsIgnoreCase(request.name().trim()) && rates.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("A tax rate with this name already exists");
        }
        rate.update(request.name(), request.rate());
        if (request.isDefault() && !rate.isDefault()) {
            makeDefault(rate);
        } else if (!request.isDefault() && rate.isDefault()) {
            rate.setDefault(false);
        }
        audit.record("tax_rate", rate.getId(), "updated", "Tax rate " + rate.getName() + " updated",
                Map.of("rate", rate.getRate().toPlainString()));
        return TaxRateResponse.from(rate);
    }

    @Transactional
    public TaxRateResponse setArchived(UUID id, boolean archived) {
        TenantContext.require().requireRole(Role.ADMIN);
        TaxRate rate = find(id);
        rate.setArchived(archived);
        audit.record("tax_rate", rate.getId(), archived ? "archived" : "restored",
                "Tax rate " + rate.getName() + (archived ? " archived" : " restored"), Map.of());
        return TaxRateResponse.from(rate);
    }

    /** Package-visible for products and invoices: a rate from another org is simply "not found". */
    public TaxRate find(UUID id) {
        return rates.findById(id).orElseThrow(() -> ApiException.notFound("Tax rate not found"));
    }

    /** Only one default per org (a partial unique index enforces it): clear the old one first. */
    private void makeDefault(TaxRate rate) {
        rates.findByIsDefaultTrue().filter(old -> !old.getId().equals(rate.getId())).ifPresent(old -> {
            old.setDefault(false);
            rates.flush();
        });
        rate.setDefault(true);
    }
}
