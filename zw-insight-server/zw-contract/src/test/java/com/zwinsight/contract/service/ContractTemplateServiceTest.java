package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizContractTemplate;
import com.zwinsight.contract.mapper.BizContractTemplateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ContractTemplateService 单元测试
 * <p>重点：${变量名} 占位符替换规则、使用次数自增、编码唯一性校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContractTemplateServiceTest {

    @Mock
    private BizContractTemplateMapper templateMapper;

    @InjectMocks
    private ContractTemplateService service;

    private BizContractTemplate template(Long id, String content, int usageCount) {
        BizContractTemplate t = new BizContractTemplate();
        t.setId(id);
        t.setTemplateCode("TPL-001");
        t.setTemplateContent(content);
        t.setUsageCount(usageCount);
        return t;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizContractTemplate> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(template(1L, "内容", 3)));
        page.setTotal(1L);
        when(templateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizContractTemplate> result = service.page(1, 10, "CONSTRUCTION");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("getById - 不存在抛异常")
    void getById_notFound_throws() {
        when(templateMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("save - 编码重复抛异常；正常时初始化使用次数与状态")
    void save_variants() {
        when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.save(template(null, "x", 0)))
                .hasMessageContaining("模板编码已存在");

        when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        BizContractTemplate t = template(null, "x", 99);
        service.save(t);
        assertThat(t.getUsageCount()).isZero();
        assertThat(t.getStatus()).isEqualTo(1);
        verify(templateMapper).insert(t);
    }

    @Test
    @DisplayName("update/delete - 不存在抛异常")
    void updateAndDelete_notFound_throws() {
        when(templateMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(template(99L, "x", 0)))
                .hasMessageContaining("模板不存在");
        assertThatThrownBy(() -> service.delete(99L))
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("renderTemplate - 已知变量替换、未知变量保留原占位符、使用次数+1")
    void renderTemplate_replacesVariables() {
        BizContractTemplate t = template(1L,
                "工程：${projectName}，乙方：${partyBName}，金额：${contractAmount}，未知：${unknownVar}", 5);
        when(templateMapper.selectById(1L)).thenReturn(t);

        Map<String, String> vars = new HashMap<>();
        vars.put("projectName", "滨江花园");
        vars.put("partyBName", "中维建设");
        // contractAmount 未提供 → 保留原占位符

        String result = service.renderTemplate(1L, vars);

        assertThat(result).isEqualTo("工程：滨江花园，乙方：中维建设，金额：${contractAmount}，未知：${unknownVar}");
        assertThat(t.getUsageCount()).isEqualTo(6);
        verify(templateMapper).updateById(t);
    }

    @Test
    @DisplayName("renderTemplate - 内容为空抛异常")
    void renderTemplate_emptyContent_throws() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, "  ", 0));

        assertThatThrownBy(() -> service.renderTemplate(1L, new HashMap<>()))
                .hasMessageContaining("模板内容为空");
    }

    @Test
    @DisplayName("previewTemplate - 内置示例数据替换")
    void previewTemplate_usesSampleData() {
        BizContractTemplate t = template(1L, "${projectName}由${partyAName}发包", 0);
        when(templateMapper.selectById(1L)).thenReturn(t);

        String preview = service.previewTemplate(1L);

        assertThat(preview).isEqualTo("XX市政道路工程由XX建设投资有限公司发包");
    }
}
