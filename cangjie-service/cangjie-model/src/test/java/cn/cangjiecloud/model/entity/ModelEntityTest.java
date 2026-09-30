package cn.cangjiecloud.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelEntity} 单元测试：验证 Lombok 生成的访问器/equals/hashCode/toString、
 * 继承自 BaseEntity 的审计字段以及表名注解。
 */
class ModelEntityTest {

    private static ModelEntity sample() {
        ModelEntity entity = new ModelEntity();
        entity.setId("m1");
        entity.setName("通用模型");
        entity.setModelType("openai");
        entity.setProviderId("p1");
        entity.setModelName("gpt-4o");
        entity.setTemperature(0.5);
        entity.setMaxTokens(2048);
        entity.setTopP(0.9);
        entity.setIsDefault(true);
        entity.setSupportEmbedding(false);
        entity.setEmbeddingDimension(1536);
        entity.setStatus("active");
        entity.setDescription("测试用模型");
        return entity;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        ModelEntity entity = sample();

        assertThat(entity.getId()).isEqualTo("m1");
        assertThat(entity.getName()).isEqualTo("通用模型");
        assertThat(entity.getModelType()).isEqualTo("openai");
        assertThat(entity.getProviderId()).isEqualTo("p1");
        assertThat(entity.getModelName()).isEqualTo("gpt-4o");
        assertThat(entity.getTemperature()).isEqualTo(0.5);
        assertThat(entity.getMaxTokens()).isEqualTo(2048);
        assertThat(entity.getTopP()).isEqualTo(0.9);
        assertThat(entity.getIsDefault()).isTrue();
        assertThat(entity.getSupportEmbedding()).isFalse();
        assertThat(entity.getEmbeddingDimension()).isEqualTo(1536);
        assertThat(entity.getStatus()).isEqualTo("active");
        assertThat(entity.getDescription()).isEqualTo("测试用模型");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：新建对象字段默认全部为 null
        ModelEntity entity = new ModelEntity();

        assertThat(entity.getName()).isNull();
        assertThat(entity.getModelType()).isNull();
        assertThat(entity.getIsDefault()).isNull();
        assertThat(entity.getTemperature()).isNull();
        assertThat(entity.getId()).isNull();
    }

    @Test
    void baseEntityFieldsShouldBeWritable() {
        // 正常场景：审计字段来自 BaseEntity
        ModelEntity entity = new ModelEntity();
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
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
        ModelEntity one = sample();
        ModelEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);
        assertThat(one).isNotEqualTo("notAModel");

        another.setModelName("gpt-4o-mini");
        assertThat(one).isNotEqualTo(another);

        ModelEntity otherId = sample();
        otherId.setId("m2");
        assertThat(one).isNotEqualTo(otherId);
    }

    @Test
    void toStringShouldContainKeyFields() {
        // 正常场景：日志输出包含关键字段
        assertThat(sample().toString())
                .contains("name=通用模型")
                .contains("modelName=gpt-4o")
                .contains("modelType=openai");
    }

    @Test
    void tableNameShouldBeModelConfig() {
        // 正常场景：表名注解映射到 model_config 表并启用 autoResultMap
        TableName tableName = ModelEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("model_config");
        assertThat(tableName.autoResultMap()).isTrue();
    }
}
