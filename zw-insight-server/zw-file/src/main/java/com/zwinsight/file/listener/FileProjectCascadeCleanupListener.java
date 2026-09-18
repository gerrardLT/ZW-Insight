package com.zwinsight.file.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-file 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理 file_info 中挂在该项目下的附件索引。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p><b>只清索引、不删对象存储</b>：本监听器仅把 file_info 行置为逻辑删除，
 * <b>不会</b>调用 MinIO 删除物理文件。这是有意的取舍——级联路径里做不可逆的
 * 存储删除风险过高（一次误删项目就永久丢失图纸/合同扫描件），且事件消费失败
 * 会让整个删除事务回滚，而 MinIO 删除无法参与数据库事务回滚，两者一致性无法保证。
 * 物理对象成为存储孤儿后由独立的对象存储巡检任务回收。</p>
 *
 * <p><b>不纳入级联</b>：file_storage（存储端配置）、serial_number_rule（编号规则）、
 * biz_export_schedule（导出任务）均无 {@code project_id} 列。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：file_info 存量 57 条，其中 8 条指向已删除项目、
 * 46 条指向物理不存在的项目，是全库 ORPHAN_MISS 第二高的表（仅次于 sys_user_project）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileProjectCascadeCleanupListener {

    private final FileInfoMapper fileInfoMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int files = fileInfoMapper.delete(
                new QueryWrapper<FileInfo>().eq("project_id", projectId));

        log.info("项目删除级联清理[file]完成, projectId={}, 附件索引={}（对象存储物理文件保留，待巡检回收）",
                projectId, files);
    }
}
