package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgTemplate;
import com.zwinsight.message.mapper.MsgTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 消息模板服务
 */
@Service("messageTemplateService")
@RequiredArgsConstructor
public class TemplateService {

    private final MsgTemplateMapper templateMapper;

    /**
     * 分页查询模板
     */
    public PageResult<MsgTemplate> page(int page, int size, String templateName) {
        Page<MsgTemplate> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(templateName), MsgTemplate::getTemplateName, templateName)
                .orderByDesc(MsgTemplate::getCreatedAt);
        Page<MsgTemplate> result = templateMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public MsgTemplate getById(Long id) {
        MsgTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    /**
     * 新增模板
     */
    public void save(MsgTemplate template) {
        templateMapper.insert(template);
    }

    /**
     * 更新模板
     */
    public void update(MsgTemplate template) {
        MsgTemplate existing = templateMapper.selectById(template.getId());
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        templateMapper.updateById(template);
    }

    /**
     * 删除模板
     */
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }
}
