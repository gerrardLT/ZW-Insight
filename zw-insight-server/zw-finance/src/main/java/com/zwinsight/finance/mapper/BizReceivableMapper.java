package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.finance.domain.BizReceivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 应收台账 Mapper
 */
@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizReceivableMapper extends BaseMapper<BizReceivable> {

    /**
     * 原子增减已核销金额并按结果同步状态（核销/反冲共用）。
     * <p><b>为何不用 updateById</b>：本仓未注册 MyBatis-Plus 的 OptimisticLockerInnerInterceptor，
     * {@code updateById} 不会携带 version 条件，属 read-modify-write，并发核销同一批应收时会丢失更新
     * （2026-09-23 审查发现）。改用单条原子 SQL，把「累加 + 状态切换」收敛到数据库层。</p>
     * <p><b>依赖 MySQL SET 子句从左到右求值</b>：status 表达式中的 written_off_amount
     * 已是本次累加后的新值，因此不能再写成 {@code written_off_amount + #{delta}}（会重复加一次）。</p>
     * <p>GREATEST(0, ...) 为防御：反冲额若超过当前已核销额（并发或人工改库），
     * 保证余额不为负；调用方需按实际可反冲额维护项目应收单值（见 ReceivableService.reverseWriteOff）。</p>
     * <p><b>方法级空 {@code @DataPermission} 用于显式豁免数据权限注入</b>：
     * MyBatis-Plus 的 {@code DataPermissionInterceptor} 对 UPDATE/DELETE 同样注入条件
     * （{@code beforePrepare → processUpdate/processDelete}），类级注解会让本语句被追加
     * {@code AND created_by = ?}。而 {@code ReceivableService} 不校验本方法的返回行数，
     * 一旦按创建人过滤就会「台账静默未更新、核销明细已落库」造成账实不一致。
     * 写操作访问控制由接口层 {@code @RequiresPermission} 负责，故此处豁免行级过滤
     * （2026-10-10 权限一致性清查 F2 审阅结论）。</p>
     *
     * @param id    应收台账ID
     * @param delta 增减额（正=核销，负=反冲）
     * @return 影响行数（0 表示记录不存在或已逻辑删除）
     */
    @DataPermission({})
    @Update("UPDATE biz_receivable SET written_off_amount = GREATEST(0, written_off_amount + #{delta}), "
            + "status = IF(written_off_amount >= receivable_amount, 'CLOSED', 'OPEN') "
            + "WHERE id = #{id} AND deleted = 0")
    int addWrittenOffAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
