package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.SupplierExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 供应商导入监听器
 * TODO: 接入 BdSupplierMapper 完成实际持久化
 */
@Slf4j
public class SupplierImportListener extends AbstractImportListener<SupplierExcelDTO> {

    @Override
    protected String validate(SupplierExcelDTO data) {
        if (StrUtil.isBlank(data.getSupplierName())) {
            return "供应商名称不能为空";
        }
        if (StrUtil.isBlank(data.getCreditCode())) {
            return "统一社会信用代码不能为空";
        }
        if (StrUtil.isBlank(data.getContactPerson())) {
            return "联系人不能为空";
        }
        return null;
    }

    @Override
    protected void batchSave(List<SupplierExcelDTO> dataList) {
        // TODO: 调用 BdSupplierMapper 批量插入供应商记录
        log.info("供应商批量导入 {} 条（待实现持久化）", dataList.size());
    }
}
