package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.tender.domain.BizTenderPersonBinding;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizTenderPersonBindingMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投标押证绑定服务（V2026_82，TI-2 一证多投排他锁定）。
 * <p>
 * 核心规则：同一人员证件在任一报名的 REGISTERED/SUBMITTED/WON 期间处于 LOCKED 状态，
 * 其他在投项目（也处于活跃状态）尝试绑定同一证件时被拒绝。
 * 开标（不论中标或落标）后，该报名下所有绑定自动释放。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderPersonBindingService {

    private final BizTenderPersonBindingMapper bindingMapper;
    private final BizTenderRegisterMapper registerMapper;

    /**
     * 批量绑定（投标报名提交时调用）。
     * 每条绑定做 TI-2 排他校验：证件在其他活跃报名中已锁定则拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindBatch(Long registerId, List<BizTenderPersonBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        BizTenderRegister register = registerMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException("投标报名不存在");
        }
        for (BizTenderPersonBinding b : bindings) {
            validateAndBind(register, b);
        }
        log.info("投标押证绑定完成, registerId={}, 绑定数={}", registerId, bindings.size());
    }

    /** 单条绑定（含排他校验） */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long registerId, BizTenderPersonBinding binding) {
        BizTenderRegister register = registerMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException("投标报名不存在");
        }
        validateAndBind(register, binding);
    }

    private void validateAndBind(BizTenderRegister register, BizTenderPersonBinding binding) {
        if (binding.getPersonCertificateId() == null) {
            throw new BusinessException("押证人员证件ID不能为空");
        }
        if (binding.getPersonName() == null || binding.getPersonName().isBlank()) {
            throw new BusinessException("押证人员姓名不能为空");
        }
        // TI-2 排他校验：该证件在其他活跃在投项目中已锁定
        long conflicts = bindingMapper.countActiveLockOnOtherRegisters(
                binding.getPersonCertificateId(), register.getId());
        if (conflicts > 0) {
            throw new BusinessException(String.format(
                    "人员[%s]证件已在其他在投项目中被锁定（活跃锁定数=%d），禁止一证多投。请先开标释放或更换人员。",
                    binding.getPersonName(), conflicts));
        }
        // 同一报名下同证件幂等（已有绑定则跳过）
        Long existing = bindingMapper.selectCount(new LambdaQueryWrapper<BizTenderPersonBinding>()
                .eq(BizTenderPersonBinding::getRegisterId, register.getId())
                .eq(BizTenderPersonBinding::getPersonCertificateId, binding.getPersonCertificateId())
                .eq(BizTenderPersonBinding::getStatus, BizTenderPersonBinding.STATUS_LOCKED));
        if (existing != null && existing > 0) {
            return; // 幂等：同证件同报名已锁定，跳过
        }
        binding.setRegisterId(register.getId());
        binding.setProjectId(register.getProjectId());
        binding.setStatus(BizTenderPersonBinding.STATUS_LOCKED);
        bindingMapper.insert(binding);
    }

    /**
     * 解锁指定报名下的全部绑定（开标后调用：不论中标或落标均释放）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int releaseAllByRegister(Long registerId, String reason) {
        List<BizTenderPersonBinding> locked = bindingMapper.selectList(
                new LambdaQueryWrapper<BizTenderPersonBinding>()
                        .eq(BizTenderPersonBinding::getRegisterId, registerId)
                        .eq(BizTenderPersonBinding::getStatus, BizTenderPersonBinding.STATUS_LOCKED));
        for (BizTenderPersonBinding b : locked) {
            b.setStatus(BizTenderPersonBinding.STATUS_RELEASED);
            b.setReleasedAt(LocalDateTime.now());
            b.setReleasedReason(reason);
            bindingMapper.updateById(b);
        }
        if (!locked.isEmpty()) {
            log.info("投标押证解锁, registerId={}, reason={}, 解锁数={}", registerId, reason, locked.size());
        }
        return locked.size();
    }

    /** 查询指定报名下的活跃绑定 */
    public List<BizTenderPersonBinding> listByRegister(Long registerId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<BizTenderPersonBinding>()
                .eq(BizTenderPersonBinding::getRegisterId, registerId)
                .orderByAsc(BizTenderPersonBinding::getId));
    }

    /** 查询证件当前活跃锁定（排他校验查询用） */
    public List<BizTenderPersonBinding> listActiveLocksByCert(Long certId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<BizTenderPersonBinding>()
                .eq(BizTenderPersonBinding::getPersonCertificateId, certId)
                .eq(BizTenderPersonBinding::getStatus, BizTenderPersonBinding.STATUS_LOCKED));
    }

}
