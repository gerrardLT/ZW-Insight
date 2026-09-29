package com.zwinsight.system.controller;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.system.domain.dto.ConfigUpdateRequest;
import com.zwinsight.system.domain.vo.SysConfigVO;
import com.zwinsight.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 系统配置接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/config")
@RequiredArgsConstructor
@RequiresPermission("system:view")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    // 默认前端静态资源托管目录（与 Docker 编排挂载对齐）
    private static final String BRAND_STATIC_DIR = "/usr/share/nginx/html/brand";
    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "svg", "webp", "ico");

    /**
     * 按分组查询配置列表
     */
    @GetMapping("/group/{group}")
    public R<List<SysConfigVO>> listByGroup(@PathVariable String group) {
        return R.ok(systemConfigService.listByGroup(group));
    }

    /**
     * 更新配置值
     */
    @PutMapping
    public R<Void> updateConfig(@RequestBody ConfigUpdateRequest request) {
        systemConfigService.updateConfig(request.getConfigKey(), request.getConfigValue());
        return R.ok();
    }

    /**
     * 批量更新配置
     */
    @PutMapping("/batch")
    public R<Void> batchUpdate(@RequestBody List<ConfigUpdateRequest> requests) {
        systemConfigService.batchUpdate(requests);
        return R.ok();
    }

    /**
     * 恢复默认值
     */
    @PostMapping("/{key}/reset")
    public R<Void> resetToDefault(@PathVariable String key) {
        systemConfigService.resetToDefault(key);
        return R.ok();
    }

    /**
     * 上传品牌 Logo / 图标文件
     *
     * @param file     上传的图片文件
     * @param logoType 图标类型：logo (深色模式Logo), logoLight (浅色模式Logo), favicon (浏览器图标)
     * @return 可直接访问的相对静态路径，如 /brand/logo-uuid.png
     */
    @PostMapping("/brand/upload")
    public R<String> uploadBrandImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "logoType", defaultValue = "logo") String logoType) {
        if (file.isEmpty()) {
            throw new BusinessException("上传图片不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            throw new BusinessException("仅支持常见图片格式（png, jpg, jpeg, svg, webp, ico）");
        }

        // Docker 中由 backend/frontend 共享持久卷；目录不可用时必须失败，禁止返回无法访问的假 URL。
        Path targetDir = Paths.get(BRAND_STATIC_DIR);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            log.error("创建品牌图片目录失败: {}", targetDir, e);
            throw new BusinessException("品牌图片存储不可用，请联系管理员");
        }

        String filename = logoType + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        Path targetFile = targetDir.resolve(filename);

        try {
            file.transferTo(targetFile);
            log.info("品牌图标成功落盘: {}", targetFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("保存上传图标失败", e);
            throw new BusinessException("图片保存失败: " + e.getMessage());
        }

        // 返回相对 URL 给前端和数据库存储
        String relativeUrl = "/brand/" + filename;
        return R.ok(relativeUrl);
    }
}
