package com.zwinsight.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 上传文件大小显式上限（与 application.yml spring.servlet.multipart.max-file-size 同口径 100MB）。
     * 服务层在写 MinIO 之前快速失败，返回友好业务错误而非框架层 413/500。
     */
    static final long MAX_UPLOAD_SIZE_BYTES = 100L * 1024 * 1024;

    /**
     * 危险扩展名黑名单（2026-08-14 审计批次 6 收尾项）：可执行/脚本/服务端页面类文件
     * 一律拒绝。业务上传类型（文档/图片/表格/压缩包）均不在名单内，不影响现有上传流。
     * 黑名单而非白名单：业务文件类型分散（签证照片/合同扫描件/BOQ 表格等），
     * 白名单需逐业务维护易误拦；黑名单只剔除真实危险面。
     */
    static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "jsp", "jspx", "jspa", "jsw", "jsv", "jtml",
            "php", "php3", "php4", "php5", "phtml",
            "asp", "aspx", "asa", "asax", "ascx", "ashx", "asmx", "cer", "cdx",
            "exe", "bat", "cmd", "com", "scr", "pif", "msi", "dll", "hta",
            "sh", "bash", "ps1", "psm1", "vbs", "vbe", "js", "wsf", "jar", "war"
    );

    private final MinioService minioService;
    private final FileInfoMapper fileInfoMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 上传文件
     */
    public FileInfo upload(MultipartFile file, String businessType, Long businessId, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();

        // 显式大小守卫：先于 MinIO 上传快速失败
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new BusinessException("文件大小超过上限（最大 100MB），当前 "
                    + (file.getSize() / 1024 / 1024) + "MB");
        }

        // 危险扩展名黑名单：拒绝可执行/脚本/服务端页面类文件
        String extension = extractExtension(originalName);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：." + extension);
        }

        // 计算 SHA-256 内容哈希（电子档案防篡改与完整性校验）
        String fileHash = calculateSha256(file);

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/";

        // 使用MinioService上传
        String filePath = minioService.upload(file, datePath);

        // 从路径中提取文件名
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

        // 保存文件信息
        FileInfo fileInfo = new FileInfo();
        fileInfo.setOriginalName(originalName);
        fileInfo.setFileName(fileName);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileType(file.getContentType());
        fileInfo.setFileHash(fileHash);
        fileInfo.setStorageType("MINIO");
        fileInfo.setBusinessType(businessType);
        fileInfo.setBusinessId(businessId);
        fileInfo.setProjectId(projectId);
        fileInfoMapper.insert(fileInfo);

        return fileInfo;
    }

    /**
     * 计算上传文件内容的 SHA-256 摘要
     */
    private String calculateSha256(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算文件 SHA-256 哈希失败: {}", file.getOriginalFilename(), e);
            return null;
        }
    }

    /**
     * 提取小写扩展名（无扩展名返回空串，不阻断上传——业务存在无扩展名文件场景）
     */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 删除文件
     */
    public void delete(Long id) {
        FileInfo fileInfo = fileInfoMapper.selectById(id);
        Long tenantId = com.zwinsight.common.config.SecurityContextHolder.getTenantId();
        // 他租户的文件按不存在处理，防止凭文件 ID 越权删除对象存储里的别家文件
        if (fileInfo == null || (tenantId != null && fileInfo.getTenantId() != null
                && !tenantId.equals(fileInfo.getTenantId()))) {
            throw new BusinessException("文件不存在");
        }

        minioService.delete(fileInfo.getFilePath());
        fileInfoMapper.deleteById(id);

        // 删除留痕审计：将文件被删事件广播到统一审计日志
        try {
            eventPublisher.publishEvent(new com.zwinsight.common.event.AuditLogRecordEvent(
                    this, "file_info", id, "deleted",
                    fileInfo.getOriginalName() + " (" + fileInfo.getBusinessType() + ":" + fileInfo.getBusinessId() + ")",
                    "DELETED", fileInfo.getTenantId(),
                    com.zwinsight.common.config.SecurityContextHolder.getUserId(), null));
        } catch (Exception ignored) {
            // 审计写入异常不阻断主流程
        }
    }

    /**
     * 批量回填绑定业务主体（解决表单新建阶段先传文件再生成业务ID的时序断裂）
     */
    public int bindBusiness(List<Long> fileIds, String businessType, Long businessId, Long projectId) {
        if (fileIds == null || fileIds.isEmpty() || businessId == null) {
            return 0;
        }
        Long tenantId = com.zwinsight.common.config.SecurityContextHolder.getTenantId();
        int count = 0;
        for (Long fileId : fileIds) {
            FileInfo f = fileInfoMapper.selectById(fileId);
            if (f != null && (tenantId == null || tenantId.equals(f.getTenantId()))) {
                if (businessType != null) f.setBusinessType(businessType);
                f.setBusinessId(businessId);
                if (projectId != null) f.setProjectId(projectId);
                fileInfoMapper.updateById(f);
                count++;
            }
        }
        return count;
    }

    /**
     * 根据业务查询文件列表
     */
    public List<FileInfo> getByBusiness(String businessType, Long businessId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getBusinessType, businessType)
                .eq(FileInfo::getBusinessId, businessId)
                .orderByDesc(FileInfo::getCreatedAt);
        return fileInfoMapper.selectList(wrapper);
    }

    /**
     * 根据业务主体批量物理清理其附件（用于草稿单据删除时的级联防孤儿）
     */
    public int deleteByBusiness(String businessType, Long businessId) {
        if (businessType == null || businessId == null) {
            return 0;
        }
        List<FileInfo> files = getByBusiness(businessType, businessId);
        int deleted = 0;
        for (FileInfo f : files) {
            try {
                delete(f.getId());
                deleted++;
            } catch (Exception e) {
                log.warn("级联删除附件失败: fileId={}, err={}", f.getId(), e.getMessage());
            }
        }
        return deleted;
    }

    /**
     * 流式打包下载指定项目的全部卷宗附件（ZIP 格式）
     */
    public void packageProjectFiles(Long projectId, jakarta.servlet.http.HttpServletResponse response) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        Long tenantId = com.zwinsight.common.config.SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getProjectId, projectId)
                .eq(tenantId != null, FileInfo::getTenantId, tenantId)
                .orderByDesc(FileInfo::getCreatedAt);
        List<FileInfo> files = fileInfoMapper.selectList(wrapper);
        if (files.isEmpty()) {
            throw new BusinessException("该项目暂无归档附件");
        }

        response.setContentType("application/zip");
        String zipName = "project-archive-" + projectId + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            java.util.Set<String> entryNames = new java.util.HashSet<>();
            byte[] buffer = new byte[8192];
            for (FileInfo f : files) {
                if (f.getFilePath() == null) continue;
                String entryName = f.getOriginalName() != null ? f.getOriginalName() : f.getFileName();
                // 规避重名文件覆盖
                if (entryNames.contains(entryName)) {
                    entryName = f.getId() + "_" + entryName;
                }
                entryNames.add(entryName);

                try (java.io.InputStream is = minioService.download(f.getFilePath())) {
                    if (is != null) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                } catch (Exception e) {
                    log.warn("打包附件读取失败跳过: fileId={}, path={}, err={}", f.getId(), f.getFilePath(), e.getMessage());
                }
            }
            zos.finish();
        } catch (java.io.IOException e) {
            log.error("流式打包下载失败: projectId={}", projectId, e);
            throw new BusinessException("流式打包附件失败: " + e.getMessage());
        }
    }
}
