package com.fatoora.invoicing.product;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.product.ProductDtos.ProductRequest;
import com.fatoora.invoicing.product.ProductDtos.ProductResponse;
import com.fatoora.invoicing.tax.TaxRate;
import com.fatoora.invoicing.tax.TaxRateService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final TaxRateService taxRates;
    private final AuditService audit;

    public ProductService(ProductRepository products, TaxRateService taxRates, AuditService audit) {
        this.products = products;
        this.taxRates = taxRates;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String query, boolean includeArchived, int page, int size) {
        TenantContext.require();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name").ascending());
        return PageResponse.from(products.findAll(ProductRepository.matching(query, includeArchived), pageable),
                ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        TenantContext.require();
        return ProductResponse.from(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        TenantContext.require();
        String sku = Strings.clean(r.sku());
        if (sku != null && products.existsBySku(sku)) {
            throw ApiException.conflict("Another product already uses SKU " + sku);
        }
        Money price = Money.parse(r.unitPrice(), r.currency());
        Product product = products.save(new Product(r.name(), r.description(), sku, r.unit(), price, taxRate(r)));
        audit.record("product", product.getId(), "created", "Product " + product.getName() + " added",
                Map.of("unitPrice", price.toString()));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest r) {
        TenantContext.require();
        Product product = find(id);
        String sku = Strings.clean(r.sku());
        if (sku != null && !sku.equals(product.getSku()) && products.existsBySku(sku)) {
            throw ApiException.conflict("Another product already uses SKU " + sku);
        }
        Money price = Money.parse(r.unitPrice(), r.currency());
        product.update(r.name(), r.description(), sku, r.unit(), price, taxRate(r));
        audit.record("product", product.getId(), "updated", "Product " + product.getName() + " updated",
                Map.of("unitPrice", price.toString()));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse setArchived(UUID id, boolean archived) {
        TenantContext.require();
        Product product = find(id);
        product.setArchived(archived);
        audit.record("product", product.getId(), archived ? "archived" : "restored",
                "Product " + product.getName() + (archived ? " archived" : " restored"), Map.of());
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private TaxRate taxRate(ProductRequest r) {
        if (r.taxRateId() == null) {
            return null;
        }
        TaxRate rate = taxRates.find(r.taxRateId());
        if (rate.isArchived()) {
            throw ApiException.badRequest("That tax rate is archived");
        }
        return rate;
    }
}
