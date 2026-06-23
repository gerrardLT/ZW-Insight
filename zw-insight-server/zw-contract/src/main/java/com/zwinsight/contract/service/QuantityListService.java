package com.zwinsight.contract.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizQuantityList;
import com.zwinsight.contract.dto.QuantityListExcelDTO;
import com.zwinsight.contract.mapper.BizQuantityListMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工程量清单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuantityListService {

    private final BizQuantityListMapper quantityListMapper;

    /**
     * 分页查询
     */
    public PageResult<BizQuantityList> page(int page, int size, Long projectId, Long contractId) {
        Page<BizQuantityList> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizQuantityList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizQuantityList::getProjectId, projectId)
                .eq(contractId != null, BizQuantityList::getContractId, contractId)
                .orderByDesc(BizQuantityList::getCreatedAt);
        Page<BizQuantityList> result = quantityListMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存工程量清单
     */
    public void save(BizQuantityList quantityList) {
        // 计算金额 = 数量 × 单价
        if (quantityList.getQuantity() != null && quantityList.getUnitPrice() != null) {
            quantityList.setAmount(quantityList.getQuantity().multiply(quantityList.getUnitPrice()));
        }
        quantityListMapper.insert(quantityList);
    }

    /**
     * 更新工程量清单
     */
    public void update(BizQuantityList quantityList) {
        BizQuantityList existing = quantityListMapper.selectById(quantityList.getId());
        if (existing == null) {
            throw new BusinessException("工程量清单不存在");
        }
        // 重新计算金额
        if (quantityList.getQuantity() != null && quantityList.getUnitPrice() != null) {
            quantityList.setAmount(quantityList.getQuantity().multiply(quantityList.getUnitPrice()));
        }
        quantityListMapper.updateById(quantityList);
    }

    /**
     * 删除工程量清单
     */
    public void delete(Long id) {
        BizQuantityList existing = quantityListMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工程量清单不存在");
        }
        quantityListMapper.deleteById(id);
    }

    /**
     * 批量导入（Excel）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchImport(MultipartFile file, Long projectId, Long contractId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }
        if (projectId == null || contractId == null) {
            throw new BusinessException("项目ID和合同ID不能为空");
        }

        List<BizQuantityList> importList = new ArrayList<>();

        try {
            EasyExcel.read(file.getInputStream(), QuantityListExcelDTO.class,
                    new PageReadListener<QuantityListExcelDTO>(dataList -> {
                        for (QuantityListExcelDTO dto : dataList) {
                            BizQuantityList item = new BizQuantityList();
                            item.setProjectId(projectId);
                            item.setContractId(contractId);
                            item.setItemName(dto.getItemName());
                            item.setSpecification(dto.getSpecification());
                            item.setUnit(dto.getUnit());
                            item.setQuantity(dto.getQuantity());
                            item.setUnitPrice(dto.getUnitPrice());
                            // 计算金额
                            if (dto.getQuantity() != null && dto.getUnitPrice() != null) {
                                item.setAmount(dto.getQuantity().multiply(dto.getUnitPrice()));
                            } else {
                                item.setAmount(BigDecimal.ZERO);
                            }
                            importList.add(item);
                            quantityListMapper.insert(item);
                        }
                    })).sheet().doRead();
        } catch (IOException e) {
            log.error("Excel导入失败", e);
            throw new BusinessException("Excel文件读取失败：" + e.getMessage());
        }

        return importList.size();
    }
}
