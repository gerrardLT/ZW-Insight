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

    /**
     * 新增开标记录（中标→更新项目status=WON + 更新register status）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizOpenBidRecord record) {
        openBidRecordMapper.insert(record);

        // 更新投标登记状态
        BizTenderRegister register = registerMapper.selectById(record.getRegisterId());
        if (register == null) {
            throw new BusinessException("投标登记不存在");
        }

        if (record.getIsWon() != null && record.getIsWon() == 1) {
            // 中标
            register.setStatus("WON");
            registerMapper.updateById(register);

            // 更新项目状态为WON
            BizProject project = projectMapper.selectById(record.getProjectId());
            if (project != null) {
                project.setStatus("WON");
                projectMapper.updateById(project);
            }
        } else {
            // 未中标
            register.setStatus("LOST");
            registerMapper.updateById(register);
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
}
