package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFundCategory;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizFundCategoryMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FundCategoryService 单元测试（53_V2026_51 资金分类科目）
 */
@ExtendWith(MockitoExtension.class)
class FundCategoryServiceTest {

    @Mock private BizFundCategoryMapper fundCategoryMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;

    @InjectMocks
    private FundCategoryService fundCategoryService;

    private BizFundCategory sampleCategory() {
        BizFundCategory category = new BizFundCategory();
        category.setCode("EXP-DIRECT-MATERIAL");
        category.setName("材料款");
        category.setDirection("EXPENSE");
        category.setParentId(0L);
        return category;
    }

    @Nested
    @DisplayName("save() 新增科目")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 顶级科目层级=1，默认启用并插入")
        void save_normalPath_topLevel() {
            BizFundCategory category = sampleCategory();
            when(fundCategoryMapper.selectCount(any())).thenReturn(0L);

            fundCategoryService.save(category);

            assertThat(category.getLevel()).isEqualTo(1);
            assertThat(category.getStatus()).isEqualTo("ENABLED");
            assertThat(category.getIsSystem()).isZero();
            verify(fundCategoryMapper).insert(category);
        }

        @Test
        @DisplayName("异常路径 — 编码重复时拒绝保存")
        void save_duplicateCode_rejected() {
            when(fundCategoryMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> fundCategoryService.save(sampleCategory()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        @DisplayName("异常路径 — 子科目方向与父级不一致时拒绝")
        void save_directionMismatch_rejected() {
            BizFundCategory parent = sampleCategory();
            parent.setId(900120L);
            parent.setLevel(1);
            parent.setStatus("ENABLED");
            BizFundCategory child = sampleCategory();
            child.setCode("NEW-CHILD");
            child.setDirection("INCOME"); // 父级是 EXPENSE
            child.setParentId(900120L);

            when(fundCategoryMapper.selectById(900120L)).thenReturn(parent);

            assertThatThrownBy(() -> fundCategoryService.save(child))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("方向必须与父级一致");
        }
    }

    @Nested
    @DisplayName("delete() 删除科目")
    class DeleteTests {

        @Test
        @DisplayName("正常路径 — 自定义科目无子级无引用时删除")
        void delete_normalPath() {
            BizFundCategory category = sampleCategory();
            category.setId(1L);
            category.setIsSystem(0);
            when(fundCategoryMapper.selectById(1L)).thenReturn(category);
            when(fundCategoryMapper.selectCount(any())).thenReturn(0L);
            when(paymentApplyMapper.selectCount(any())).thenReturn(0L);

            fundCategoryService.delete(1L);

            verify(fundCategoryMapper).deleteById(1L);
        }

        @Test
        @DisplayName("异常路径 — 被付款申请引用时拒绝删除")
        void delete_referencedByPayment_rejected() {
            BizFundCategory category = sampleCategory();
            category.setId(1L);
            category.setIsSystem(0);
            when(fundCategoryMapper.selectById(1L)).thenReturn(category);
            when(fundCategoryMapper.selectCount(any())).thenReturn(0L);
            when(paymentApplyMapper.selectCount(any())).thenReturn(3L);

            assertThatThrownBy(() -> fundCategoryService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3笔付款申请引用");
        }

        @Test
        @DisplayName("异常路径 — 系统内置科目拒绝删除")
        void delete_systemCategory_rejected() {
            BizFundCategory category = sampleCategory();
            category.setId(1L);
            category.setIsSystem(1);
            when(fundCategoryMapper.selectById(1L)).thenReturn(category);

            assertThatThrownBy(() -> fundCategoryService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("系统内置科目不允许删除");
        }
    }

    @Nested
    @DisplayName("getByCode() 编码校验")
    class GetByCodeTests {

        @Test
        @DisplayName("正常路径 — 有效编码且方向匹配时返回科目")
        void getByCode_normalPath() {
            BizFundCategory category = sampleCategory();
            category.setStatus("ENABLED");
            when(fundCategoryMapper.selectOne(any())).thenReturn(category);

            BizFundCategory result = fundCategoryService.getByCode("EXP-DIRECT-MATERIAL", "EXPENSE");

            assertThat(result.getName()).isEqualTo("材料款");
        }

        @Test
        @DisplayName("异常路径 — 编码不存在时抛业务异常（不静默）")
        void getByCode_notFound_throws() {
            when(fundCategoryMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> fundCategoryService.getByCode("NOT-EXIST", "EXPENSE"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }
    }
}
