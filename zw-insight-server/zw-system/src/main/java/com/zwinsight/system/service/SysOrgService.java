package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
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
                // sys_* 免拦截器过滤，显式按当前租户过滤（无上下文的内部调用零回归）
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysOrg::getTenantId, SecurityContextHolder.getTenantId())
                .orderByAsc(SysOrg::getSortOrder);
        return orgMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询
     */
    public SysOrg getById(Long id) {
        SysOrg org = orgMapper.selectById(id);
        return sameTenant(org) ? org : null;
    }

    /** 机构属于其他租户时视为不存在（sys_* 免拦截器过滤） */
    private boolean sameTenant(SysOrg org) {
        Long tenantId = SecurityContextHolder.getTenantId();
        return org == null || tenantId == null || org.getTenantId() == null || tenantId.equals(org.getTenantId());
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
            if (parent == null || !sameTenant(parent)) {
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
        if (existing == null || !sameTenant(existing)) {
            throw new BusinessException("机构不存在");
        }
        // 白名单拷贝：tenantId/deleted/version/ancestors 不接受请求体
        if (org.getOrgName() != null) existing.setOrgName(org.getOrgName());
        if (org.getOrgCode() != null) existing.setOrgCode(org.getOrgCode());
        if (org.getOrgType() != null) existing.setOrgType(org.getOrgType());
        if (org.getSortOrder() != null) existing.setSortOrder(org.getSortOrder());
        if (org.getStatus() != null) {
            assertValidStatus(org.getStatus());
            existing.setStatus(org.getStatus());
        }

        // 子树路径前缀必须在改写本机构前取旧值
        String oldPrefix = (existing.getAncestors() == null ? "0" : existing.getAncestors()) + "," + existing.getId();
        String newPrefix = null;
        if (org.getParentId() != null && !org.getParentId().equals(existing.getParentId())) {
            if (org.getParentId().equals(existing.getId())) {
                throw new BusinessException("上级机构不能是自己");
            }
            String newAncestors;
            if (org.getParentId() == 0L) {
                newAncestors = "0";
            } else {
                SysOrg parent = orgMapper.selectById(org.getParentId());
                if (parent == null || !sameTenant(parent)) {
                    throw new BusinessException("父机构不存在");
                }
                // 防环：新上级不能是本机构的后代，否则子树脱离根形成孤岛
                if (isDescendantOf(parent, existing.getId())) {
                    throw new BusinessException("上级机构不能是自己的下级机构");
                }
                newAncestors = parent.getAncestors() + "," + parent.getId();
            }
            existing.setParentId(org.getParentId());
            existing.setAncestors(newAncestors);
            newPrefix = newAncestors + "," + existing.getId();
        }

        List<SysOrg> descendants = newPrefix == null ? List.of() : findDescendants(oldPrefix);
        orgMapper.updateById(existing);
        // 整棵子树的 ancestors 同步重算（原实现只更新当前节点，后代路径失真）
        for (SysOrg d : descendants) {
            d.setAncestors(newPrefix + d.getAncestors().substring(oldPrefix.length()));
            orgMapper.updateById(d);
        }
    }

    private static void assertValidStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值无效，仅支持 0（停用）或 1（启用）");
        }
    }

    /** node 是否为 id 的后代（其 ancestors 路径含 id） */
    private static boolean isDescendantOf(SysOrg node, Long id) {
        if (id.equals(node.getId())) {
            return true;
        }
        if (node.getAncestors() == null) {
            return false;
        }
        for (String a : node.getAncestors().split(",")) {
            if (a.trim().equals(String.valueOf(id))) {
                return true;
            }
        }
        return false;
    }

    /** 某前缀下的全部后代：ancestors 等于前缀，或以「前缀,」开头（避免 1,2 误匹配 1,20） */
    private List<SysOrg> findDescendants(String prefix) {
        return orgMapper.selectList(new LambdaQueryWrapper<SysOrg>().likeRight(SysOrg::getAncestors, prefix))
                .stream()
                .filter(o -> o.getAncestors().equals(prefix) || o.getAncestors().startsWith(prefix + ","))
                .toList();
    }

    /**
     * 删除机构
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 他租户机构按不存在处理：否则凭 ID 即可删除别家机构（sys_* 免拦截器过滤）
        SysOrg target = orgMapper.selectById(id);
        if (target != null && !sameTenant(target)) {
            throw new BusinessException("机构不存在");
        }
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
        assertValidStatus(status);
        SysOrg current = orgMapper.selectById(id);
        if (current == null || !sameTenant(current)) {
            throw new BusinessException("机构不存在");
        }
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setStatus(status);
        orgMapper.updateById(org);
    }
}
