package com.fatoora.invoicing.client;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import com.fatoora.invoicing.client.ClientDtos.ClientResponse;
import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every member of the organization can manage clients. */
@Service
public class ClientService {

    private final ClientRepository clients;
    private final AuditService audit;

    public ClientService(ClientRepository clients, AuditService audit) {
        this.clients = clients;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> list(String query, boolean includeArchived, int page, int size) {
        TenantContext.require();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name").ascending());
        return PageResponse.from(clients.findAll(ClientRepository.matching(query, includeArchived), pageable),
                ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        TenantContext.require();
        return ClientResponse.from(find(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        TenantContext.require();
        Client client = clients.save(new Client(request));
        audit.record("client", client.getId(), "created", "Client " + client.getName() + " added", Map.of());
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        TenantContext.require();
        Client client = find(id);
        client.apply(request);
        audit.record("client", client.getId(), "updated", "Client " + client.getName() + " updated", Map.of());
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse setArchived(UUID id, boolean archived) {
        TenantContext.require();
        Client client = find(id);
        client.setArchived(archived);
        audit.record("client", client.getId(), archived ? "archived" : "restored",
                "Client " + client.getName() + (archived ? " archived" : " restored"), Map.of());
        return ClientResponse.from(client);
    }

    /** For quotes and invoices. Another org's client id is simply not found. */
    @Transactional(readOnly = true)
    public Client find(UUID id) {
        return clients.findById(id).orElseThrow(() -> ApiException.notFound("Client not found"));
    }
}
