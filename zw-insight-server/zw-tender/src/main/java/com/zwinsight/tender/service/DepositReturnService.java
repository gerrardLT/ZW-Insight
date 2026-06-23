package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizDepositReturn;
import com.zwinsight.tender.mapper.BizDepositReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 保证金退还服务
 */
@Service
@RequiredArgsConstructor
public class DepositReturnService {

    private final BizDepositReturnMapper returnMapper;

    /**
     * 分页查询
     */
    public PageResult<BizDepositReturn> page(int page, int size, Long depositApplyId) {
        Page<BizDepositReturn> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizDepositReturn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(depositApplyId != null, BizDepositReturn::getDepositApplyId, depositApplyId)
                .orderByDesc(BizDepositReturn::getReturnDate);
        Page<BizDepositReturn> result = returnMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增退还
     */
    public void save(BizDepositReturn depositReturn) {
        returnMapper.insert(depositReturn);
    }
}
