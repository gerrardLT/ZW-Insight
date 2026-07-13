package com.zwinsight.basedata.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdCompany;
import com.zwinsight.basedata.mapper.BdCompanyMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自持公司服务
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final BdCompanyMapper companyMapper;

    /**
     * 分页查询公司
     */
    public PageResult<BdCompany> page(int page, int size, String companyName, Integer status) {
        Page<BdCompany> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BdCompany> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(companyName), BdCompany::getCompanyName, companyName)
                .eq(status != null, BdCompany::getStatus, status)
                .orderByDesc(BdCompany::getCreatedAt);
        Page<BdCompany> result = companyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 列表查询（供前端下拉选择使用）
     * 按公司名称模糊匹配 + 可选状态过滤，按创建时间倒序，限制返回条数。
     */
    public List<BdCompany> list(String companyName, Integer status) {
        LambdaQueryWrapper<BdCompany> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(companyName), BdCompany::getCompanyName, companyName)
                .eq(status != null, BdCompany::getStatus, status)
                .orderByDesc(BdCompany::getCreatedAt)
                .last("LIMIT 50");
        return companyMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询
     */
    public BdCompany getById(Long id) {
        BdCompany company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException("公司不存在");
        }
        return company;
    }

    /**
     * 新增公司
     */
    public void save(BdCompany company) {
        companyMapper.insert(company);
    }

    /**
     * 更新公司
     */
    public void update(BdCompany company) {
        BdCompany existing = companyMapper.selectById(company.getId());
        if (existing == null) {
            throw new BusinessException("公司不存在");
        }
        companyMapper.updateById(company);
    }

    /**
     * 删除公司
     */
    public void delete(Long id) {
        companyMapper.deleteById(id);
    }
}
