package com.zwinsight.security.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private Long tenantId;
    private String tenantName;
    private List<String> roles;
    private List<String> permissions;
}
