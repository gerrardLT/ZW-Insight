package com.zwinsight.contract.listener;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.contract.mapper.ContractRevisionMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ContractRevisionProjectCascadeCleanupListenerTest {
    @Test void cleansRevisionAndPropagatesFailure() {
        ContractRevisionMapper mapper = mock(ContractRevisionMapper.class);
        ProjectDeletedEvent event = mock(ProjectDeletedEvent.class); when(event.getProjectId()).thenReturn(9999L);
        ContractRevisionProjectCascadeCleanupListener listener = new ContractRevisionProjectCascadeCleanupListener(mapper);
        listener.cleanup(event); verify(mapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        when(mapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenThrow(new IllegalStateException("db"));
        assertThrows(IllegalStateException.class, () -> listener.cleanup(event));
    }
}
