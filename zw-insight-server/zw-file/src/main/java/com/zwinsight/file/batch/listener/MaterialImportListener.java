package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.MaterialExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 材料字典导入监听器
 * TODO: 接入 BdMaterialMapper 完成实际持久化
 */
@Slf4j
public class MaterialImportListener extends AbstractImportListener<MaterialExcelDTO> {

    @Override
    protected String validate(MaterialExcelDTO data) {
        if (StrUtil.isBlank(data.getMaterialName())) {
            return "材料名称不能为空";
        }
        if (StrUtil.isBlank(data.getMaterialCode())) {
            return "材料编码不能为空";
        }
        if (StrUtil.isBlank(data.getUnit())) {
            return "单位不能为空";
        }
        return null;
    }

    @Override
    protected void batchSave(List<MaterialExcelDTO> dataList) {
        // TODO: 调用 BdMaterialMapper 批量插入材料字典记录
        log.info("材料字典批量导入 {} 条（待实现持久化）", dataList.size());
    }
}
