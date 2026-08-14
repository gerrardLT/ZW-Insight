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
        fileInfo.setStorageType("MINIO");
        fileInfo.setBusinessType(businessType);
        fileInfo.setBusinessId(businessId);
        fileInfo.setProjectId(projectId);
        fileInfoMapper.insert(fileInfo);

        return fileInfo;
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
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }

        minioService.delete(fileInfo.getFilePath());
        fileInfoMapper.deleteById(id);
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
}
