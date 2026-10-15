package com.zwinsight.contract.controller;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.ContractRevision;
import com.zwinsight.contract.domain.dto.ContractRevisionRequest;
import com.zwinsight.contract.service.ContractRevisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/contract/revision")
@RequiredArgsConstructor
@RequiresPermission("contract:view")
public class ContractRevisionController {
    private final ContractRevisionService service;
    @GetMapping public R<List<ContractRevision>> list(@RequestParam Long contractId) { return R.ok(service.list(contractId)); }
    @GetMapping("/{id}") public R<ContractRevision> detail(@PathVariable Long id) { return R.ok(service.detail(id)); }
    @PostMapping @RequiresPermission("contract:contract:edit")
    public R<Long> create(@Valid @RequestBody ContractRevisionRequest request) { return R.ok(service.create(request)); }
    @PostMapping("/{id}/cancel") @RequiresPermission("contract:contract:edit")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }
    @PostMapping("/{id}/submit") @RequiresPermission("contract:contract:submit")
    public R<Void> submit(@PathVariable Long id) { service.submit(id); return R.ok(); }
}
