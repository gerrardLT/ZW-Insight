package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.SysUserExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 系统用户导入监听器
 * TODO: 接入 SysUserMapper 完成实际持久化
 */
@Slf4j
public class SysUserImportListener extends AbstractImportListener<SysUserExcelDTO> {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    protected String validate(SysUserExcelDTO data) {
        if (StrUtil.isBlank(data.getRealName())) {
            return "姓名不能为空";
        }
        if (StrUtil.isBlank(data.getPhone())) {
            return "手机号不能为空";
        }
        if (!PHONE_PATTERN.matcher(data.getPhone().trim()).matches()) {
            return "手机号格式错误";
        }
        return null;
    }

    @Override
    protected void batchSave(List<SysUserExcelDTO> dataList) {
        // TODO: 调用 SysUserMapper 批量插入用户记录
        log.info("系统用户批量导入 {} 条（待实现持久化）", dataList.size());
    }
}
