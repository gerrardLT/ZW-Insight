package com.zwinsight.contract.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizExpenseContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizQuantityList;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.mapper.BizQuantityListMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-contract 模块，R7-02）。
 * <p>
 * 监听 {@link ProjectDeletedEvent}，清理本模块拥有的、含 {@code project_id} 列的全部表。
 * 契约定义在 zw-common 而非 zw-project，因为本模块已依赖 zw-insight-project
 * （施工合同审批通过时要回写项目金额/状态），反向注入会形成 Maven 循环依赖。
 * </p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>各实体均继承 {@code BaseEntity}（带 {@code @TableLogic deleted}），
 *       故 {@code mapper.delete(wrapper)} 由 MyBatis-Plus 自动转成
 *       {@code UPDATE ... SET deleted=1 WHERE project_id=? AND deleted=0}，
 *       与 biz_project 主表保持同一逻辑删除语义</li>
 *   <li>用字符串列名 {@code QueryWrapper} 而非 {@code LambdaQueryWrapper}：
 *       级联覆盖本模块 8 张表，字符串列名只依赖「表有 project_id 列」
 *       （已由 information_schema 全量核实），不依赖逐个实体的 getter 签名</li>
 *   <li><b>不吞异常</b>：清理失败必须传播，让 ProjectService 的整体事务回滚，
 *       不允许「项目删了但合同留着」的半成品状态</li>
 *   <li>天然幂等：重复投递同一 projectId 时第二次影响行数为 0，不报错</li>
 *   <li><b>类名带模块前缀</b>：11 个模块各有一个本监听器，而组件扫描用的是
 *       {@code ZwInsightApplication} 上的 {@code scanBasePackages="com.zwinsight"} +
 *       默认 {@code AnnotationBeanNameGenerator}（按短类名生成 bean 名）；
 *       {@code @MapperScan} 里的 {@code FullyQualifiedAnnotationBeanNameGenerator}
 *       <b>只管 Mapper、管不到组件扫描</b>。故若 11 个类同名，启动时会直接抛
 *       {@code ConflictingBeanDefinitionException}。本仓库对偶发重名（如两个
 *       {@code TemplateService}）的做法是加显式 bean 名；但本监听器是「同一职责
 *       在每模块各一份」的家族，模块名就是其语义区别项，因此直接把模块名编进
 *       类名（{@code Contract/Material/.../FileProjectCascadeCleanupListener}），
 *       既避开冲突，也避免 11 个同名文件在 IDE 与异常栈里无法区分</li>
 * </ul>
 *
 * <p>线上取证（2026-09-18 数据审计 Round 7）：本模块 biz_construction_contract
 * 遗留 49 条指向已删除项目 + 18 条指向物理不存在项目的孤儿，为全库单表最高。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractProjectCascadeCleanupListener {

    private final BizConstructionContractMapper constructionContractMapper;
    private final BizOutputReportMapper outputReportMapper;
    private final BizQuantityListMapper quantityListMapper;
    private final BizChangeVisaMapper changeVisaMapper;
    private final BizChangeEventMapper changeEventMapper;
    private final BizExpenseContractMapper expenseContractMapper;
    private final BizOtherContractMapper otherContractMapper;
    private final BizFinalSettlementMapper finalSettlementMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int contracts = constructionContractMapper.delete(
                new QueryWrapper<BizConstructionContract>().eq("project_id", projectId));
        int outputReports = outputReportMapper.delete(
                new QueryWrapper<BizOutputReport>().eq("project_id", projectId));
        int quantityLists = quantityListMapper.delete(
                new QueryWrapper<BizQuantityList>().eq("project_id", projectId));
        int changeVisas = changeVisaMapper.delete(
                new QueryWrapper<BizChangeVisa>().eq("project_id", projectId));
        int changeEvents = changeEventMapper.delete(
                new QueryWrapper<BizChangeEvent>().eq("project_id", projectId));
        int expenseContracts = expenseContractMapper.delete(
                new QueryWrapper<BizExpenseContract>().eq("project_id", projectId));
        int otherContracts = otherContractMapper.delete(
                new QueryWrapper<BizOtherContract>().eq("project_id", projectId));
        int finalSettlements = finalSettlementMapper.delete(
                new QueryWrapper<BizFinalSettlement>().eq("project_id", projectId));

        log.info("项目删除级联清理[contract]完成, projectId={}, 施工合同={} 产值={} 工程量={} "
                        + "签证={} 变更事件={} 支出合同={} 其他合同={} 最终结算={}",
                projectId, contracts, outputReports, quantityLists, changeVisas,
                changeEvents, expenseContracts, otherContracts, finalSettlements);
    }
}
