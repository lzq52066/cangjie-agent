package cn.cangjiecloud.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelProviderEntity} 单元测试：验证 Lombok 生成的访问器/equals/hashCode/toString、
 * 继承自 BaseEntity 的审计字段以及表名注解。
 */
class ModelProviderEntityTest {

    private static ModelProviderEntity sample() {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId("p1");
        entity.setName("OpenAI 官方");
        entity.setCode("openai");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setApiKey("encrypted-ciphertext");
        entity.setStatus("active");
        entity.setDescription("OpenAI 兼容厂商");
        return entity;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        ModelProviderEntity entity = sample();

        assertThat(entity.getId()).isEqualTo("p1");
        assertThat(entity.getName()).isEqualTo("OpenAI 官方");
        assertThat(entity.getCode()).isEqualTo("openai");
        assertThat(entity.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(entity.getApiKey()).isEqualTo("encrypted-ciphertext");
        assertThat(entity.getStatus()).isEqualTo("active");
        assertThat(entity.getDescription()).isEqualTo("OpenAI 兼容厂商");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：新建对象字段默认全部为 null
        ModelProviderEntity entity = new ModelProviderEntity();

        assertThat(entity.getName()).isNull();
        assertThat(entity.getCode()).isNull();
        assertThat(entity.getApiKey()).isNull();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreateTime()).isNull();
    }

    @Test
    void baseEntityFieldsShouldBeWritable() {
        // 正常场景：审计字段来自 BaseEntity
        ModelProviderEntity entity = new ModelProviderEntity();
        LocalDateTime now = LocalDateTime.of(2026, 2, 3, 4, 5, 6);
        entity.setCreateBy("system");
        entity.setUpdateBy("admin");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);

        assertThat(entity.getCreateBy()).isEqualTo("system");
        assertThat(entity.getUpdateBy()).isEqualTo("admin");
        assertThat(entity.getCreateTime()).isEqualTo(now);
        assertThat(entity.getUpdateTime()).isEqualTo(now);
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllFieldsIncludingSuper() {
        // 正常场景：字段一致则相等（callSuper=true）；任一字段不同则不等
        ModelProviderEntity one = sample();
        ModelProviderEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);
        assertThat(one).isNotEqualTo("notAProvider");

        another.setCode("deepseek");
        assertThat(one).isNotEqualTo(another);

        ModelProviderEntity otherId = sample();
        otherId.setId("p2");
        assertThat(one).isNotEqualTo(otherId);
    }

    @Test
    void toStringShouldContainKeyFields() {
        // 正常场景：日志输出包含关键字段
        assertThat(sample().toString())
                .contains("name=OpenAI 官方")
                .contains("code=openai")
                .contains("baseUrl=https://api.openai.com/v1");
    }

    @Test
    void tableNameShouldBeModelProvider() {
        // 正常场景：表名注解映射到 model_provider 表并启用 autoResultMap
        TableName tableName = ModelProviderEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("model_provider");
        assertThat(tableName.autoResultMap()).isTrue();
    }
}
