package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.mapper.SysOrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 机构管理服务
 */
@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;

    /**
     * 查询机构列表（全量，用于前端构建树）
     */
    public List<SysOrg> list(String orgName, Integer status) {
        LambdaQueryWrapper<SysOrg> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(orgName), SysOrg::getOrgName, orgName)
                .eq(status != null, SysOrg::getStatus, status)
                .orderByAsc(SysOrg::getSortOrder);
        return orgMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询
     */
    public SysOrg getById(Long id) {
        return orgMapper.selectById(id);
    }

    /**
     * 新增机构
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(SysOrg org) {
        // 设置祖先路径
        if (org.getParentId() == null || org.getParentId() == 0L) {
            org.setParentId(0L);
            org.setAncestors("0");
        } else {
            SysOrg parent = orgMapper.selectById(org.getParentId());
            if (parent == null) {
                throw new BusinessException("父机构不存在");
            }
            org.setAncestors(parent.getAncestors() + "," + parent.getId());
        }
        orgMapper.insert(org);
    }

    /**
     * 更新机构
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SysOrg org) {
        SysOrg existing = orgMapper.selectById(org.getId());
        if (existing == null) {
            throw new BusinessException("机构不存在");
        }
        // 如果父级变更，更新ancestors
        if (org.getParentId() != null && !org.getParentId().equals(existing.getParentId())) {
            if (org.getParentId() == 0L) {
                org.setAncestors("0");
            } else {
                SysOrg parent = orgMapper.selectById(org.getParentId());
                if (parent == null) {
                    throw new BusinessException("父机构不存在");
                }
                org.setAncestors(parent.getAncestors() + "," + parent.getId());
            }
        }
        orgMapper.updateById(org);
    }

    /**
     * 删除机构
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查是否有子机构
        long childCount = orgMapper.selectCount(
                new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("存在子机构，无法删除");
        }
        // 检查是否有关联人员
        long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId, id));
        if (userCount > 0) {
            throw new BusinessException("机构下存在人员，无法删除");
        }
        orgMapper.deleteById(id);
    }

    /**
     * 更新状态
     */
    public void updateStatus(Long id, Integer status) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setStatus(status);
        orgMapper.updateById(org);
    }
}
