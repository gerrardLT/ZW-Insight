package com.zwinsight.security.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private String address;
    private Long tenantTypeId;
    private Integer status;
    private LocalDate expireDate;
    private String secretKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
