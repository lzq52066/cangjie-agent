package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.eval.entity.EvalResultEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link EvalResultServiceImpl} 单元测试。
 * <p>
 * 盯两件事：明细写失败必须「回传」而不是吞掉（吞掉就会做出「指标齐全、明细 0 行」的静默不一致报告），
 * 以及列表接口确实没把正文三列带上（正文跟着轮询重传就是每次 MB 级响应）。
 */
@ExtendWith(MockitoExtension.class)
class EvalResultServiceImplTest {

    private EvalResultServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 裁列要按实体字段解析列名，需要 TableInfo 缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.eval.mapper.EvalResultMapper");
        TableInfoHelper.initTableInfo(assistant, EvalResultEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new EvalResultServiceImpl());
    }

    private static EvalResultEntity row(String runId) {
        EvalResultEntity result = new EvalResultEntity();
        result.setRunId(runId);
        result.setCaseId("c1");
        result.setQuestion("什么是仓颉？");
        result.setAnswer("一段很长的回答正文");
        return result;
    }

    @Test
    void recordShouldReturnTrueWhenInsertSucceeds() {
        doReturn(true).when(service).save(any(EvalResultEntity.class));

        assertThat(service.record(row("r1"))).isTrue();
    }

    /** 落库失败只记日志并把「没写成」回传，绝不让一条明细把整个运行带崩 */
    @Test
    void recordShouldReportFailureWithoutThrowing() {
        doThrow(new RuntimeException("value too long for column detail")).when(service).save(any(EvalResultEntity.class));

        assertThat(service.record(row("r1"))).isFalse();
    }

    @Test
    void recordShouldRejectNullRow() {
        assertThat(service.record(null)).isFalse();
        verify(service, never()).save(any(EvalResultEntity.class));
    }

    /** 精简列：正文三列由展开行按 id 单条拉取，列表一律不选 */
    @Test
    @SuppressWarnings("unchecked")
    void listByRunShouldNotSelectHeavyColumns() {
        doReturn(List.of()).when(service).list(any(Wrapper.class));

        service.listByRun("r1");

        ArgumentCaptor<Wrapper<EvalResultEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).list(captor.capture());
        String selected = ((LambdaQueryWrapper<EvalResultEntity>) captor.getValue()).getSqlSelect();
        assertThat(selected)
                .contains("question").contains("recall").contains("correctness").contains("trace_id")
                .doesNotContain("answer").doesNotContain("contexts").doesNotContain("detail");
    }

    @Test
    void listByRunShouldReturnEmptyWithoutRunId() {
        assertThat(service.listByRun(null)).isEmpty();
        assertThat(service.listByRun("  ")).isEmpty();
        verify(service, never()).list(any(Wrapper.class));
    }

    /** 单条正文要拿得到全列，否则展开行永远是空的 */
    @Test
    void detailShouldFetchWholeRowById() {
        EvalResultEntity stored = row("r1");
        doReturn(stored).when(service).getById("res1");

        assertThat(service.detail("res1")).isSameAs(stored);
    }

    @Test
    void detailShouldReturnNullWithoutId() {
        assertThat(service.detail("")).isNull();
        verify(service, never()).getById(any());
    }
}
