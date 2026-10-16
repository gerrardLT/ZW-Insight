package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizContractPayTerm;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizContractPayTermMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * 合同收付款条款服务（V2026_109，C2）。
 * <p>
 * 条款是合同价款的支付约定（预付款/进度款/竣工款/质保金），挂接各类合同；
 * 只被引用不被复制：过程结算按 RETENTION 条款计提质保金、按 PREPAY 条款建议扣回，
 * 资金预测按条款计划日期推导收付款时点。质保金比例默认上限 3%
 * （财建〔2022〕183 号：除合同约定保留不超过工程价款总额 3% 的质量保证金外）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractPayTermService {

    private static final Set<String> VALID_TYPES = Set.of(
            BizContractPayTerm.TYPE_PREPAY, BizContractPayTerm.TYPE_PROGRESS,
            BizContractPayTerm.TYPE_FINAL, BizContractPayTerm.TYPE_RETENTION);
    private static final Set<String> VALID_DEDUCT_RULES = Set.of("PER_STAGE", "AFTER_80", "NONE");
    /** 质保金比例上限（%） */
    private static final BigDecimal RETENTION_RATIO_CEILING = new BigDecimal("3");

    private final BizContractPayTermMapper termMapper;
    private final BizConstructionContractMapper contractMapper;

    /**
     * 查询某合同的收付款条款（ACTIVE 在前，按类型排序展示）
     */
    public List<BizContractPayTerm> list(String contractCategory, Long contractId) {
        validateCategory(contractCategory);
        if (contractId == null) {
            throw new BusinessException(400, "合同ID不能为空");
        }
        return termMapper.selectList(new LambdaQueryWrapper<BizContractPayTerm>()
                .eq(BizContractPayTerm::getContractCategory, contractCategory)
                .eq(BizContractPayTerm::getContractId, contractId)
                .orderByAsc(BizContractPayTerm::getTermType)
                .orderByAsc(BizContractPayTerm::getId));
    }

    /**
     * 新增条款。同合同同类型允许多条（如分期进度款），但 RETENTION 建议单条——
     * 多条时计提按第一条生效条款计算并在日志留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizContractPayTerm term) {
        validate(term);
        term.setId(null);
        term.setStatus("ACTIVE");
        termMapper.insert(term);
    }

    /** 编辑条款（ACTIVE 状态可编辑；引用方每次实时读取，不留缓存副本） */
    @Transactional(rollbackFor = Exception.class)
    public void update(BizContractPayTerm term) {
        if (term.getId() == null) {
            throw new BusinessException(400, "条款ID不能为空");
        }
        BizContractPayTerm existing = termMapper.selectById(term.getId());
        if (existing == null) {
            throw new BusinessException(404, "条款不存在");
        }
        // 归属不可改绑：防止把条款挪到别的合同导致计提错位
        term.setContractCategory(existing.getContractCategory());
        term.setContractId(existing.getContractId());
        term.setProjectId(existing.getProjectId());
        validate(term);
        term.setStatus(existing.getStatus());
        termMapper.updateById(term);
    }

    /** 删除条款（逻辑删；已被引用的历史计提不受影响） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (termMapper.deleteById(id) != 1) {
            throw new BusinessException(404, "条款不存在");
        }
    }

    /**
     * 计算质保金计提额：RETENTION 条款存在时取审定额×比例（封顶条款金额）；
     * 无条款返回 null（表示合同未约定结构化质保金，沿用人工录入）。
     */
    public BigDecimal calcRetentionAmount(String contractCategory, Long contractId, BigDecimal confirmedAmount) {
        BizContractPayTerm term = firstActiveTerm(contractCategory, contractId, BizContractPayTerm.TYPE_RETENTION);
        if (term == null || confirmedAmount == null || confirmedAmount.signum() <= 0) {
            return null;
        }
        BigDecimal retention;
        if (term.getAmount() != null && term.getAmount().signum() > 0) {
            retention = term.getAmount();
        } else if (term.getRatio() != null && term.getRatio().signum() > 0) {
            retention = confirmedAmount.multiply(term.getRatio()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            return null;
        }
        if (retention.compareTo(confirmedAmount) > 0) {
            log.warn("质保金条款计提额 {} 超过本期审定额 {}，按审定额封顶, contractId={}",
                    retention, confirmedAmount, contractId);
            retention = confirmedAmount;
        }
        return retention;
    }

    /** 预付款建议扣回额：按 PREPAY 条款与剩余未扣回规则计算（v1 人工确认口径） */
    public BigDecimal suggestPrepayDeduct(String contractCategory, Long contractId) {
        BizContractPayTerm term = firstActiveTerm(contractCategory, contractId, BizContractPayTerm.TYPE_PREPAY);
        if (term == null) {
            return BigDecimal.ZERO;
        }
        // v1：无逐期台账，建议额 = 条款金额（PER_STAGE 由人工按期拆分），仅作表单预填
        return term.getAmount() != null ? term.getAmount() : BigDecimal.ZERO;
    }

    public BizContractPayTerm firstActiveTerm(String contractCategory, Long contractId, String termType) {
        List<BizContractPayTerm> terms = termMapper.selectList(new LambdaQueryWrapper<BizContractPayTerm>()
                .eq(BizContractPayTerm::getContractCategory, contractCategory)
                .eq(BizContractPayTerm::getContractId, contractId)
                .eq(BizContractPayTerm::getTermType, termType)
                .eq(BizContractPayTerm::getStatus, "ACTIVE")
                .orderByAsc(BizContractPayTerm::getId)
                .last("LIMIT 1"));
        return terms.isEmpty() ? null : terms.get(0);
    }

    private void validate(BizContractPayTerm term) {
        validateCategory(term.getContractCategory());
        if (term.getContractId() == null) {
            throw new BusinessException(400, "合同ID不能为空");
        }
        if (!VALID_TYPES.contains(term.getTermType())) {
            throw new BusinessException(400, "未知的条款类型：" + term.getTermType());
        }
        if (term.getRatio() == null && term.getAmount() == null) {
            throw new BusinessException(400, "比例与金额至少填一项");
        }
        if (term.getRatio() != null && (term.getRatio().signum() < 0
                || term.getRatio().compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException(400, "比例须在0至100之间");
        }
        if (BizContractPayTerm.TYPE_RETENTION.equals(term.getTermType())
                && term.getRatio() != null && term.getRatio().compareTo(RETENTION_RATIO_CEILING) > 0) {
            throw new BusinessException(400, "质量保证金比例不得超过工程价款总额的3%（财建〔2022〕183号）");
        }
        if (term.getDeductRule() != null && !VALID_DEDUCT_RULES.contains(term.getDeductRule())) {
            throw new BusinessException(400, "未知的扣回规则：" + term.getDeductRule());
        }
        if (BizContractPayTerm.CATEGORY_CONSTRUCTION.equals(term.getContractCategory())) {
            // 施工合同：projectId 由后端从合同反查，不信任请求体
            BizConstructionContract contract = contractMapper.selectById(term.getContractId());
            if (contract == null) {
                throw new BusinessException(404, "关联的施工合同不存在");
            }
            term.setProjectId(contract.getProjectId());
        } else if (term.getProjectId() == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException(400, "合同类别不能为空");
        }
    }
}
