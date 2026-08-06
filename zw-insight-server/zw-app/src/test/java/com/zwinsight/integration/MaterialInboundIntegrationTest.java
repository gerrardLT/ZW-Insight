package com.zwinsight.integration;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialInboundDetail;
import com.zwinsight.material.service.MaterialInboundService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 材料入库集成测试（阶段一收尾 1.6.3）
 * <p>
 * 在 Testcontainers 真实 MySQL/Redis 上验证材料入库真实业务链路：
 * <ul>
 *   <li>save：创建入库单（DRAFT）+ 明细金额自动计算 + 总金额汇总回写</li>
 *   <li>submit：置 APPROVED + 项目库存新增 + 采购合同累计入库金额回写</li>
 *   <li>状态机保护：非草稿不可重复提交</li>
 * </ul>
 * </p>
 * <p>租户隔离：全部数据 tenant_id=9999（自动化测试租户），@BeforeEach 物理清理残留。</p>
 */
@DisplayName("材料入库集成测试")
class MaterialInboundIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MaterialInboundService inboundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long PROJECT_ID = 99990002L;
    private static final Long CONTRACT_ID = 99990201L;

    @BeforeEach
    void setup() {
        // 清除残留（容器跨测试类复用，保持类间隔离）
        jdbcTemplate.update("DELETE FROM biz_material_inbound_detail WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM biz_material_inbound WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM biz_project_material_stock WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM biz_purchase_contract WHERE tenant_id = ?", TEST_TENANT_ID);

        // 夹具：采购合同（入库提交后回写累计入库金额；created_by 满足数据权限 SELF 过滤，否则 Mapper selectById 查不到）
        jdbcTemplate.update(
                "INSERT INTO biz_purchase_contract (id, project_id, contract_code, contract_name, " +
                        "contract_amount, cumulative_inbound, cumulative_settlement, cumulative_paid, " +
                        "cumulative_invoice_received, status, tenant_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                CONTRACT_ID, PROJECT_ID, "CG-IT-INB-001", "集成测试入库关联采购合同",
                new BigDecimal("500000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "EFFECTIVE", TEST_TENANT_ID, TEST_USER_ID);
    }

    @Test
    @DisplayName("创建入库单 - 草稿且总金额按明细自动汇总")
    void testSave_draftWithAggregatedAmount() {
        BizMaterialInbound inbound = newInbound();
        List<BizMaterialInboundDetail> details = newDetails();

        inboundService.save(inbound, details);

        assertThat(inbound.getId()).isNotNull();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM biz_material_inbound WHERE id = ?", String.class, inbound.getId());
        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM biz_material_inbound WHERE id = ?", BigDecimal.class, inbound.getId());
        Integer detailCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM biz_material_inbound_detail WHERE inbound_id = ?",
                Integer.class, inbound.getId());

        assertThat(status).isEqualTo("DRAFT");
        // 钢筋 10×4000=40000 + 水泥 5×300=1500 = 41500
        assertThat(total).isEqualByComparingTo(new BigDecimal("41500.00"));
        assertThat(detailCount).isEqualTo(2);
    }

    @Test
    @DisplayName("提交入库单 - 审批通过并回写库存与合同累计入库")
    void testSubmit_approvesAndWritesBackStockAndContract() {
        BizMaterialInbound inbound = newInbound();
        inboundService.save(inbound, newDetails());

        inboundService.submit(inbound.getId());

        // 状态置 APPROVED
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM biz_material_inbound WHERE id = ?", String.class, inbound.getId());
        assertThat(status).isEqualTo("APPROVED");

        // 库存新增：钢筋 10 吨入库
        BigDecimal stockQty = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM biz_project_material_stock " +
                        "WHERE project_id = ? AND material_name = ? AND tenant_id = ?",
                BigDecimal.class, PROJECT_ID, "钢筋", TEST_TENANT_ID);
        assertThat(stockQty).isEqualByComparingTo(new BigDecimal("10.000"));

        // 采购合同累计入库金额回写 41500
        BigDecimal cumulative = jdbcTemplate.queryForObject(
                "SELECT cumulative_inbound FROM biz_purchase_contract WHERE id = ?",
                BigDecimal.class, CONTRACT_ID);
        assertThat(cumulative).isEqualByComparingTo(new BigDecimal("41500.00"));
    }

    @Test
    @DisplayName("重复提交入库单 - 非草稿拒绝")
    void testSubmitTwice_rejected() {
        BizMaterialInbound inbound = newInbound();
        inboundService.save(inbound, newDetails());
        inboundService.submit(inbound.getId());

        assertThatThrownBy(() -> inboundService.submit(inbound.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    private BizMaterialInbound newInbound() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setProjectId(PROJECT_ID);
        inbound.setContractId(CONTRACT_ID);
        inbound.setInboundDate(LocalDate.of(2026, 8, 1));
        inbound.setDirectOutbound(0);
        return inbound;
    }

    private List<BizMaterialInboundDetail> newDetails() {
        List<BizMaterialInboundDetail> details = new ArrayList<>();
        details.add(detail("钢筋", "HRB400", "吨", "4000.00", "10"));
        details.add(detail("水泥", "P.O42.5", "袋", "300.00", "5"));
        return details;
    }

    private BizMaterialInboundDetail detail(String name, String spec, String unit,
                                            String unitPrice, String quantity) {
        BizMaterialInboundDetail d = new BizMaterialInboundDetail();
        d.setMaterialName(name);
        d.setSpecification(spec);
        d.setUnit(unit);
        d.setUnitPrice(new BigDecimal(unitPrice));
        d.setQuantity(new BigDecimal(quantity));
        return d;
    }
}
