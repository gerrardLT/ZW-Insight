package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizContractPayTerm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同收付款条款 Mapper（V2026_109）。
 * <p>必须带 {@code @Mapper}：@MapperScan 只注册带注解接口，漏注解会让依赖方启动失败。</p>
 */
@Mapper
public interface BizContractPayTermMapper extends BaseMapper<BizContractPayTerm> {
}
