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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 开标记录服务
 */
@Service
@RequiredArgsConstructor
public class OpenBidRecordService {

    private final BizOpenBidRecordMapper openBidRecordMapper;
    private final BizTenderRegisterMapper registerMapper;
    private final BizProjectMapper projectMapper;
    private final com.zwinsight.project.service.ProjectService projectService;

    /**
     * 新增开标记录（中标→更新项目status=WON + 更新register status）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizOpenBidRecord record) {
        // D1 守卫（2026-08-11）：中标时终态项目禁止被改回 WON，先校验再落库（fail-fast）。
        // P2 强化（2026-08-12，批次二 D3）：施工中项目被中标登记回退为 WON，一并拦截
        boolean won = record.getIsWon() != null && record.getIsWon() == 1;
        // P1-M1 审核修复：project 加载提前到分支外（原落标分支 project 恒 null，loseBid 永远不执行）
        BizProject project = projectMapper.selectById(record.getProjectId());
        if (won) {
            if (project != null && ("CLOSED".equals(project.getStatus())
                    || "COMPLETED".equals(project.getStatus()) || "CLOSING".equals(project.getStatus())
                    || "CONSTRUCTION".equals(project.getStatus()))) {
                throw new BusinessException("项目已竣工/关闭/施工中，不可登记中标");
            }
        }

        openBidRecordMapper.insert(record);

        // 更新投标登记状态
        BizTenderRegister register = registerMapper.selectById(record.getRegisterId());
        if (register == null) {
            throw new BusinessException("投标登记不存在");
        }

        if (won) {
            // 中标
            register.setStatus("WON");
            registerMapper.updateById(register);

            // P1-M1：项目状态走状态机（WIN_BID，留大事记+守卫）
            if (project != null) {
                projectService.winBid(project.getId());
            }
        } else {
            // 未中标
            register.setStatus("LOST");
            registerMapper.updateById(register);
            // P1-M1：项目落标归档（LOST 终态，留原因）。
            // 审核修复：只在项目确为 TENDERING 时流转；非阻断——异常状态下录开标记录是合法的
            // 记录行为，不应因状态流转失败而整体回滚开标记录
            if (project != null && "TENDERING".equals(project.getStatus())) {
                try {
                    projectService.loseBid(project.getId(), "开标未中标");
                } catch (com.zwinsight.common.exception.BusinessException e) {
                    // 状态机拒绝（并发窗口下项目状态已变）→ 仅保留开标记录，不阻断
                }
            }
        }
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
