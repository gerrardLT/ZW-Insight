package com.zwinsight.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 检查明细手动填写 DTO
 */
@Data
public class InspectionDetailDTO {

    @NotBlank(message = "检查项目名称不能为空")
    @Size(max = 200, message = "项目名称不超过200字符")
    private String itemName;

    @Size(max = 500, message = "检查标准不超过500字符")
    private String checkStandard;

    @Size(max = 300, message = "检查方法不超过300字符")
    private String checkMethod;
}
