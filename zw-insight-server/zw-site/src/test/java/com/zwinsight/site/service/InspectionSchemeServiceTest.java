package com.zwinsight.site.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizInspectionDetail;
import com.zwinsight.site.dto.InspectionDetailDTO;
import com.zwinsight.site.mapper.BizInspectionDetailMapper;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.InspectionSchemeQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * InspectionSchemeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class InspectionSchemeServiceTest {

    @Mock
    private InspectionSchemeQueryMapper schemeQueryMapper;

    @Mock
    private BizInspectionMapper inspectionMapper;

    @Mock
    private BizInspectionDetailMapper inspectionDetailMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InspectionSchemeService inspectionSchemeService;

    private BizInspection mockInspection;
    private InspectionSchemeQueryMapper.SchemeRawDTO mockScheme;

    @BeforeEach
    void setUp() {
        // 构建通用的检查记录
        mockInspection = new BizInspection();
        mockInspection.setId(1L);
        mockInspection.setProjectId(100L);
        mockInspection.setTenantId(10L);
        mockInspection.setInspectionType("QUALITY");

        // 构建通用的检查方案
        mockScheme = new InspectionSchemeQueryMapper.SchemeRawDTO();
        mockScheme.setId(200L);
        mockScheme.setSchemeName("混凝土浇筑检查方案");
        mockScheme.setSchemeType("QUALITY");
        mockScheme.setStatus(1);
        mockScheme.setContent("[{\"itemName\":\"模板检查\",\"checkStandard\":\"模板平整度≤3mm\",\"checkMethod\":\"靠尺测量\"},"
                + "{\"itemName\":\"钢筋绑扎\",\"checkStandard\":\"间距偏差≤10mm\",\"checkMethod\":\"尺量检查\"}]");
    }

    @Test
    @DisplayName("testApplyScheme_fillsDetailsCorrectly — 方案关联后检查明细正确填充")
    void testApplyScheme_fillsDetailsCorrectly() throws JsonProcessingException {
        // given
        Long inspectionId = 1L;
        Long schemeId = 200L;

        when(inspectionMapper.selectById(inspectionId)).thenReturn(mockInspection);
        when(schemeQueryMapper.selectSchemeById(schemeId)).thenReturn(mockScheme);

        // 模拟 ObjectMapper 解析 content JSON
        List<Object> parsedItems = new ArrayList<>();
        // 由于 SchemeContentItem 是私有内部类，我们通过 ObjectMapper mock 返回真实解析结果
        // 这里使用真实的 ObjectMapper 解析来构造预期数据
        ObjectMapper realMapper = new ObjectMapper();
        List<?> realItems = realMapper.readValue(mockScheme.getContent(), new TypeReference<List<Object>>() {});

        // mock objectMapper.readValue 返回内容 — 使用 any() 匹配 TypeReference 参数
        when(objectMapper.readValue(eq(mockScheme.getContent()), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    // 使用真实 ObjectMapper 解析，返回真实的 List
                    ObjectMapper real = new ObjectMapper();
                    return real.readValue(mockScheme.getContent(), invocation.getArgument(1, TypeReference.class));
                });

        // mock objectMapper.writeValueAsString 用于快照序列化
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"schemeId\":200,\"schemeName\":\"混凝土浇筑检查方案\",\"items\":[]}");

        when(inspectionDetailMapper.deleteByInspectionId(inspectionId)).thenReturn(0);
        when(inspectionDetailMapper.insert(any(BizInspectionDetail.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(BizInspection.class))).thenReturn(1);

        // when
        inspectionSchemeService.applyScheme(inspectionId, schemeId);

        // then - 验证插入了正确数量的检查明细（方案有2个检查项）
        ArgumentCaptor<BizInspectionDetail> detailCaptor = ArgumentCaptor.forClass(BizInspectionDetail.class);
        verify(inspectionDetailMapper, times(2)).insert(detailCaptor.capture());

        List<BizInspectionDetail> insertedDetails = detailCaptor.getAllValues();

        // 验证第一条明细
        BizInspectionDetail first = insertedDetails.get(0);
        assertThat(first.getInspectionId()).isEqualTo(inspectionId);
        assertThat(first.getItemName()).isEqualTo("模板检查");
        assertThat(first.getCheckStandard()).isEqualTo("模板平整度≤3mm");
        assertThat(first.getCheckMethod()).isEqualTo("靠尺测量");
        assertThat(first.getCheckResult()).isEqualTo("NOT_CHECKED");
        assertThat(first.getSortOrder()).isEqualTo(1);

        // 验证第二条明细
        BizInspectionDetail second = insertedDetails.get(1);
        assertThat(second.getInspectionId()).isEqualTo(inspectionId);
        assertThat(second.getItemName()).isEqualTo("钢筋绑扎");
        assertThat(second.getCheckStandard()).isEqualTo("间距偏差≤10mm");
        assertThat(second.getCheckMethod()).isEqualTo("尺量检查");
        assertThat(second.getCheckResult()).isEqualTo("NOT_CHECKED");
        assertThat(second.getSortOrder()).isEqualTo(2);

        // 验证更新了检查记录的 scheme_id
        ArgumentCaptor<BizInspection> inspectionCaptor = ArgumentCaptor.forClass(BizInspection.class);
        verify(inspectionMapper).updateById(inspectionCaptor.capture());
        BizInspection updatedInspection = inspectionCaptor.getValue();
        assertThat(updatedInspection.getSchemeId()).isEqualTo(schemeId);
        assertThat(updatedInspection.getSchemeSnapshot()).isNotNull();
    }

    @Test
    @DisplayName("testApplyScheme_clearsOldDetails — 重新选方案清除旧数据")
    void testApplyScheme_clearsOldDetails() throws JsonProcessingException {
        // given - 检查记录已有旧方案
        Long inspectionId = 1L;
        Long newSchemeId = 300L;
        mockInspection.setSchemeId(200L); // 已关联旧方案

        InspectionSchemeQueryMapper.SchemeRawDTO newScheme = new InspectionSchemeQueryMapper.SchemeRawDTO();
        newScheme.setId(300L);
        newScheme.setSchemeName("新安全检查方案");
        newScheme.setSchemeType("SAFETY");
        newScheme.setStatus(1);
        newScheme.setContent("[{\"itemName\":\"高处作业防护\",\"checkStandard\":\"安全网完好\",\"checkMethod\":\"目视检查\"}]");

        when(inspectionMapper.selectById(inspectionId)).thenReturn(mockInspection);
        when(schemeQueryMapper.selectSchemeById(newSchemeId)).thenReturn(newScheme);

        when(objectMapper.readValue(eq(newScheme.getContent()), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    ObjectMapper real = new ObjectMapper();
                    return real.readValue(newScheme.getContent(), invocation.getArgument(1, TypeReference.class));
                });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"schemeId\":300}");

        when(inspectionDetailMapper.deleteByInspectionId(inspectionId)).thenReturn(3); // 清除了3条旧数据
        when(inspectionDetailMapper.insert(any(BizInspectionDetail.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(BizInspection.class))).thenReturn(1);

        // when
        inspectionSchemeService.applyScheme(inspectionId, newSchemeId);

        // then - 验证旧数据被清除
        verify(inspectionDetailMapper).deleteByInspectionId(inspectionId);

        // 验证新方案的检查项被正确插入（新方案只有1个检查项）
        ArgumentCaptor<BizInspectionDetail> detailCaptor = ArgumentCaptor.forClass(BizInspectionDetail.class);
        verify(inspectionDetailMapper, times(1)).insert(detailCaptor.capture());

        BizInspectionDetail insertedDetail = detailCaptor.getValue();
        assertThat(insertedDetail.getItemName()).isEqualTo("高处作业防护");
        assertThat(insertedDetail.getCheckStandard()).isEqualTo("安全网完好");
        assertThat(insertedDetail.getCheckMethod()).isEqualTo("目视检查");

        // 验证 scheme_id 被更新为新方案
        ArgumentCaptor<BizInspection> inspectionCaptor = ArgumentCaptor.forClass(BizInspection.class);
        verify(inspectionMapper).updateById(inspectionCaptor.capture());
        assertThat(inspectionCaptor.getValue().getSchemeId()).isEqualTo(newSchemeId);
    }

    @Test
    @DisplayName("testSaveManualDetails_rejectWhenSchemeLinked — 已关联方案时不允许手动新增")
    void testSaveManualDetails_rejectWhenSchemeLinked() {
        // given - 检查记录已关联方案
        Long inspectionId = 1L;
        mockInspection.setSchemeId(200L); // 已关联方案

        when(inspectionMapper.selectById(inspectionId)).thenReturn(mockInspection);

        List<InspectionDetailDTO> details = new ArrayList<>();
        InspectionDetailDTO dto = new InspectionDetailDTO();
        dto.setItemName("手动检查项");
        dto.setCheckStandard("标准A");
        details.add(dto);

        // when & then - 应抛出业务异常
        assertThatThrownBy(() -> inspectionSchemeService.saveManualDetails(inspectionId, details))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关联方案");

        // 验证没有执行任何删除或插入操作
        verify(inspectionDetailMapper, never()).deleteByInspectionId(any());
        verify(inspectionDetailMapper, never()).insert(any());
    }

    @Test
    @DisplayName("testSaveManualDetails_rejectWhenOver100Items — 超过100条拒绝")
    void testSaveManualDetails_rejectWhenOver100Items() {
        // given - 检查记录未关联方案
        Long inspectionId = 1L;
        mockInspection.setSchemeId(null); // 未关联方案

        when(inspectionMapper.selectById(inspectionId)).thenReturn(mockInspection);

        // 构造101条检查项
        List<InspectionDetailDTO> details = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            InspectionDetailDTO dto = new InspectionDetailDTO();
            dto.setItemName("检查项" + (i + 1));
            dto.setCheckStandard("标准" + (i + 1));
            details.add(dto);
        }

        // when & then - 应抛出业务异常
        assertThatThrownBy(() -> inspectionSchemeService.saveManualDetails(inspectionId, details))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过100条");

        // 验证没有执行任何删除或插入操作
        verify(inspectionDetailMapper, never()).deleteByInspectionId(any());
        verify(inspectionDetailMapper, never()).insert(any());
    }
}
