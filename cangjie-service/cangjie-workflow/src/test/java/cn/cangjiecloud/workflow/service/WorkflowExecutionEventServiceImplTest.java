package cn.cangjiecloud.workflow.service;

import cn.cangjiecloud.workflow.entity.WorkflowExecutionEventEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionEventMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link WorkflowExecutionEventServiceImpl} 单元测试（不连库，spy 拦截 list）。
 */
class WorkflowExecutionEventServiceImplTest {

    private WorkflowExecutionEventServiceImpl service;

    @BeforeAll
    // 预注册表结构：让 SFunction -> 列名 解析在离线环境可用
    static void installTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(WorkflowExecutionEventMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, WorkflowExecutionEventEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new WorkflowExecutionEventServiceImpl());
    }

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：按执行记录查询节点事件 —— eq execution_id + create_time 升序
    void listByExecutionShouldFilterAndOrderAscending() {
        WorkflowExecutionEventEntity event = new WorkflowExecutionEventEntity();
        event.setExecutionId("exec-1");
        event.setNodeId("n1");
        doReturn(List.of(event)).when(service).list(any(Wrapper.class));

        List<WorkflowExecutionEventEntity> result = service.listByExecution("exec-1");

        assertThat(result).containsExactly(event);
        ArgumentCaptor<Wrapper<WorkflowExecutionEventEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).list(cap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) cap.getValue();
        assertThat(wrapper.getTargetSql()).contains("execution_id");
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY create_time ASC");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("exec-1");
    }

    @Test
    // 覆盖场景：无事件记录 —— 返回空列表
    void listByExecutionShouldReturnEmptyWhenNoEvents() {
        doReturn(List.of()).when(service).list(any(Wrapper.class));

        assertThat(service.listByExecution("exec-none")).isEmpty();
    }
}
