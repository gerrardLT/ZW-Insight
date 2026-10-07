package com.zwinsight.tender.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.tender.domain.BizTenderPersonBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 投标押证绑定 Mapper（V2026_82）
 */
@Mapper
public interface BizTenderPersonBindingMapper extends BaseMapper<BizTenderPersonBinding> {

    /**
     * 排他校验（TI-2）：指定证件当前是否在其他在投项目中被锁定。
     * 仅统计 LOCKED 状态且 register_id 不等于当前报名的活跃绑定。
     */
    @Select("SELECT COUNT(*) FROM biz_tender_person_binding b " +
            "JOIN biz_tender_register r ON r.id = b.register_id AND r.deleted = 0 " +
            "WHERE b.person_certificate_id = #{certId} " +
            "AND b.status = 'LOCKED' AND b.deleted = 0 " +
            "AND b.register_id != #{excludeRegisterId} " +
            "AND r.status IN ('REGISTERED', 'SUBMITTED', 'WON')")
    long countActiveLockOnOtherRegisters(@Param("certId") Long certId,
                                          @Param("excludeRegisterId") Long excludeRegisterId);
}
