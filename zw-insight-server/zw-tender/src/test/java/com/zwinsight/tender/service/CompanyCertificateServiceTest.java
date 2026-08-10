package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizCompanyCertificate;
import com.zwinsight.tender.mapper.BizCompanyCertificateMapper;
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
 * CompanyCertificateService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CompanyCertificateServiceTest {

    @Mock private BizCompanyCertificateMapper certificateMapper;

    @InjectMocks
    private CompanyCertificateService companyCertificateService;

    @Test
    @DisplayName("分页查询：返回 PageResult 且记录透传")
    void testPage_returnsResult() {
        when(certificateMapper.selectPage(any(Page.class), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PageResult<BizCompanyCertificate> result =
                companyCertificateService.page(1, 10, "资质", "施工");

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isEmpty();
        verify(certificateMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("新增：委托 mapper.insert")
    void testSave_delegatesInsert() {
        BizCompanyCertificate cert = new BizCompanyCertificate();
        when(certificateMapper.insert(cert)).thenReturn(1);

        companyCertificateService.save(cert);

        verify(certificateMapper).insert(cert);
    }

    @Test
    @DisplayName("更新：记录存在则更新")
    void testUpdate_exists_updates() {
        BizCompanyCertificate cert = new BizCompanyCertificate();
        cert.setId(1L);
        when(certificateMapper.selectById(1L)).thenReturn(new BizCompanyCertificate());
        when(certificateMapper.updateById(cert)).thenReturn(1);

        companyCertificateService.update(cert);

        verify(certificateMapper).updateById(cert);
    }

    @Test
    @DisplayName("更新：记录不存在抛业务异常")
    void testUpdate_notFound_throws() {
        BizCompanyCertificate cert = new BizCompanyCertificate();
        cert.setId(999L);
        when(certificateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> companyCertificateService.update(cert))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("企业证书不存在");
        verify(certificateMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除：记录存在则删除")
    void testDelete_exists_deletes() {
        when(certificateMapper.selectById(1L)).thenReturn(new BizCompanyCertificate());

        companyCertificateService.delete(1L);

        verify(certificateMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：记录不存在抛业务异常")
    void testDelete_notFound_throws() {
        when(certificateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> companyCertificateService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("企业证书不存在");
        verify(certificateMapper, never()).deleteById(any());
    }
}
