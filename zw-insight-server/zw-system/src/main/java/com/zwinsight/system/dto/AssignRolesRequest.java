package com.zwinsight.system.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Data
public class AssignRolesRequest {
    @NotNull(message = "角色列表不能为空")
    private List<@NotNull @Positive Long> roleIds;
}
