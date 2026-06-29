package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysVersion;
import com.zwinsight.system.dto.VersionCreateRequest;
import com.zwinsight.system.service.VersionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统版本管理接口。
 *
 * <p>提供版本记录创建、版本列表(按发布日期降序)、当前最新版本查询能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/version")
@RequiredArgsConstructor
public class VersionController {

    private final VersionManagerService versionManagerService;

    /**
     * 创建版本记录。
     *
     * @param request 版本创建请求(versionNo / releaseDate / changelog)
     * @return 创建后的版本记录
     */
    @PostMapping
    public R<SysVersion> create(@RequestBody VersionCreateRequest request) {
        SysVersion version = versionManagerService.create(
                request.getVersionNo(), request.getReleaseDate(), request.getChangelog());
        return R.ok("创建成功", version);
    }

    /**
     * 版本列表，按发布日期降序。
     *
     * @return 版本记录列表
     */
    @GetMapping("/list")
    public R<List<SysVersion>> list() {
        return R.ok(versionManagerService.listAll());
    }

    /**
     * 当前最新版本。
     *
     * @return 最新版本记录，若无则为 null
     */
    @GetMapping("/current")
    public R<SysVersion> current() {
        return R.ok(versionManagerService.getCurrent());
    }
}
