package cn.cangjiecloud.trigger.service.impl;

import cn.cangjiecloud.trigger.entity.ChannelMessageEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link ChannelMessageServiceImpl} 单元测试。
 * <p>
 * spy 拦截继承自 {@code ServiceImpl} 的 {@code page} 方法，不连接数据库；
 * 通过捕获 Wrapper 断言查询条件与排序。
 */
@DisplayName("渠道消息服务 - ChannelMessageServiceImpl")
class ChannelMessageServiceImplTest {

    private ChannelMessageServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.trigger.mapper.ChannelMessageMapper");
        TableInfoHelper.initTableInfo(assistant, ChannelMessageEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ChannelMessageServiceImpl());
    }

    @Test
    @DisplayName("channelId 为空时不过滤渠道，分页参数为空回退 1/10")
    void pageQueryShouldSkipBlankChannelAndUseDefaultPaging() {
        IPage<ChannelMessageEntity> stub = new Page<>(1, 10);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        IPage<ChannelMessageEntity> result = service.pageQuery("  ", null, null);

        assertThat(result).isSameAs(stub);
        ArgumentCaptor<IPage<ChannelMessageEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<ChannelMessageEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());

        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(1);
        assertThat(requested.getSize()).isEqualTo(10);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).doesNotContain("channel_id");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").containsIgnoringCase("create_time");
    }

    @Test
    @DisplayName("按渠道 ID 过滤并透传分页参数，按创建时间倒序")
    void pageQueryShouldFilterByChannelIdAndPassPaging() {
        IPage<ChannelMessageEntity> stub = new Page<>(2, 20);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        IPage<ChannelMessageEntity> result = service.pageQuery("c1", 2, 20);

        assertThat(result).isSameAs(stub);
        ArgumentCaptor<IPage<ChannelMessageEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<ChannelMessageEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());

        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(2);
        assertThat(requested.getSize()).isEqualTo(20);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).contains("channel_id");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("c1");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
    }
}
