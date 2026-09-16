package com.zwinsight.site.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.site.dto.EvidenceSubmitDTO;
import com.zwinsight.site.dto.EvidenceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SiteEvidenceControllerTest {

    private SiteEvidenceController controller;

    @BeforeEach
    void setUp() {
        controller = new SiteEvidenceController();
        SecurityContextHolder.setUserId(8888L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void submitEvidence_returnsVerifiedStatusAndFields() {
        EvidenceSubmitDTO req = new EvidenceSubmitDTO();
        req.setBusinessType("WATERMARK");
        req.setBusinessId(12345L);
        req.setProjectId(90001L);
        req.setFileUrl("https://oss.example.com/site/wm123.jpg");
        req.setClientSha256("A1B2C3D4E5F6");
        req.setCapturedAtDevice(1757800000000L);
        req.setLongitude(new BigDecimal("120.12345"));
        req.setLatitude(new BigDecimal("30.54321"));

        R<EvidenceVO> res = controller.submitEvidence(req);

        assertEquals(200, res.getCode());
        assertNotNull(res.getData());
        EvidenceVO vo = res.getData();
        assertEquals("VERIFIED", vo.getVerificationStatus());
        assertEquals("WATERMARK", vo.getBusinessType());
        assertEquals(12345L, vo.getBusinessId());
        assertEquals("A1B2C3D4E5F6", vo.getVerifiedSha256());
        assertEquals(8888L, vo.getOperatorUserId());
        assertNotNull(vo.getReceivedAtServer());
    }

    @Test
    void getVerification_returnsValidAck() {
        R<EvidenceVO> res = controller.getVerification("INSPECTION", 67890L);

        assertEquals(200, res.getCode());
        EvidenceVO vo = res.getData();
        assertEquals("VERIFIED", vo.getVerificationStatus());
        assertEquals("INSPECTION", vo.getBusinessType());
        assertEquals(67890L, vo.getBusinessId());
        assertEquals(8888L, vo.getOperatorUserId());
    }
}
