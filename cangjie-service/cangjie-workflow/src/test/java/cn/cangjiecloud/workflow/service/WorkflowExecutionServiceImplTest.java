package cn.cangjiecloud.workflow.service;

import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.mapper.WorkflowExecutionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link WorkflowExecutionServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：spy 拦截 MyBatis-Plus 的 page/getById/updateById，
 * TableInfoHelper 预注册实体元信息以便 LambdaQueryWrapper 解析列名。
 */
class WorkflowExecutionServiceImplTest {

    private WorkflowExecutionServiceImpl service;

    @BeforeAll
    // 预注册表结构：让 SFunction -> 列名 解析在离线环境可用
    static void installTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(WorkflowExecutionMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, WorkflowExecutionEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new WorkflowExecutionServiceImpl());
    }

    private static WorkflowExecutionEntity execution(String id, String workflowId) {
        WorkflowExecutionEntity e = new WorkflowExecutionEntity();
        e.setId(id);
        e.setWorkflowId(workflowId);
        e.setStatus("running");
        return e;
    }

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：分页查询 —— 按 workflow_id 过滤 + start_time 倒序，页码默认值 1/10
    void pageQueryShouldFilterByWorkflowAndOrderDesc() {
        Page<WorkflowExecutionEntity> fake = new Page<>(1, 10);
        fake.setRecords(List.of(execution("e1", "w1")));
        doReturn(fake).when(service).page(any(Page.class), any(Wrapper.class));

        IPage<WorkflowExecutionEntity> result = service.pageQuery("w1", null, null);

        ArgumentCaptor<IPage<WorkflowExecutionEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<WorkflowExecutionEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());
        assertThat(result).isSameAs(fake);
        assertThat(pageCap.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCap.getValue().getSize()).isEqualTo(10);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).contains("workflow_id");
        assertThat(wrapper.getSqlSegment()).contains("ORDER BY start_time DESC");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("w1");
    }

    @Test
    @SuppressWarnings("unchecked")
    // 覆盖场景：分页查询 —— 显式页码与每页大小透传
    void pageQueryShouldRespectExplicitPaging() {
        Page<WorkflowExecutionEntity> fake = new Page<>(3, 20);
        doReturn(fake).when(service).page(any(Page.class), any(Wrapper.class));

        service.pageQuery("w2", 3, 20);

        ArgumentCaptor<IPage<WorkflowExecutionEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        verify(service).page(pageCap.capture(), any(Wrapper.class));
        assertThat(pageCap.getValue().getCurrent()).isEqualTo(3);
        assertThat(pageCap.getValue().getSize()).isEqualTo(20);
    }

    @Test
    // 覆盖场景：recordStatus —— 执行记录不存在时静默返回，不更新
    void recordStatusShouldSkipWhenExecutionMissing() {
        doReturn(null).when(service).getById("missing");

        service.recordStatus("missing", "completed", "end", null);

        verify(service, never()).updateById(any(WorkflowExecutionEntity.class));
    }

    @Test
    // 覆盖场景：recordStatus —— 全字段更新（status/currentNode/errorMessage 均设置）
    void recordStatusShouldUpdateAllProvidedFields() {
        WorkflowExecutionEntity entity = execution("e1", "w1");
        doReturn(entity).when(service).getById("e1");
        doReturn(true).when(service).updateById(entity);

        service.recordStatus("e1", "failed", "llm-node", "节点挂了");

        ArgumentCaptor<WorkflowExecutionEntity> cap = ArgumentCaptor.forClass(WorkflowExecutionEntity.class);
        verify(service).updateById(cap.capture());
        assertThat(cap.getValue())
                .extracting(WorkflowExecutionEntity::getStatus,
                        WorkflowExecutionEntity::getCurrentNode,
                        WorkflowExecutionEntity::getErrorMessage)
                .containsExactly("failed", "llm-node", "节点挂了");
    }

    @Test
    // 覆盖场景：recordStatus —— currentNode/errorMessage 传 null 时保留原值不覆盖
    void recordStatusShouldKeepOldValuesWhenOptionalArgsNull() {
        WorkflowExecutionEntity entity = execution("e2", "w1");
        entity.setCurrentNode("old-node");
        entity.setErrorMessage("old-error");
        doReturn(entity).when(service).getById("e2");
        doReturn(true).when(service).updateById(entity);

        service.recordStatus("e2", "completed", null, null);

        assertThat(entity.getStatus()).isEqualTo("completed");
        assertThat(entity.getCurrentNode()).isEqualTo("old-node");
        assertThat(entity.getErrorMessage()).isEqualTo("old-error");
    }
}
