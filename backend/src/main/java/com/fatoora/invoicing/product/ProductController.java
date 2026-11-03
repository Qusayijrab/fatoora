package com.fatoora.invoicing.product;

import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.product.ProductDtos.ProductRequest;
import com.fatoora.invoicing.product.ProductDtos.ProductResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(q, includeArchived, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public ProductResponse archive(@PathVariable UUID id) {
        return service.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    public ProductResponse restore(@PathVariable UUID id) {
        return service.setArchived(id, false);
    }
}
