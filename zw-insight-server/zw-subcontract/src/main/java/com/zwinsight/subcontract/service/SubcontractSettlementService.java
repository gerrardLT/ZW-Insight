package com.zwinsight.subcontract.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.domain.BizSubcontractSettlementDetail;
import com.zwinsight.subcontract.dto.SubcontractSettlementCreateRequest;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailDTO;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailExcelDTO;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailVO;
import com.zwinsight.subcontract.dto.SubcontractSettlementExcelDTO;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import com.zwinsight.subcontract.mapper.SubcontractSettlementDetailMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分包结算服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubcontractSettlementService {

    private final BizSubcontractSettlementMapper settlementMapper;
    private final SubcontractSettlementDetailMapper detailMapper;
    private final BizSubcontractMapper subcontractMapper;
    private final BizProjectMapper projectMapper;

    public PageResult<BizSubcontractSettlement> page(int page, int size, Long projectId, Long contractId) {
        Page<BizSubcontractSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSubcontractSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSubcontractSettlement::getProjectId, projectId)
                .eq(contractId != null, BizSubcontractSettlement::getContractId, contractId)
                .orderByDesc(BizSubcontractSettlement::getCreatedAt);
        return PageResult.of(settlementMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 创建分包结算单（含明细行）
     * <p>
     * 明细行金额计算：amount = quantity × unitPrice（保留2位小数，HALF_UP）
     * 总金额计算：settlementAmount = sum(detail.amount)
     *
     * @param request 创建请求
     * @return 结算单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSettlement(SubcontractSettlementCreateRequest request) {
        // 1. 创建结算单主表
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setContractId(request.getContractId());
        settlement.setProjectId(request.getProjectId());
        settlement.setStatus("DRAFT");
        settlement.setSettlementAmount(BigDecimal.ZERO);
        settlementMapper.insert(settlement);

        // 2. 保存明细行并计算总金额
        BigDecimal totalAmount = saveDetails(settlement.getId(), request.getDetails());

        // 3. 更新结算单总金额
        settlement.setSettlementAmount(totalAmount);
        settlementMapper.updateById(settlement);

        return settlement.getId();
    }

    /**
     * 更新分包结算单（含明细行重新计算）
     * <p>
     * 支持修改明细行后重新计算总金额。
     * 策略：删除旧明细行，重新插入新明细行并计算总金额。
     *
     * @param id      结算单ID
     * @param request 更新请求（含新的明细行列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSettlement(Long id, SubcontractSettlementCreateRequest request) {
        BizSubcontractSettlement existing = settlementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("结算记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }

        // 1. 更新主表基本信息
        existing.setContractId(request.getContractId());
        existing.setProjectId(request.getProjectId());

        // 2. 删除旧明细行
        LambdaQueryWrapper<BizSubcontractSettlementDetail> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizSubcontractSettlementDetail::getSettlementId, id);
        detailMapper.delete(deleteWrapper);

        // 3. 重新插入明细行并计算总金额
        BigDecimal totalAmount = saveDetails(id, request.getDetails());

        // 4. 更新结算单总金额
        existing.setSettlementAmount(totalAmount);
        settlementMapper.updateById(existing);
    }

    /**
     * 查询结算单的明细行列表
     *
     * @param settlementId 结算单ID
     * @return 明细行列表
     */
    public List<BizSubcontractSettlementDetail> listDetails(Long settlementId) {
        LambdaQueryWrapper<BizSubcontractSettlementDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSubcontractSettlementDetail::getSettlementId, settlementId)
                .orderByAsc(BizSubcontractSettlementDetail::getSortOrder);
        return detailMapper.selectList(wrapper);
    }

    public void save(BizSubcontractSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    public BizSubcontractSettlement getById(Long id) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) throw new BusinessException("结算记录不存在");
        return settlement;
    }

    public void update(BizSubcontractSettlement settlement) {
        BizSubcontractSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        settlementMapper.updateById(settlement);
    }

    public void delete(Long id) {
        BizSubcontractSettlement existing = settlementMapper.selectById(id);
        if (existing == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");

        // 删除明细行
        LambdaQueryWrapper<BizSubcontractSettlementDetail> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizSubcontractSettlementDetail::getSettlementId, id);
        detailMapper.delete(deleteWrapper);

        settlementMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(settlement.getStatus())) throw new BusinessException("仅草稿状态可提交");

        // 获取分包合同
        BizSubcontract contract = subcontractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("分包合同不存在");
        }

        // 校验：累计结算金额不能超过合同金额
        BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal currentCumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
        BigDecimal newCumulative = currentCumulative.add(settlement.getSettlementAmount());

        if (newCumulative.compareTo(contractAmount) > 0) {
            BigDecimal maxSettlement = contractAmount.subtract(currentCumulative);
            throw new BusinessException("结算金额超出合同金额限制，当前最大可结算金额：" + maxSettlement);
        }

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算
        contract.setCumulativeSettlement(newCumulative);
        subcontractMapper.updateById(contract);

        // 回写项目总支出
        BizProject project = projectMapper.selectById(settlement.getProjectId());
        if (project != null) {
            BigDecimal totalExpense = project.getTotalExpense() != null ? project.getTotalExpense() : BigDecimal.ZERO;
            project.setTotalExpense(totalExpense.add(settlement.getSettlementAmount()));
            projectMapper.updateById(project);
        }
    }

    // ==================== 详情与导出 ====================

    /**
     * 获取结算单详情（含明细行和合同信息）
     *
     * @param id 结算单ID
     * @return 结算单详情VO
     */
    public SubcontractSettlementDetailVO getDetailVO(Long id) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算记录不存在");
        }

        SubcontractSettlementDetailVO vo = new SubcontractSettlementDetailVO();
        vo.setSettlement(settlement);
        vo.setDetails(listDetails(id));

        // 查询关联合同信息
        if (settlement.getContractId() != null) {
            BizSubcontract contract = subcontractMapper.selectById(settlement.getContractId());
            if (contract != null) {
                vo.setContractCode(contract.getContractCode());
                vo.setContractName(contract.getContractName());
                vo.setSubcontractor(contract.getSubcontractor());
            }
        }

        return vo;
    }

    /**
     * 导出结算单 Excel（EasyExcel 多 Sheet）
     * <p>
     * Sheet1: 结算汇总信息
     * Sheet2: 结算明细行
     *
     * @param settlementId 结算单ID
     * @param response     HTTP 响应
     */
    public void exportSettlement(Long settlementId, HttpServletResponse response) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算记录不存在");
        }

        // 查询明细
        List<BizSubcontractSettlementDetail> details = listDetails(settlementId);

        // 查询合同信息
        String contractCode = "";
        String contractName = "";
        String subcontractor = "";
        if (settlement.getContractId() != null) {
            BizSubcontract contract = subcontractMapper.selectById(settlement.getContractId());
            if (contract != null) {
                contractCode = contract.getContractCode() != null ? contract.getContractCode() : "";
                contractName = contract.getContractName() != null ? contract.getContractName() : "";
                subcontractor = contract.getSubcontractor() != null ? contract.getSubcontractor() : "";
            }
        }

        // 构建 Sheet1 数据：结算汇总
        SubcontractSettlementExcelDTO summaryDTO = new SubcontractSettlementExcelDTO();
        summaryDTO.setContractCode(contractCode);
        summaryDTO.setContractName(contractName);
        summaryDTO.setSubcontractor(subcontractor);
        summaryDTO.setSettlementAmount(settlement.getSettlementAmount());
        summaryDTO.setStatusText(getStatusText(settlement.getStatus()));
        summaryDTO.setCreatedAt(settlement.getCreatedAt() != null
                ? settlement.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        List<SubcontractSettlementExcelDTO> summaryData = List.of(summaryDTO);

        // 构建 Sheet2 数据：结算明细
        List<SubcontractSettlementDetailExcelDTO> detailData = details.stream().map(d -> {
            SubcontractSettlementDetailExcelDTO dto = new SubcontractSettlementDetailExcelDTO();
            dto.setSortOrder(d.getSortOrder());
            dto.setItemName(d.getItemName());
            dto.setUnit(d.getUnit());
            dto.setQuantity(d.getQuantity());
            dto.setUnitPrice(d.getUnitPrice());
            dto.setAmount(d.getAmount());
            dto.setRemark(d.getRemark());
            return dto;
        }).collect(Collectors.toList());

        // 写入 Excel
        String fileName = "分包结算单_" + contractCode + ".xlsx";
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
                // Sheet1: 结算汇总
                WriteSheet summarySheet = EasyExcel.writerSheet(0, "结算汇总")
                        .head(SubcontractSettlementExcelDTO.class)
                        .build();
                excelWriter.write(summaryData, summarySheet);

                // Sheet2: 结算明细
                WriteSheet detailSheet = EasyExcel.writerSheet(1, "结算明细")
                        .head(SubcontractSettlementDetailExcelDTO.class)
                        .build();
                excelWriter.write(detailData, detailSheet);
            }
        } catch (Exception e) {
            log.error("导出分包结算单失败", e);
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    private String getStatusText(String status) {
        if (status == null) return "";
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "APPROVED" -> "已审批";
            default -> status;
        };
    }

    // ==================== 私有方法 ====================

    /**
     * 保存明细行并计算总金额
     * <p>
     * 每行金额 = quantity × unitPrice，保留2位小数，HALF_UP 舍入。
     * 总金额 = 所有明细行金额之和。
     *
     * @param settlementId 结算单ID
     * @param details      明细行DTO列表
     * @return 总金额
     */
    private BigDecimal saveDetails(Long settlementId, List<SubcontractSettlementDetailDTO> details) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 1;

        for (SubcontractSettlementDetailDTO item : details) {
            BizSubcontractSettlementDetail detail = new BizSubcontractSettlementDetail();
            detail.setSettlementId(settlementId);
            detail.setItemName(item.getItemName());
            detail.setUnit(item.getUnit());
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(item.getUnitPrice());
            detail.setRemark(item.getRemark());
            detail.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : sortOrder);

            // 行金额 = 数量 × 单价，保留2位小数，HALF_UP
            BigDecimal lineAmount = item.getQuantity()
                    .multiply(item.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            detail.setAmount(lineAmount);

            totalAmount = totalAmount.add(lineAmount);
            detailMapper.insert(detail);
            sortOrder++;
        }

        return totalAmount;
    }
}
