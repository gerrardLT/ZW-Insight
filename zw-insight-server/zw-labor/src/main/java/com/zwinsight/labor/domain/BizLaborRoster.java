package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.desensitize.Desensitize;
import com.zwinsight.common.desensitize.DesensitizeType;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 劳务花名册
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_roster")
public class BizLaborRoster extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 班组ID */
    private Long teamId;

    /** 工人姓名 */
    private String workerName;

    /** 身份证号 */
    @Desensitize(type = DesensitizeType.ID_CARD)
    private String idCard;

    /** 联系电话 */
    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    /** 用工类型（FIXED-固定/TEMPORARY-临时） */
    private String workerType;

    /** 工种（如钢筋工、木工、电工等） */
    private String workType;

    /** 进场日期 */
    private LocalDate entryDate;

    /** 退场日期 */
    private LocalDate exitDate;

    /** 状态（1-在岗 0-离岗） */
    private Integer status;

    /** 所属班组名称（非本表字段，从 biz_team 回填） */
    @TableField(exist = false)
    private String teamName;
}
