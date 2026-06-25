package com.zwinsight.basedata.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.dto.SupplierExcelDTO;
import com.zwinsight.basedata.mapper.BdSupplierMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.reference.ReferenceCheck;
import com.zwinsight.common.reference.ReferenceRelation;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商服务
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final BdSupplierMapper supplierMapper;

    /**
     * 分页查询供应商（支持类型和状态筛选）
     */
    public PageResult<BdSupplier> page(int page, int size, String supplierName, String supplierType, Integer status) {
        Page<BdSupplier> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BdSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(supplierName), BdSupplier::getSupplierName, supplierName)
                .eq(StrUtil.isNotBlank(supplierType), BdSupplier::getSupplierType, supplierType)
                .eq(status != null, BdSupplier::getStatus, status)
                .orderByDesc(BdSupplier::getCreatedAt);
        Page<BdSupplier> result = supplierMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BdSupplier getById(Long id) {
        BdSupplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        return supplier;
    }

    /**
     * 新增供应商
     */
    public void save(BdSupplier supplier) {
        supplierMapper.insert(supplier);
    }

    /**
     * 更新供应商
     */
    public void update(BdSupplier supplier) {
        BdSupplier existing = supplierMapper.selectById(supplier.getId());
        if (existing == null) {
            throw new BusinessException("供应商不存在");
        }
        supplierMapper.updateById(supplier);
    }

    /**
     * 删除供应商（引用校验：采购合同、入库单、询价）
     */
    @ReferenceCheck({
            @ReferenceRelation(tableName = "biz_purchase_contract", column = "supplier_id",
                    displayName = "采购合同", codeColumn = "contract_code"),
            @ReferenceRelation(tableName = "biz_material_inbound", column = "supplier_id",
                    displayName = "入库单", codeColumn = "inbound_code"),
            @ReferenceRelation(tableName = "biz_purchase_inquiry", column = "supplier_id",
                    displayName = "询价单", codeColumn = "inquiry_code")
    })
    public void delete(Long id) {
        supplierMapper.deleteById(id);
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        supplierMapper.deleteBatchIds(ids);
    }

    /**
     * 批量导入供应商（EasyExcel）
     */
    @Transactional(rollbackFor = Exception.class)
    public int importSuppliers(MultipartFile file) {
        List<BdSupplier> suppliers = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), SupplierExcelDTO.class,
                    new PageReadListener<SupplierExcelDTO>(dataList -> {
                        for (SupplierExcelDTO dto : dataList) {
                            BdSupplier supplier = new BdSupplier();
                            supplier.setSupplierName(dto.getSupplierName());
                            supplier.setSupplierCode(dto.getSupplierCode());
                            supplier.setSupplierType(dto.getSupplierType());
                            supplier.setContactName(dto.getContactName());
                            supplier.setContactPhone(dto.getContactPhone());
                            supplier.setAddress(dto.getAddress());
                            supplier.setBankName(dto.getBankName());
                            supplier.setBankAccount(dto.getBankAccount());
                            supplier.setTaxNumber(dto.getTaxNumber());
                            supplier.setStatus(1);
                            suppliers.add(supplier);
                        }
                    })).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        if (suppliers.isEmpty()) {
            throw new BusinessException("导入文件无有效数据");
        }

        for (BdSupplier supplier : suppliers) {
            // 跳过已存在相同编码的供应商
            if (StrUtil.isNotBlank(supplier.getSupplierCode())) {
                long count = supplierMapper.selectCount(
                        new LambdaQueryWrapper<BdSupplier>()
                                .eq(BdSupplier::getSupplierCode, supplier.getSupplierCode()));
                if (count > 0) {
                    continue;
                }
            }
            supplierMapper.insert(supplier);
        }
        return suppliers.size();
    }
}
