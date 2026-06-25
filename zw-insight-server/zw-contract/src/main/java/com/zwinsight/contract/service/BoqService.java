package com.zwinsight.contract.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizBoqItem;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.dto.BoqExcelRow;
import com.zwinsight.contract.dto.BoqReadListener;
import com.zwinsight.contract.dto.BoqUploadResultVO;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 工程量清单(BOQ)服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoqService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L; // 20MB
    private static final Set<String> ALLOWED_UPLOAD_STATUSES = Set.of("EFFECTIVE", "CHANGING");

    private final BizBoqItemMapper boqItemMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizOutputReportMapper outputReportMapper;
    private final FileService fileService;

    /**
     * 上传工程量清单
     * <p>
     * 1. 合同状态校验 — 只有 EFFECTIVE 或 CHANGING 状态才能上传
     * 2. 文件大小校验 — 不超过 20MB
     * 3. 产值上报引用检查 — 有引用则拒绝覆盖
     * 4. 存储原始文件到 MinIO
     * 5. EasyExcel 解析与层级构建
     * 6. 删除旧数据并批量插入新数据
     * 7. 计算合计金额并回写合同
     *
     * @param contractId 施工合同ID
     * @param file       上传的 Excel 文件
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BoqUploadResultVO uploadBoq(Long contractId, MultipartFile file) {
        // 1. 查询合同并校验状态
        BizConstructionContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        if (!ALLOWED_UPLOAD_STATUSES.contains(contract.getStatus())) {
            throw new BusinessException("当前合同状态不允许上传清单，仅生效或变更中状态可操作");
        }

        // 2. 文件大小校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件大小不能超过20MB");
        }

        // 3. 产值上报引用检查
        if (hasOutputReportReference(contractId)) {
            throw new BusinessException("该合同的清单条目已被产值上报引用，无法覆盖更新");
        }

        // 4. 存储原始文件到 MinIO
        FileInfo fileInfo = fileService.upload(file, "BOQ", contractId, contract.getProjectId());

        // 5. EasyExcel 解析
        BoqReadListener listener = new BoqReadListener();
        try {
            EasyExcel.read(file.getInputStream(), BoqExcelRow.class, listener)
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        // 解析后校验错误
        if (listener.hasErrors()) {
            String errorMsg = String.join("；", listener.getErrors());
            throw new BusinessException("清单文件校验失败：" + errorMsg);
        }

        List<BoqExcelRow> rows = listener.getDataList();
        if (rows.isEmpty()) {
            throw new BusinessException("解析结果为空，请检查文件内容");
        }

        // 6. 删除旧数据
        boqItemMapper.deleteByContractId(contractId);

        // 7. 构建层级树并逐条插入（插入后ID自动生成，后续子条目可引用父ID）
        List<BizBoqItem> items = buildHierarchyAndInsert(rows, contractId);

        // 8. 计算合计金额（所有 parentId==0 的顶层条目 totalPrice 之和，精确到小数点后2位）
        BigDecimal totalAmount = items.stream()
                .filter(item -> item.getParentId() == 0L)
                .map(item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 9. 回写合同的 contractAmount 字段
        contract.setContractAmount(totalAmount);
        contractMapper.updateById(contract);

        // 10. 计算最大层级数
        int maxLevel = items.stream()
                .mapToInt(BizBoqItem::getLevel)
                .max()
                .orElse(0);

        // 11. 组装返回结果
        BoqUploadResultVO result = new BoqUploadResultVO();
        result.setTotalItems(items.size());
        result.setLevelCount(maxLevel);
        result.setTotalAmount(totalAmount);
        result.setFileUrl(fileInfo.getFilePath());
        return result;
    }

    /**
     * 构建层级树并逐条插入
     * <p>
     * 按 itemCode 中的 "." 分隔计算层级:
     * - "1" → 1级
     * - "1.1" → 2级
     * - "1.1.1" → 3级
     * - "1.1.1.1" → 4级（最多4级）
     * <p>
     * 父级编码推导规则:
     * - "1.2.3" → 父编码 "1.2"
     * - "1" → 无父级，parentId=0
     * <p>
     * 逐条插入以确保父级ID在子条目设置parentId之前已生成（ASSIGN_ID策略在insert时自动填充）
     *
     * @param rows       解析出的Excel行数据
     * @param contractId 合同ID
     * @return 已插入的BOQ条目列表
     */
    List<BizBoqItem> buildHierarchyAndInsert(List<BoqExcelRow> rows, Long contractId) {
        List<BizBoqItem> items = new ArrayList<>();
        // code → item 的映射，用于查找父级（插入后item.getId()可用）
        Map<String, BizBoqItem> codeItemMap = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            BoqExcelRow row = rows.get(i);
            String code = row.getItemCode();

            // 计算层级：按 "." 分隔的段数，最多4级
            int level = code.split("\\.").length;
            if (level > 4) {
                level = 4;
            }

            // 推导父编码
            String parentCode = getParentCode(code);

            // 查找父级ID
            long parentId = 0L;
            if (parentCode != null) {
                BizBoqItem parentItem = codeItemMap.get(parentCode);
                if (parentItem != null) {
                    parentId = parentItem.getId();
                }
                // 父级不存在则 parentId=0
            }

            // 构建 BizBoqItem
            BizBoqItem item = new BizBoqItem();
            item.setContractId(contractId);
            item.setParentId(parentId);
            item.setItemCode(code);
            item.setItemName(row.getItemName());
            item.setUnit(row.getUnit());
            item.setQuantity(row.getQuantity());
            item.setUnitPrice(row.getUnitPrice());
            item.setTotalPrice(row.getTotalPrice());
            item.setCompletedQuantity(BigDecimal.ZERO);
            item.setLevel(level);
            item.setSortOrder(i + 1);

            // 逐条插入，插入后item.getId()由ASSIGN_ID策略自动填充
            boqItemMapper.insert(item);

            items.add(item);
            codeItemMap.put(code, item);
        }

        return items;
    }

    /**
     * 获取父级编码
     * <p>
     * "1.2.3" → "1.2"
     * "1.2" → "1"
     * "1" → null（无父级）
     *
     * @param code 当前编码
     * @return 父级编码，顶层返回null
     */
    String getParentCode(String code) {
        int lastDotIndex = code.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return null;
        }
        return code.substring(0, lastDotIndex);
    }

    /**
     * 检查合同BOQ是否被产值上报引用
     * <p>
     * 判断逻辑：如果存在该合同的产值上报记录，则视为已引用
     *
     * @param contractId 合同ID
     * @return true 表示存在引用
     */
    private boolean hasOutputReportReference(Long contractId) {
        LambdaQueryWrapper<BizOutputReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizOutputReport::getContractId, contractId);
        return outputReportMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查询合同的BOQ清单树形结构
     *
     * @param contractId 合同ID
     * @return 清单条目列表
     */
    public List<BizBoqItem> getBoqTree(Long contractId) {
        LambdaQueryWrapper<BizBoqItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBoqItem::getContractId, contractId)
                .orderByAsc(BizBoqItem::getSortOrder);
        return boqItemMapper.selectList(wrapper);
    }

    /**
     * 查询合同的BOQ清单平铺列表（供产值上报使用）
     *
     * @param contractId 合同ID
     * @return 清单条目列表
     */
    public List<BizBoqItem> getBoqFlat(Long contractId) {
        LambdaQueryWrapper<BizBoqItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBoqItem::getContractId, contractId)
                .orderByAsc(BizBoqItem::getLevel)
                .orderByAsc(BizBoqItem::getSortOrder);
        return boqItemMapper.selectList(wrapper);
    }

    /**
     * 删除合同的全部BOQ清单数据（逻辑删除）
     *
     * @param contractId 合同ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBoq(Long contractId) {
        // 校验合同存在
        BizConstructionContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        if (!ALLOWED_UPLOAD_STATUSES.contains(contract.getStatus())) {
            throw new BusinessException("当前合同状态不允许操作清单");
        }

        // 引用检查
        if (hasOutputReportReference(contractId)) {
            throw new BusinessException("该合同的清单条目已被产值上报引用，无法删除");
        }

        boqItemMapper.deleteByContractId(contractId);
    }
}
