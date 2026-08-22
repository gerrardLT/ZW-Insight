package com.zwinsight.finance.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.PaymentReceivedExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回款登记导出处理器（仅导出，不支持导入）
 */
@Component
@RequiredArgsConstructor
public class PaymentReceivedBatchHandler implements BatchModuleHandler {

    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizProjectMapper projectMapper;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.PAYMENT_RECEIVED == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return PaymentReceivedExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        throw new BusinessException("回款登记模块不支持批量导入");
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        LambdaQueryWrapper<BizPaymentReceived> wrapper = new LambdaQueryWrapper<>();
        if (params != null && params.get("projectId") != null) {
            wrapper.eq(BizPaymentReceived::getProjectId, Long.valueOf(params.get("projectId").toString()));
        }
        wrapper.orderByDesc(BizPaymentReceived::getCreatedAt);
        List<BizPaymentReceived> list = paymentReceivedMapper.selectList(wrapper);

        Map<Long, String> projectNames = projectMapper.selectList(null).stream()
                .collect(Collectors.toMap(BizProject::getId, BizProject::getProjectName, (a, b) -> a));
        return list.stream().map(entity -> {
            PaymentReceivedExcelDTO dto = new PaymentReceivedExcelDTO();
            dto.setProjectName(projectNames.getOrDefault(entity.getProjectId(), ""));
            dto.setReceiveDate(entity.getReceiveDate() != null ? entity.getReceiveDate().toString() : "");
            dto.setReceiveAmount(entity.getReceiveAmount() != null ? entity.getReceiveAmount().toPlainString() : "");
            dto.setReceiver(entity.getReceiver());
            dto.setReceiveType(entity.getReceiveType());
            dto.setReceiveBankAccount(entity.getReceiveBankAccount());
            dto.setStatus("APPROVED".equals(entity.getStatus()) ? "已审批" : "草稿");
            return dto;
        }).toList();
    }
}
