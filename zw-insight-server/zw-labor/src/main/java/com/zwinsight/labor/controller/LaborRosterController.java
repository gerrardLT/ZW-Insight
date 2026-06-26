package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.service.LaborRosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 劳务花名册接口
 */
@RestController
@RequestMapping("/api/v1/labor/roster")
@RequiredArgsConstructor
public class LaborRosterController {

    private final LaborRosterService rosterService;

    @GetMapping("/page")
    public R<PageResult<BizLaborRoster>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long teamId) {
        return R.ok(rosterService.page(page, size, projectId, teamId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborRoster roster) {
        rosterService.save(roster);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizLaborRoster roster) {
        roster.setId(id);
        rosterService.update(roster);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        rosterService.delete(id);
        return R.ok();
    }

    @PostMapping("/import")
    public R<Integer> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam Long teamId) {
        int count = rosterService.batchImport(file, projectId, teamId);
        return R.ok("成功导入" + count + "条数据", count);
    }
}
