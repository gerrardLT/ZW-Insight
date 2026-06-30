package com.zwinsight.tender.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizOpenBidRecord;
import com.zwinsight.tender.service.OpenBidRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 开标记录接口
 */
@RestController
@RequestMapping("/api/v1/tender/open-bid")
@RequiredArgsConstructor
public class OpenBidRecordController {

    private final OpenBidRecordService openBidRecordService;

    @PostMapping
    public R<Void> save(@RequestBody BizOpenBidRecord record) {
        openBidRecordService.save(record);
        return R.ok();
    }

    @GetMapping("/{registerId}")
    public R<BizOpenBidRecord> getByRegister(@PathVariable Long registerId) {
        return R.ok(openBidRecordService.getByRegister(registerId));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizOpenBidRecord record) {
        record.setId(id);
        openBidRecordService.update(record);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        openBidRecordService.delete(id);
        return R.ok();
    }
}
