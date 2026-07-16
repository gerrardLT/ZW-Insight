package com.zwinsight.tender.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizPersonCertificate;
import com.zwinsight.tender.mapper.BizPersonCertificateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 人员证书服务
 */
@Service
@RequiredArgsConstructor
public class PersonCertificateService {

    private final BizPersonCertificateMapper certificateMapper;

    /**
     * 分页查询
     */
    public PageResult<BizPersonCertificate> page(int page, int size, String personName, String certificateType) {
        Page<BizPersonCertificate> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPersonCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(personName), BizPersonCertificate::getPersonName, personName)
                .eq(StrUtil.isNotBlank(certificateType), BizPersonCertificate::getCertificateType, certificateType)
                .orderByDesc(BizPersonCertificate::getCreatedAt);
        Page<BizPersonCertificate> result = certificateMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增
     */
    public void save(BizPersonCertificate certificate) {
        certificateMapper.insert(certificate);
    }

    /**
     * 更新
     */
    public void update(BizPersonCertificate certificate) {
        BizPersonCertificate existing = certificateMapper.selectById(certificate.getId());
        if (existing == null) {
            throw new BusinessException("人员证书不存在");
        }
        certificateMapper.updateById(certificate);
    }

    /**
     * 删除人员证书
     * <p>注：投标登记（biz_tender_register）与投标任务（biz_tender_task）均不以证书ID关联，
     * 故不做引用校验。</p>
     */
    public void delete(Long id) {
        BizPersonCertificate existing = certificateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("人员证书不存在");
        }
        certificateMapper.deleteById(id);
    }
}
