package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投标登记服务
 */
@Service
@RequiredArgsConstructor
public class TenderRegisterService {

    private final BizTenderRegisterMapper registerMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizTenderRegister> page(int page, int size, Long projectId) {
        Page<BizTenderRegister> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTenderRegister> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizTenderRegister::getProjectId, projectId)
                .orderByDesc(BizTenderRegister::getCreatedAt);
        Page<BizTenderRegister> result = registerMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增投标登记（更新项目状态为TENDERING）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizTenderRegister register) {
        register.setStatus("REGISTERED");
        registerMapper.insert(register);

        // 更新项目状态为TENDERING
        BizProject project = projectMapper.selectById(register.getProjectId());
        if (project != null) {
            project.setStatus("TENDERING");
            projectMapper.updateById(project);
        }
    }

    /**
     * 根据ID查询
     */
    public BizTenderRegister getById(Long id) {
        BizTenderRegister register = registerMapper.selectById(id);
        if (register == null) {
            throw new BusinessException("投标登记不存在");
        }
        return register;
    }
}
