package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizOpenBidRecord;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizOpenBidRecordMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 开标记录服务（P1-M2 深度优化，蓝图 02-tender.md A1/TI-3）
 * <p>
 * 开标是投标全流程的收束点：录入结果时联动三条线——
 * ① 投标报名状态（WON/LOST）+ 落标原因分类（B4）
 * ② 项目状态机（WIN_BID/LOSE_BID，P1-M1 已接通）
 * ③ 押证人员解锁（TI-2：不论中标或落标，开标完毕即释放全部锁定人员）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenBidRecordService {

    private final BizOpenBidRecordMapper openBidRecordMapper;
    private final BizTenderRegisterMapper registerMapper;
    private final BizProjectMapper projectMapper;
    private final com.zwinsight.project.service.ProjectService projectService;
    private final TenderPersonBindingService personBindingService;

    /** 合法的落标原因分类（B4） */
    public static final Set<String> LOST_REASON_CATEGORIES = Set.of(
            "PRICE_OVER", "TECH_WEAK", "BIZ_DEVIATION", "CREDIT_LACK", "OTHER");

    /**
     * 新增开标记录（中标→项目 WIN_BID + 报名 WON + 解锁押证；
     * 落标→报名 LOST + 项目 LOSE_BID + 解锁押证 + 落标原因分类）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizOpenBidRecord record) {
        // D1 守卫（2026-08-11）：中标时终态项目禁止被改回 WON，先校验再落库（fail-fast）。
        boolean won = record.getIsWon() != null && record.getIsWon() == 1;
        BizProject project = projectMapper.selectById(record.getProjectId());
        if (won) {
            if (project != null && ("CLOSED".equals(project.getStatus())
                    || "COMPLETED".equals(project.getStatus()) || "CLOSING".equals(project.getStatus())
                    || "CONSTRUCTION".equals(project.getStatus()))) {
                throw new BusinessException("项目已竣工/关闭/施工中，不可登记中标");
            }
        } else {
            // B4：落标原因分类校验（未中标时必填）
            validateLostReason(record.getLostReasonCategory());
        }

        openBidRecordMapper.insert(record);

        // 更新投标登记状态 + 落标原因分类
        BizTenderRegister register = registerMapper.selectById(record.getRegisterId());
        if (register == null) {
            throw new BusinessException("投标登记不存在");
        }

        if (won) {
            register.setStatus("WON");
            registerMapper.updateById(register);

            // P1-M1：项目状态走状态机（WIN_BID，留大事记+守卫）
            if (project != null) {
                projectService.winBid(project.getId());
            }

            // TI-2：中标后释放非本项目的锁定人员（本项目的押证人员继续锁定至合同签订）
            personBindingService.releaseAllByRegister(record.getRegisterId(), "开标中标释放");
        } else {
            register.setStatus("LOST");
            register.setLostReasonCategory(record.getLostReasonCategory());
            registerMapper.updateById(register);

            // P1-M1：项目落标归档（LOST 终态，留原因）
            if (project != null && "TENDERING".equals(project.getStatus())) {
                String reason = buildLostReasonText(record);
                try {
                    projectService.loseBid(project.getId(), reason);
                } catch (com.zwinsight.common.exception.BusinessException e) {
                    // 状态机拒绝（并发窗口下项目状态已变）→ 仅保留开标记录，不阻断
                    log.warn("落标时项目状态流转被拒, projectId={}, reason={}",
                            project.getId(), e.getMessage());
                }
            }

            // TI-2：落标后释放全部押证人员
            personBindingService.releaseAllByRegister(record.getRegisterId(), "开标落标释放");
        }
    }

    /** B4：落标原因分类校验 */
    private void validateLostReason(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException("落标时必须填写落标原因分类（PRICE_OVER/TECH_WEAK/BIZ_DEVIATION/CREDIT_LACK/OTHER）");
        }
        if (!LOST_REASON_CATEGORIES.contains(category)) {
            throw new BusinessException("无效的落标原因分类：" + category);
        }
    }

    /** 落标原因人类可读文本 */
    private String buildLostReasonText(BizOpenBidRecord record) {
        String cat = record.getLostReasonCategory() != null ? record.getLostReasonCategory() : "OTHER";
        String desc = switch (cat) {
            case "PRICE_OVER" -> "报价偏高";
            case "TECH_WEAK" -> "技术标失分";
            case "BIZ_DEVIATION" -> "商务标偏离";
            case "CREDIT_LACK" -> "资信不足";
            default -> "其他原因";
        };
        return "开标落标（" + desc + "）";
    }

    /**
     * 根据登记ID查询开标记录
     */
    public BizOpenBidRecord getByRegister(Long registerId) {
        LambdaQueryWrapper<BizOpenBidRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizOpenBidRecord::getRegisterId, registerId)
                .last("LIMIT 1");
        return openBidRecordMapper.selectOne(wrapper);
    }

    /**
     * 更新开标记录
     */
    public void update(BizOpenBidRecord record) {
        openBidRecordMapper.updateById(record);
    }

    /**
     * 删除开标记录
     */
    public void delete(Long id) {
        openBidRecordMapper.deleteById(id);
    }
}
