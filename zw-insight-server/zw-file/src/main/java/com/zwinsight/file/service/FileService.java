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

/**
 * 文件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

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
