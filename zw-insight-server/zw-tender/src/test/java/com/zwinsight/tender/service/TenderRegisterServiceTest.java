package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TenderRegisterService 单元测试
 * 覆盖：投标登记 CRUD + 状态约束 + 项目状态联动
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("投标登记服务 - TenderRegisterService")
class TenderRegisterServiceTest {

    @Mock private BizTenderRegisterMapper registerMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private TenderRegisterService tenderRegisterService;

    private BizTenderRegister sampleRegister;

    @BeforeEach
    void setUp() {
        sampleRegister = new BizTenderRegister();
        sampleRegister.setId(1L);
        sampleRegister.setProjectId(100L);
        sampleRegister.setOwnerCompany("测试业主单位");
        sampleRegister.setBidMethod("公开招标");
        sampleRegister.setRegisterDate(LocalDate.of(2026, 1, 15));
        sampleRegister.setOpenDate(LocalDate.of(2026, 2, 1));
        sampleRegister.setDepositAmount(new BigDecimal("50000.00"));
        sampleRegister.setStatus("REGISTERED");
    }

    // ═══════════════════════════════════════════
    // 分页查询
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("分页查询")
    class PageTests {

        @Test
        @DisplayName("分页查询：按项目ID过滤")
        void testPage_withProjectId() {
            Page<BizTenderRegister> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(sampleRegister));
            mockPage.setTotal(1);
            when(registerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PageResult<BizTenderRegister> result = tenderRegisterService.page(1, 10, 100L);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getProjectId()).isEqualTo(100L);
            verify(registerMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("分页查询：projectId 为 null 时查全部")
        void testPage_withoutProjectId() {
            Page<BizTenderRegister> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0);
            when(registerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PageResult<BizTenderRegister> result = tenderRegisterService.page(1, 10, null);

            assertThat(result).isNotNull();
            verify(registerMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ═══════════════════════════════════════════
    // 新增投标登记
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("新增投标登记")
    class SaveTests {

        @Test
        @DisplayName("新增：状态初始化为 REGISTERED + 项目状态回写为 TENDERING")
        void testSave_initializesStatusAndUpdatesProject() {
            BizTenderRegister register = new BizTenderRegister();
            register.setProjectId(100L);
            register.setOwnerCompany("测试业主");

            BizProject project = new BizProject();
            project.setId(100L);
            project.setStatus("FILED");
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(registerMapper.insert(any(BizTenderRegister.class))).thenReturn(1);

            tenderRegisterService.save(register);

            assertThat(register.getStatus()).isEqualTo("REGISTERED");
            verify(registerMapper).insert(register);
            verify(projectMapper).updateById(argThat(p -> "TENDERING".equals(p.getStatus())));
        }

        @Test
        @DisplayName("新增：项目不存在时跳过项目状态回写")
        void testSave_projectNotFound_skipsProjectUpdate() {
            BizTenderRegister register = new BizTenderRegister();
            register.setProjectId(999L);
            when(projectMapper.selectById(999L)).thenReturn(null);
            when(registerMapper.insert(any(BizTenderRegister.class))).thenReturn(1);

            tenderRegisterService.save(register);

            assertThat(register.getStatus()).isEqualTo("REGISTERED");
            verify(registerMapper).insert(register);
            verify(projectMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("新增：无论传入什么 status 都覆盖为 REGISTERED")
        void testSave_overridesStatusToRegistered() {
            BizTenderRegister register = new BizTenderRegister();
            register.setProjectId(100L);
            register.setStatus("WON"); // 传入非法状态

            BizProject project = new BizProject();
            project.setId(100L);
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(registerMapper.insert(any(BizTenderRegister.class))).thenReturn(1);

            tenderRegisterService.save(register);

            assertThat(register.getStatus()).isEqualTo("REGISTERED");
        }
    }

    // ═══════════════════════════════════════════
    // 查询详情
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("查询详情")
    class GetByIdTests {

        @Test
        @DisplayName("查询：存在则返回完整对象")
        void testGetById_found() {
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            BizTenderRegister result = tenderRegisterService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("REGISTERED");
            assertThat(result.getProjectId()).isEqualTo(100L);
            assertThat(result.getOwnerCompany()).isEqualTo("测试业主单位");
        }

        @Test
        @DisplayName("查询：不存在抛 BusinessException")
        void testGetById_notFound() {
            when(registerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> tenderRegisterService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("投标登记不存在");
        }
    }

    // ═══════════════════════════════════════════
    // 更新投标登记
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("更新投标登记")
    class UpdateTests {

        @Test
        @DisplayName("更新：正常更新")
        void testUpdate_success() {
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            sampleRegister.setOwnerCompany("修改后的业主");
            tenderRegisterService.update(sampleRegister);

            verify(registerMapper).updateById(sampleRegister);
        }

        @Test
        @DisplayName("更新：不存在抛异常")
        void testUpdate_notFound() {
            BizTenderRegister register = new BizTenderRegister();
            register.setId(999L);
            when(registerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> tenderRegisterService.update(register))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("投标登记不存在");
        }
    }

    // ═══════════════════════════════════════════
    // 删除：状态约束（仅 REGISTERED 可删）
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("删除 - 状态约束")
    class DeleteTests {

        @Test
        @DisplayName("删除：REGISTERED 状态可删")
        void testDelete_registeredAllowed() {
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            tenderRegisterService.delete(1L);

            verify(registerMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除：SUBMITTED 状态拒绝")
        void testDelete_submittedRejected() {
            sampleRegister.setStatus("SUBMITTED");
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            assertThatThrownBy(() -> tenderRegisterService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅报名状态可删除");
        }

        @Test
        @DisplayName("删除：WON 状态拒绝")
        void testDelete_wonRejected() {
            sampleRegister.setStatus("WON");
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            assertThatThrownBy(() -> tenderRegisterService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅报名状态可删除");
        }

        @Test
        @DisplayName("删除：LOST 状态拒绝")
        void testDelete_lostRejected() {
            sampleRegister.setStatus("LOST");
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            assertThatThrownBy(() -> tenderRegisterService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅报名状态可删除");
        }

        @Test
        @DisplayName("删除：不存在抛异常")
        void testDelete_notFound() {
            when(registerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> tenderRegisterService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("投标登记不存在");
        }
    }

    // ═══════════════════════════════════════════
    // 提交审批
    // ═══════════════════════════════════════════
    @Nested
    @DisplayName("提交审批")
    class SubmitTests {

        @Test
        @DisplayName("提交：状态变更为 SUBMITTED")
        void testSubmit_statusChanges() {
            when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

            tenderRegisterService.submit(1L);

            verify(registerMapper).updateById(argThat(r -> "SUBMITTED".equals(r.getStatus())));
        }

        @Test
        @DisplayName("提交：不存在抛异常")
        void testSubmit_notFound() {
            when(registerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> tenderRegisterService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("投标登记不存在");
        }
    }
}
