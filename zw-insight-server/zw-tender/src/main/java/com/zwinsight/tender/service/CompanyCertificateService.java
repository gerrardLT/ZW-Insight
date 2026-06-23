package com.zwinsight.tender.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizCompanyCertificate;
import com.zwinsight.tender.mapper.BizCompanyCertificateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 企业证书服务
 */
@Service
@RequiredArgsConstructor
public class CompanyCertificateService {

    private final BizCompanyCertificateMapper certificateMapper;

    /**
     * 分页查询
     */
    public PageResult<BizCompanyCertificate> page(int page, int size, String certificateName, String certificateType) {
        Page<BizCompanyCertificate> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizCompanyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(certificateName), BizCompanyCertificate::getCertificateName, certificateName)
                .eq(StrUtil.isNotBlank(certificateType), BizCompanyCertificate::getCertificateType, certificateType)
                .orderByDesc(BizCompanyCertificate::getCreatedAt);
        Page<BizCompanyCertificate> result = certificateMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增
     */
    public void save(BizCompanyCertificate certificate) {
        certificateMapper.insert(certificate);
    }

    /**
     * 更新
     */
    public void update(BizCompanyCertificate certificate) {
        BizCompanyCertificate existing = certificateMapper.selectById(certificate.getId());
        if (existing == null) {
            throw new BusinessException("企业证书不存在");
        }
        certificateMapper.updateById(certificate);
    }

    /**
     * 删除（检查引用）
     */
    public void delete(Long id) {
        BizCompanyCertificate existing = certificateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("企业证书不存在");
        }
        certificateMapper.deleteById(id);
    }
}
