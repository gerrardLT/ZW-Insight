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
        // 日期合理性校验（2026-08-21 台账缺陷修复）：开标日期不得早于报名日期
        validateDates(register);
        // D1 守卫（2026-08-11）：终态项目禁止被改回投标中，先校验再落库（fail-fast）。
        // P2 强化（2026-08-12，批次二 D3）：已中标/施工中项目新增登记会把状态回退为
        // TENDERING（状态回退漏洞），一并拦截
        BizProject project = projectMapper.selectById(register.getProjectId());
        if (project != null && ("CLOSED".equals(project.getStatus())
                || "COMPLETED".equals(project.getStatus()) || "CLOSING".equals(project.getStatus())
                || "WON".equals(project.getStatus()) || "CONSTRUCTION".equals(project.getStatus()))) {
            throw new BusinessException("项目已中标/竣工/关闭，不可新增投标登记");
        }

        register.setStatus("REGISTERED");
        registerMapper.insert(register);

        // 更新项目状态为TENDERING
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

    /**
     * 更新投标登记
     */
    public void update(BizTenderRegister register) {
        BizTenderRegister existing = registerMapper.selectById(register.getId());
        if (existing == null) throw new BusinessException("投标登记不存在");
        // 状态守卫（2026-08-21 台账缺陷修复）：仅报名状态可编辑，
        // SUBMITTED/WON/LOST 已进投标/开标链路，禁止事后篡改
        if (!"REGISTERED".equals(existing.getStatus())) {
            throw new BusinessException("仅报名状态可编辑");
        }
        // 日期合理性校验：以提交字段为准，缺省回落存量值比对
        BizTenderRegister merged = new BizTenderRegister();
        merged.setRegisterDate(register.getRegisterDate() != null ? register.getRegisterDate() : existing.getRegisterDate());
        merged.setOpenDate(register.getOpenDate() != null ? register.getOpenDate() : existing.getOpenDate());
        validateDates(merged);
        // P1 修复（2026-08-12，批次二取证枚举 TND-08）：status/projectId 由登记/开标链路
        // 维护，置 null 后 updateById（NOT_NULL 策略）不落库，防 PUT 篡改状态/换绑项目
        register.setStatus(null);
        register.setProjectId(null);
        registerMapper.updateById(register);
    }

    /**
     * 开标日期不得早于报名日期（两端日期均非空时校验）
     */
    private void validateDates(BizTenderRegister register) {
        if (register.getOpenDate() != null && register.getRegisterDate() != null
                && register.getOpenDate().isBefore(register.getRegisterDate())) {
            throw new BusinessException("开标日期不能早于报名日期");
        }
    }

    /**
     * 删除投标登记
     */
    public void delete(Long id) {
        BizTenderRegister existing = registerMapper.selectById(id);
        if (existing == null) throw new BusinessException("投标登记不存在");
        if (!"REGISTERED".equals(existing.getStatus())) throw new BusinessException("仅报名状态可删除");
        registerMapper.deleteById(id);
    }

    /**
     * 提交审批
     */
    public void submit(Long id) {
        BizTenderRegister register = registerMapper.selectById(id);
        if (register == null) throw new BusinessException("投标登记不存在");
        // P2 修复（2026-08-12，批次二 D3）：仅报名状态可提交，防 WON/LOST/SUBMITTED 被重提回退
        if (!"REGISTERED".equals(register.getStatus())) throw new BusinessException("仅报名状态可提交");
        register.setStatus("SUBMITTED");
        registerMapper.updateById(register);
    }
}
