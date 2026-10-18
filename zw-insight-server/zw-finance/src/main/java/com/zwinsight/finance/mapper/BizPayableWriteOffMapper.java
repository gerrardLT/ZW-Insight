package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizPayableWriteOff;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应付核销明细 Mapper（V2026_115）。
 * <p>不加 {@code @DataPermission}：表无 project_id/created_by 之外的行级维度，
 * 行级访问控制由付款/反冲入口接口层负责（对称 BizReceivableWriteOffMapper）。</p>
 */
@Mapper
public interface BizPayableWriteOffMapper extends BaseMapper<BizPayableWriteOff> {
}
