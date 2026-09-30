package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.mapper.AgentApprovalMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AgentApprovalServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code ServiceImpl} 继承的 save/update/getById/getOne/list/page 方法，
 * LambdaQueryWrapper 列名解析依赖 {@link TableInfoHelper} 预初始化，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class AgentApprovalServiceImplTest {

    @Mock
    private AgentApprovalMapper approvalMapper;

    private AgentApprovalServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AgentApprovalMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, AgentApprovalEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new AgentApprovalServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", approvalMapper);
        ReflectionTestUtils.setField(service, "defaultTimeoutSeconds", 1800);
    }

    // ==================== create ====================

    @Nested
    class Create {

        @Test
        void shouldFillTokenExpireTimeAndPersistPendingEntity() {
            AtomicReference<String> idBeforeSave = new AtomicReference<>();
            doAnswer(invocation -> {
                AgentApprovalEntity entity = invocation.getArgument(0);
                idBeforeSave.set(entity.getId());
                entity.setId("approval-100");
                return true;
            }).when(service).save(any(AgentApprovalEntity.class));
            long before = System.currentTimeMillis();

            ApprovalRequest request = ApprovalRequest.builder()
                    .runId("run-1")
                    .stepId("step-1")
                    .sessionId("session-1")
                    .applicationId("app-1")
                    .toolName("deleteFile")
                    .toolType("builtin")
                    .arguments("{\"path\":\"/tmp\"}")
                    .reason("高危操作")
                    .riskLevel("high")
                    .build();

            ApprovalRequest created = service.create(request);

            assertThat(created).isSameAs(request);
            assertThat(created.getApprovalId()).isEqualTo("approval-100");
            assertThat(created.getResumeToken()).isNotBlank().hasSize(32);
            assertThat(created.getExpireAt()).isBetween(before + 1800_000L, System.currentTimeMillis() + 1800_000L);

            ArgumentCaptor<AgentApprovalEntity> captor = ArgumentCaptor.forClass(AgentApprovalEntity.class);
            verify(service).save(captor.capture());
            AgentApprovalEntity saved = captor.getValue();
            assertThat(idBeforeSave.get()).isNull();
            assertThat(saved.getRunId()).isEqualTo("run-1");
            assertThat(saved.getStepId()).isEqualTo("step-1");
            assertThat(saved.getSessionId()).isEqualTo("session-1");
            assertThat(saved.getAppId()).isEqualTo("app-1");
            assertThat(saved.getToolName()).isEqualTo("deleteFile");
            assertThat(saved.getToolType()).isEqualTo("builtin");
            assertThat(saved.getArguments()).isEqualTo("{\"path\":\"/tmp\"}");
            assertThat(saved.getReason()).isEqualTo("高危操作");
            assertThat(saved.getRiskLevel()).isEqualTo("high");
            assertThat(saved.getStatus()).isEqualTo("pending");
            assertThat(saved.getResumeToken()).isEqualTo(created.getResumeToken());
            assertThat(saved.getExpireTime()).isEqualTo(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(created.getExpireAt()), ZoneId.systemDefault()));
        }

        @Test
        void shouldKeepGivenApprovalIdTokenAndExpireAt() {
            long expireAt = System.currentTimeMillis() + 60_000L;
            ApprovalRequest request = ApprovalRequest.builder()
                    .approvalId("approval-existed")
                    .resumeToken("token-existed")
                    .expireAt(expireAt)
                    .build();

            service.create(request);

            ArgumentCaptor<AgentApprovalEntity> captor = ArgumentCaptor.forClass(AgentApprovalEntity.class);
            verify(service).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo("approval-existed");
            assertThat(captor.getValue().getResumeToken()).isEqualTo("token-existed");
            assertThat(captor.getValue().getExpireTime()).isEqualTo(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(expireAt), ZoneId.systemDefault()));
            assertThat(request.getExpireAt()).isEqualTo(expireAt);
        }

        @Test
        void shouldReturnNullWhenSaveFails() {
            doThrow(new RuntimeException("db down")).when(service).save(any(AgentApprovalEntity.class));

            assertThat(service.create(ApprovalRequest.builder().runId("run-1").build())).isNull();
        }
    }

    // ==================== find / findPending ====================

    @Nested
    class Find {

        @Test
        void shouldMapEntityToRequest() {
            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId("approval-1");
            entity.setRunId("run-1");
            entity.setStepId("step-1");
            entity.setSessionId("session-1");
            entity.setAppId("app-1");
            entity.setToolName("exec");
            entity.setToolType("mcp");
            entity.setArguments("{}");
            entity.setReason("reason");
            entity.setRiskLevel("medium");
            entity.setResumeToken("token-1");
            entity.setExpireTime(LocalDateTime.of(2026, 1, 1, 12, 0));
            doReturn(entity).when(service).getById("approval-1");

            ApprovalRequest request = service.find("approval-1");

            assertThat(request).isNotNull();
            assertThat(request.getApprovalId()).isEqualTo("approval-1");
            assertThat(request.getRunId()).isEqualTo("run-1");
            assertThat(request.getStepId()).isEqualTo("step-1");
            assertThat(request.getSessionId()).isEqualTo("session-1");
            assertThat(request.getApplicationId()).isEqualTo("app-1");
            assertThat(request.getToolName()).isEqualTo("exec");
            assertThat(request.getToolType()).isEqualTo("mcp");
            assertThat(request.getArguments()).isEqualTo("{}");
            assertThat(request.getReason()).isEqualTo("reason");
            assertThat(request.getRiskLevel()).isEqualTo("medium");
            assertThat(request.getResumeToken()).isEqualTo("token-1");
            assertThat(request.getExpireAt())
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }

        @Test
        void shouldReturnNullWhenEntityMissingOrExpireTimeNull() {
            assertThat(service.find("approval-none")).isNull();

            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId("approval-2");
            doReturn(entity).when(service).getById("approval-2");

            assertThat(service.find("approval-2")).isNotNull();
            assertThat(service.find("approval-2").getExpireAt()).isZero();
        }

        @Test
        void shouldQueryLatestPendingApprovalByRun() {
            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId("approval-1");
            doReturn(entity).when(service).getOne(any(Wrapper.class));

            assertThat(service.findPendingByRun("run-1").getApprovalId()).isEqualTo("approval-1");

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture());
            LambdaQueryWrapper<AgentApprovalEntity> wrapper = (LambdaQueryWrapper<AgentApprovalEntity>) captor.getValue();
            assertThat(wrapper.getTargetSql()).contains("run_id", "status");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("LIMIT 1");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("run-1", "pending");
        }

        @Test
        void shouldReturnNullForBlankSessionId() {
            assertThat(service.findPendingBySession(" ")).isNull();
            assertThat(service.findPendingBySession(null)).isNull();

            verify(service, never()).getOne(any(Wrapper.class));
        }

        @Test
        void shouldExcludeOverduePendingApprovalInSessionQuery() {
            doReturn(null).when(service).getOne(any(Wrapper.class));

            assertThat(service.findPendingBySession("session-1")).isNull();

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture());
            LambdaQueryWrapper<AgentApprovalEntity> wrapper = (LambdaQueryWrapper<AgentApprovalEntity>) captor.getValue();
            assertThat(wrapper.getTargetSql()).contains("session_id", "status", "expire_time");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("LIMIT 1");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("session-1", "pending")
                    .anyMatch(v -> v instanceof LocalDateTime);
        }
    }

    // ==================== decide ====================

    @Nested
    class Decide {

        @Test
        void shouldRejectUnknownDecisionValue() {
            assertThat(service.decide("approval-1", "unknown", "admin", "remark")).isFalse();

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldNormalizeAliasesToApproved() {
            doReturn(true).when(service).update(any(Wrapper.class));

            assertThat(service.decide("approval-1", " APPROVE ", "admin", "ok")).isTrue();
            assertThat(service.decide("approval-1", "accept", "admin", "ok")).isTrue();
            assertThat(service.decide("approval-1", "TRUE", "admin", "ok")).isTrue();

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service, times(3)).update(captor.capture());
            for (Wrapper<AgentApprovalEntity> wrapper : captor.getAllValues()) {
                assertThat(((LambdaUpdateWrapper<AgentApprovalEntity>) wrapper).getParamNameValuePairs().values())
                        .contains("approved");
            }
        }

        @Test
        void shouldNormalizeAliasesToRejectedAndUseCasOnPending() {
            doReturn(true).when(service).update(any(Wrapper.class));

            assertThat(service.decide("approval-1", "deny", "admin", "no")).isTrue();
            assertThat(service.decide("approval-1", "false", "admin", "no")).isTrue();

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service, times(2)).update(captor.capture());
            LambdaUpdateWrapper<AgentApprovalEntity> wrapper =
                    (LambdaUpdateWrapper<AgentApprovalEntity>) captor.getAllValues().get(0);
            assertThat(wrapper.getSqlSet()).contains("status", "decided_by", "decide_remark", "decide_time");
            assertThat(wrapper.getSqlSegment()).contains("status");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("rejected", "admin", "no", "pending")
                    .anyMatch(v -> v instanceof LocalDateTime);
        }

        @Test
        void shouldReturnFalseWhenCasUpdateAffectsNoRow() {
            doReturn(false).when(service).update(any(Wrapper.class));

            assertThat(service.decide("approval-1", "approve", "admin", "")).isFalse();
        }
    }

    // ==================== expireOverdue ====================

    @Nested
    class ExpireOverdue {

        @Test
        void shouldReturnEmptyWhenNoOverduePending() {
            doReturn(List.of()).when(service).list(any(Wrapper.class));

            assertThat(service.expireOverdue("reject")).isEmpty();

            verify(service, never()).update(any(Wrapper.class));
        }

        @Test
        void shouldMarkOverdueApprovalsExpiredAndCollectRunIds() {
            AgentApprovalEntity first = new AgentApprovalEntity();
            first.setId("approval-1");
            first.setRunId("run-1");
            AgentApprovalEntity second = new AgentApprovalEntity();
            second.setId("approval-2");
            second.setRunId("run-2");
            doReturn(List.of(first, second)).when(service).list(any(Wrapper.class));
            doReturn(true, false).when(service).update(any(Wrapper.class));

            assertThat(service.expireOverdue("approve")).containsExactly("run-1");

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> listCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(listCaptor.capture());
            LambdaQueryWrapper<AgentApprovalEntity> listWrapper =
                    (LambdaQueryWrapper<AgentApprovalEntity>) listCaptor.getValue();
            assertThat(listWrapper.getTargetSql()).contains("status", "expire_time");
            assertThat(listWrapper.getParamNameValuePairs().values()).contains("pending");

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service, times(2)).update(updateCaptor.capture());
            LambdaUpdateWrapper<AgentApprovalEntity> updateWrapper =
                    (LambdaUpdateWrapper<AgentApprovalEntity>) updateCaptor.getAllValues().get(0);
            assertThat(updateWrapper.getSqlSet()).contains("status", "decide_time", "decide_remark");
            assertThat(updateWrapper.getSqlSegment()).contains("id", "status");
            assertThat(updateWrapper.getParamNameValuePairs().values())
                    .contains("approval-1", "expired", "pending")
                    .anyMatch(v -> v instanceof String remark
                            && remark.equals("审批超时未决策，运行已终止（默认决策留痕：approved）"));
        }

        @Test
        void shouldFallbackToRejectedWhenDefaultDecisionUnknown() {
            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId("approval-1");
            entity.setRunId("run-1");
            doReturn(List.of(entity)).when(service).list(any(Wrapper.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            assertThat(service.expireOverdue("whatever")).containsExactly("run-1");

            ArgumentCaptor<Wrapper<AgentApprovalEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            assertThat(((LambdaUpdateWrapper<AgentApprovalEntity>) captor.getValue())
                    .getParamNameValuePairs().values())
                    .anyMatch(v -> v instanceof String remark && remark.contains("rejected"));
        }

        @Test
        void shouldExcludeRunWhoseCasUpdateLost() {
            AgentApprovalEntity entity = new AgentApprovalEntity();
            entity.setId("approval-1");
            entity.setRunId("run-1");
            doReturn(List.of(entity)).when(service).list(any(Wrapper.class));
            doReturn(false).when(service).update(any(Wrapper.class));

            assertThat(service.expireOverdue(null)).isEmpty();
        }
    }

    // ==================== pageQuery ====================

    @Nested
    class PageQuery {

        @Test
        void shouldApplyFiltersAndPaging() {
            doReturn(new Page<>(2, 20)).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<AgentApprovalEntity> page = service.pageQuery("pending", "app-1", "user-1", 2, 20);

            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(20);

            ArgumentCaptor<IPage<AgentApprovalEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<AgentApprovalEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
            LambdaQueryWrapper<AgentApprovalEntity> wrapper =
                    (LambdaQueryWrapper<AgentApprovalEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("status", "app_id", "user_id");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("pending", "app-1", "user-1");
        }

        @Test
        void shouldUseDefaultsAndSkipBlankFilters() {
            doReturn(new Page<>(1, 10)).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(null, " ", "", null, null);

            ArgumentCaptor<IPage<AgentApprovalEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<AgentApprovalEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
            assertThat(((LambdaQueryWrapper<AgentApprovalEntity>) wrapperCaptor.getValue()).getTargetSql())
                    .doesNotContain("status", "app_id", "user_id");
        }
    }
}
