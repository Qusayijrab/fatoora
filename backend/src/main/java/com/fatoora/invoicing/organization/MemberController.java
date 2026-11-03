package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.organization.dto.AddMemberRequest;
import com.fatoora.invoicing.organization.dto.ChangeRoleRequest;
import com.fatoora.invoicing.organization.dto.MemberResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Members of the current organization. Thin on purpose: all rules live in the service. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MembershipService service;

    public MemberController(MembershipService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(@Valid @RequestBody AddMemberRequest request) {
        return service.add(request.email(), request.role());
    }

    @PatchMapping("/{id}")
    public MemberResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        service.remove(id);
    }
}
