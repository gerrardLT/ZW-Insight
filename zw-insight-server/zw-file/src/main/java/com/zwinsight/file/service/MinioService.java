package com.zwinsight.file.service;

import com.zwinsight.file.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 存储路径（如 "project/docs/"）
     * @return 文件存储路径
     */
    public String upload(MultipartFile file, String path) {
        try {
            ensureBucketExists(minioConfig.getBucket());

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = path + UUID.randomUUID().toString().replace("-", "") + extension;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("文件上传成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败: {}", objectName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件预签名URL（默认7天有效）
     *
     * @param objectName 对象名称
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, 7, TimeUnit.DAYS);
    }

    /**
     * 获取文件预签名URL
     *
     * @param objectName 对象名称
     * @param duration   有效时长
     * @param timeUnit   时间单位
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectName, int duration, TimeUnit timeUnit) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .expiry(duration, timeUnit)
                    .build());
        } catch (Exception e) {
            log.error("获取预签名URL失败: {}", objectName, e);
            throw new RuntimeException("获取预签名URL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
            log.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
            log.info("创建存储桶: {}", bucketName);
        }
    }
}
