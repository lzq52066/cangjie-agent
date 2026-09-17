package cn.cangjiecloud.common.domain;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页结果封装 {@link PageResult} 单元测试（使用 MyBatis-Plus 的 Page POJO，不触库）。
 */
class PageResultTest {

    @Test
    void ofShouldCopyRecordsAndPagingMeta() {
        Page<String> page = new Page<>(2, 20);
        page.setRecords(List.of("a", "b", "c"));
        page.setTotal(100);

        PageResult<String> result = PageResult.of(page);

        assertEquals(List.of("a", "b", "c"), result.getList());
        assertEquals(100, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getSize());
    }

    @Test
    void ofShouldSupportEmptyPage() {
        Page<String> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        PageResult<String> result = PageResult.of(page);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getList());
    }
}
