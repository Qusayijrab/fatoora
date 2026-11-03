package com.fatoora.invoicing.client;

import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import com.fatoora.invoicing.client.ClientDtos.ClientResponse;
import com.fatoora.invoicing.common.PageResponse;
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
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ClientResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(q, includeArchived, page, size);
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public ClientResponse archive(@PathVariable UUID id) {
        return service.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    public ClientResponse restore(@PathVariable UUID id) {
        return service.setArchived(id, false);
    }
}
