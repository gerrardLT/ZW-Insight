package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.mapper.BizSupplierEvaluationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierEvaluationServiceTest {

    @Mock private BizSupplierEvaluationMapper evaluationMapper;

    @InjectMocks
    private SupplierEvaluationService evaluationService;

    @Test
    @DisplayName("新增评价：自动计算综合评分")
    void testSave_calculatesTotalScore() {
        BizSupplierEvaluation eval = new BizSupplierEvaluation();
        eval.setQualityScore(80);
        eval.setTimelinessScore(90);
        eval.setPriceScore(70);
        eval.setServiceScore(85);
        eval.setCooperationScore(75);
        when(evaluationMapper.insert(any(BizSupplierEvaluation.class))).thenReturn(1);

        evaluationService.save(eval);

        // (80+90+70+85+75)/5 = 80
        assertThat(eval.getTotalScore()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("新增评价：无评分项时设为零")
    void testSave_noScores() {
        BizSupplierEvaluation eval = new BizSupplierEvaluation();
        when(evaluationMapper.insert(any(BizSupplierEvaluation.class))).thenReturn(1);

        evaluationService.save(eval);

        assertThat(eval.getTotalScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("获取平均评分：有评价返回均值")
    void testGetAvgScore_hasEvaluations() {
        BizSupplierEvaluation e1 = new BizSupplierEvaluation();
        e1.setTotalScore(new BigDecimal("80.00"));
        BizSupplierEvaluation e2 = new BizSupplierEvaluation();
        e2.setTotalScore(new BigDecimal("90.00"));
        when(evaluationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e1, e2));

        BigDecimal avg = evaluationService.getAvgScore(1L);

        assertThat(avg).isEqualByComparingTo(new BigDecimal("85.00"));
    }

    @Test
    @DisplayName("获取平均评分：无评价返回零")
    void testGetAvgScore_noEvaluations() {
        when(evaluationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BigDecimal avg = evaluationService.getAvgScore(999L);

        assertThat(avg).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("删除评价：正常删除")
    void testDelete() {
        evaluationService.delete(1L);

        verify(evaluationMapper).deleteById(1L);
    }
}
