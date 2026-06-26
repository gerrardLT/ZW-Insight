package com.zwinsight.system.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 创建版本记录请求体。
 */
@Data
public class VersionCreateRequest {

    /** 版本号(语义化: x.y.z) */
    private String versionNo;

    /** 发布日期 */
    private LocalDate releaseDate;

    /** 更新日志(Markdown格式) */
    private String changelog;
}
