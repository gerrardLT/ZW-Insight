package com.zwinsight.contract.listener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.contract.domain.ContractRevision;
import com.zwinsight.contract.mapper.ContractRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ContractRevisionProjectCascadeCleanupListener {
    private final ContractRevisionMapper mapper;
    @EventListener public void cleanup(ProjectDeletedEvent e) {
        mapper.delete(new LambdaQueryWrapper<ContractRevision>().eq(ContractRevision::getProjectId, e.getProjectId()));
    }
}
