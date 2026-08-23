package io.github.mortogo321.recon.api.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.mortogo321.recon.api.dto.MerchantDtos;
import io.github.mortogo321.recon.api.security.ReconRoles;
import io.github.mortogo321.recon.legacy.gateway.MerchantDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Read-through view of the legacy Oracle merchant master. */
@RestController
@RequestMapping("/api/merchants")
@Tag(name = "Merchants")
public class MerchantController {

    private final MerchantDirectory merchants;

    public MerchantController(MerchantDirectory merchants) {
        this.merchants = merchants;
    }

    @GetMapping("/{merchantId}")
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Merchant master record from the legacy system")
    public ResponseEntity<MerchantDtos.MerchantView> get(@PathVariable String merchantId) {
        return merchants
                .findOptional(merchantId)
                .map(MerchantDtos.MerchantView::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize(ReconRoles.HAS_ANY)
    @Operation(summary = "Search merchants for the console picker")
    public List<MerchantDtos.MerchantView> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mcc,
            @RequestParam(required = false) String acquirerId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "25") @Min(1) @Max(200) int limit) {
        return merchants.search(name, mcc, acquirerId, activeOnly, limit).stream()
                .map(MerchantDtos.MerchantView::of)
                .toList();
    }

    /** Invoked after a merchant master refresh on the legacy side; the cache is otherwise sticky. */
    @DeleteMapping("/cache")
    @PreAuthorize(ReconRoles.HAS_ADMIN)
    @Operation(summary = "Evict the merchant read-through cache")
    public ResponseEntity<Void> evict() {
        merchants.evictAll();
        return ResponseEntity.noContent().build();
    }
}
