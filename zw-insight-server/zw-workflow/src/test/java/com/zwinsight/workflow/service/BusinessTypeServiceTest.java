package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.mapper.WfBusinessTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BusinessTypeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BusinessTypeServiceTest {

    @Mock
    private WfBusinessTypeMapper businessTypeMapper;

    @InjectMocks
    private BusinessTypeService businessTypeService;

    @Test
    @DisplayName("查询树：空数据返回空列表")
    void testGetTree_empty() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(businessTypeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<Map<String, Object>> tree = businessTypeService.getTree();

            assertThat(tree).isEmpty();
        }
    }

    @Test
    @DisplayName("查询树：构建父子层级")
    void testGetTree_buildsHierarchy() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            WfBusinessType parent = new WfBusinessType();
            parent.setId(1L);
            parent.setTypeName("合同审批");
            parent.setTypeCode("CONTRACT");
            parent.setParentId(0L);
            parent.setSortOrder(1);

            WfBusinessType child = new WfBusinessType();
            child.setId(2L);
            child.setTypeName("主合同");
            child.setTypeCode("CONTRACT_MAIN");
            child.setParentId(1L);
            child.setSortOrder(1);

            when(businessTypeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(parent, child));

            List<Map<String, Object>> tree = businessTypeService.getTree();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).get("typeCode")).isEqualTo("CONTRACT");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get(0).get("children");
            assertThat(children).hasSize(1);
            assertThat(children.get(0).get("typeCode")).isEqualTo("CONTRACT_MAIN");
        }
    }

    @Test
    @DisplayName("新增：编码已存在时抛异常")
    void testCreate_duplicateCode_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            WfBusinessType bt = new WfBusinessType();
            bt.setTypeCode("DUP");
            when(businessTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> businessTypeService.create(bt))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("业务类型编码已存在");
        }
    }

    @Test
    @DisplayName("新增：默认 parentId=0 和 sortOrder=0")
    void testCreate_defaultsApplied() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            WfBusinessType bt = new WfBusinessType();
            bt.setTypeName("新类型");
            bt.setTypeCode("NEW_TYPE");
            when(businessTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(businessTypeMapper.insert(any(WfBusinessType.class))).thenReturn(1);

            WfBusinessType result = businessTypeService.create(bt);

            assertThat(result.getParentId()).isEqualTo(0L);
            assertThat(result.getSortOrder()).isEqualTo(0);
            verify(businessTypeMapper).insert(bt);
        }
    }

    @Test
    @DisplayName("更新：不存在时抛异常")
    void testUpdate_notFound_throws() {
        WfBusinessType bt = new WfBusinessType();
        bt.setId(999L);
        when(businessTypeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> businessTypeService.update(bt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务类型不存在");
    }

    @Test
    @DisplayName("删除：有子节点时拒绝")
    void testDelete_hasChildren_throws() {
        when(businessTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> businessTypeService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子节点");
    }

    @Test
    @DisplayName("删除：正常删除")
    void testDelete_success() {
        when(businessTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        businessTypeService.delete(1L);

        verify(businessTypeMapper).deleteById(1L);
    }
}
