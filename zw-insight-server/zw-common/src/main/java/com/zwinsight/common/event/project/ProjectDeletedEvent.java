package com.zwinsight.common.event.project;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 项目已删除领域事件（级联清理契约）。
 * <p>
 * <b>为什么放在 zw-common 而不是 zw-project？</b>
 * 项目删除的级联清理消费方横跨 9+ 个模块（合同在 zw-contract、材料在 zw-material、
 * 付款在 zw-finance、劳务在 zw-labor、机械在 zw-machine、分包在 zw-subcontract、
 * 采购在 zw-purchase、预算在 zw-budget、现场在 zw-site）。而这 9 个模块<b>全部已依赖
 * zw-insight-project</b>（用于回写项目金额/状态），若把事件契约定义在 zw-project，
 * 消费方监听时不会引入新依赖，但生产方要调用消费方的清理能力就必须反向依赖，
 * 形成 Maven 循环依赖。把契约放进共享内核 zw-common，生产方与消费方都只依赖
 * zw-common，模块边界保持单向（惯例参照 {@code event/contract/ChangeEventApprovedEvent}
 * 与 {@code LoginSuccessEvent} 的 security → system 反向解耦链路）。
 * </p>
 *
 * <h3>投递语义与消费约束</h3>
 * <ul>
 *   <li>由 {@code ProjectService.delete} 在<b>同一事务内同步发布</b>，
 *       消费方以 {@code @EventListener} 接收，异常会传播并触发整体回滚——
 *       级联清理是数据一致性操作，不允许"项目删了但子表留着"的半成品状态</li>
 *   <li>消费方<b>必须幂等</b>：同一 projectId 重复投递时重复清理不应报错
 *       （DELETE ... WHERE project_id=? 天然幂等）</li>
 *   <li>消费方只清理<b>自己模块拥有的表</b>，不得跨模块删表，避免职责扩散</li>
 *   <li>字段只增不删不改语义；新增字段必须可为 null，保证旧消费者不被破坏</li>
 * </ul>
 *
 * <h3>背景（2026-09-18 数据审计 Round 7 · R7-02）</h3>
 * 线上取证：372 个已删除项目遗留约 2000 条孤儿子表数据（施工合同 49 条、
 * 项目成员 336 条、材料出入库 31 条、预算控制配置 234 条等），根因是
 * {@code ProjectService.delete} 仅删 biz_project 单表、零级联。
 */
@Getter
public class ProjectDeletedEvent extends ApplicationEvent {

    /**
     * 被删除的项目ID（级联清理的唯一键，消费方据此定位自己的表）
     */
    private final Long projectId;

    /**
     * 租户ID（消费方回填租户上下文用；多租户隔离下清理必须限定租户）
     */
    private final Long tenantId;

    /**
     * 项目编号（仅日志与业务追溯用，可为 null）
     */
    private final String projectCode;

    /**
     * 项目名称（仅日志与业务追溯用，可为 null）
     */
    private final String projectName;

    public ProjectDeletedEvent(Object source, Long projectId, Long tenantId,
                               String projectCode, String projectName) {
        super(source);
        this.projectId = projectId;
        this.tenantId = tenantId;
        this.projectCode = projectCode;
        this.projectName = projectName;
    }
}
