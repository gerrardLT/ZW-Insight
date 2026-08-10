package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizPersonCertificate;
import com.zwinsight.tender.mapper.BizPersonCertificateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PersonCertificateService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class PersonCertificateServiceTest {

    @Mock private BizPersonCertificateMapper certificateMapper;

    @InjectMocks
    private PersonCertificateService personCertificateService;

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void testPage_returnsResult() {
        when(certificateMapper.selectPage(any(Page.class), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PageResult<BizPersonCertificate> result =
                personCertificateService.page(1, 10, "张三", "建造师");

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("新增：委托 mapper.insert")
    void testSave_delegatesInsert() {
        BizPersonCertificate cert = new BizPersonCertificate();
        when(certificateMapper.insert(cert)).thenReturn(1);

        personCertificateService.save(cert);

        verify(certificateMapper).insert(cert);
    }

    @Test
    @DisplayName("更新：记录存在则更新")
    void testUpdate_exists_updates() {
        BizPersonCertificate cert = new BizPersonCertificate();
        cert.setId(1L);
        when(certificateMapper.selectById(1L)).thenReturn(new BizPersonCertificate());
        when(certificateMapper.updateById(cert)).thenReturn(1);

        personCertificateService.update(cert);

        verify(certificateMapper).updateById(cert);
    }

    @Test
    @DisplayName("更新：记录不存在抛业务异常")
    void testUpdate_notFound_throws() {
        BizPersonCertificate cert = new BizPersonCertificate();
        cert.setId(999L);
        when(certificateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> personCertificateService.update(cert))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人员证书不存在");
        verify(certificateMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除：记录存在则删除")
    void testDelete_exists_deletes() {
        when(certificateMapper.selectById(1L)).thenReturn(new BizPersonCertificate());

        personCertificateService.delete(1L);

        verify(certificateMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：记录不存在抛业务异常")
    void testDelete_notFound_throws() {
        when(certificateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> personCertificateService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人员证书不存在");
        verify(certificateMapper, never()).deleteById(any());
    }
}
