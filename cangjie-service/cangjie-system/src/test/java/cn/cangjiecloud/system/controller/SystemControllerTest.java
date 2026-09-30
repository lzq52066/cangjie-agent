package cn.cangjiecloud.system.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.system.entity.SystemSettingEntity;
import cn.cangjiecloud.system.mapper.SystemSettingMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SystemController} 单元测试：mock SystemSettingMapper，
 * 覆盖系统信息、设置读取（存在/缺失）与保存（新增/更新）分支。
 */
@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    private SystemController controller;

    @BeforeEach
    void setUp() {
        controller = new SystemController(systemSettingMapper);
    }

    @Nested
    class InfoTest {

        @Test
        void infoShouldExposeStaticSystemInfoWithTimestamp() {
            R<Map<String, Object>> response = controller.info();

            assertThat(response.isSuccess()).isTrue();
            Map<String, Object> info = response.getData();
            assertThat(info).containsEntry("name", "CangJie Agent")
                    .containsEntry("version", "1.0.0")
                    .containsEntry("domain", "agent.cangjiecloud.cn");
            assertThat((Long) info.get("time")).isPositive();
        }
    }

    @Nested
    class GetSettingTest {

        @Test
        void getSettingShouldReturnEmptyObjectWhenRecordMissing() {
            when(systemSettingMapper.selectById(1)).thenReturn(null);

            R<ObjectNode> response = controller.getSetting(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();
            assertThat(response.getData().isEmpty()).isTrue();
        }

        @Test
        void getSettingShouldReturnStoredMeta() {
            ObjectNode meta = JsonUtils.newObject().put("theme", "dark");
            SystemSettingEntity entity = new SystemSettingEntity();
            entity.setType(1);
            entity.setMeta(meta);
            when(systemSettingMapper.selectById(1)).thenReturn(entity);

            R<ObjectNode> response = controller.getSetting(1);

            assertThat(response.getData()).isSameAs(meta);
            assertThat(response.getData().get("theme").asText()).isEqualTo("dark");
        }
    }

    @Nested
    class SaveSettingTest {

        @Test
        void saveSettingShouldInsertWhenRecordAbsent() {
            when(systemSettingMapper.selectById(2)).thenReturn(null);
            ObjectNode meta = JsonUtils.newObject().put("enable", true);

            R<Void> response = controller.saveSetting(2, meta);

            assertThat(response.isSuccess()).isTrue();
            ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
            verify(systemSettingMapper).insert(captor.capture());
            verify(systemSettingMapper, never()).updateById(captor.capture());

            SystemSettingEntity saved = captor.getAllValues().get(0);
            assertThat(saved.getType()).isEqualTo(2);
            assertThat(saved.getMeta()).isSameAs(meta);
            assertThat(saved.getCreateTime()).isNotNull();
            assertThat(saved.getCreateTime()).isEqualTo(saved.getUpdateTime());
        }

        @Test
        void saveSettingShouldUpdateAndPreserveCreateTimeWhenRecordExists() {
            LocalDateTime created = LocalDateTime.now().minusDays(3);
            SystemSettingEntity exists = new SystemSettingEntity();
            exists.setType(2);
            exists.setMeta(JsonUtils.newObject().put("enable", false));
            exists.setCreateTime(created);
            when(systemSettingMapper.selectById(2)).thenReturn(exists);
            ObjectNode meta = JsonUtils.newObject().put("enable", true);

            controller.saveSetting(2, meta);

            ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
            verify(systemSettingMapper).updateById(captor.capture());
            verify(systemSettingMapper, never()).insert(captor.capture());

            SystemSettingEntity saved = captor.getAllValues().get(0);
            assertThat(saved.getType()).isEqualTo(2);
            assertThat(saved.getMeta()).isSameAs(meta);
            assertThat(saved.getCreateTime()).isEqualTo(created);
            assertThat(saved.getUpdateTime()).isAfterOrEqualTo(created);
        }
    }
}
