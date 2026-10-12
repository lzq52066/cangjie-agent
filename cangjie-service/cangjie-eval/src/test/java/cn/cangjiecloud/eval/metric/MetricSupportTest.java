package cn.cangjiecloud.eval.metric;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MetricSupport} 单元测试。
 * <p>
 * 这个类是四个指标的公共底座，也是最容易「静默出错」的地方：解析器多宽容一点，
 * 指标分数就整体抬高；少宽容一点，读数看起来像「模型答得差」。所以每个分支都得钉住，
 * 尤其是「压根没输出」与「输出了一个空数组」这两件事的区分。
 */
class MetricSupportTest {

    @Nested
    class ExtractJson {

        /** 干净的对象输出 */
        @Test
        void shouldParsePlainObject() {
            JsonNode node = MetricSupport.extractJson("{\"claims\": [\"a\"]}");

            assertThat(node).isNotNull();
            assertThat(node.get("claims")).hasSize(1);
        }

        /** ```json 围栏要剥掉：兼容模型最爱这么包 */
        @Test
        void shouldStripCodeFence() {
            JsonNode node = MetricSupport.extractJson("""
                    ```json
                    {"verdicts": [{"index": 1, "supported": true}]}
                    ```
                    """);

            assertThat(node).isNotNull();
            assertThat(node.get("verdicts")).hasSize(1);
        }

        /** 前置的「好的，结果如下：」这类废话不能挡住取数 */
        @Test
        void shouldSkipLeadingProse() {
            JsonNode node = MetricSupport.extractJson("好的，结果如下：{\"claims\": [\"仓颉是语言\"]} 希望有帮助");

            assertThat(node).isNotNull();
            assertThat(node.get("claims").get(0).asText()).isEqualTo("仓颉是语言");
        }

        /** 顶层就是数组的输出也要能吃下（有些模型会省掉外层对象） */
        @Test
        void shouldParseTopLevelArray() {
            JsonNode node = MetricSupport.extractJson("[{\"index\": 1}, {\"index\": 2}]");

            assertThat(node).isNotNull();
            assertThat(node.isArray()).isTrue();
            assertThat(node).hasSize(2);
        }

        /** reason 里带花括号时不能把字符串内的 } 当成闭合点 */
        @Test
        void shouldIgnoreBracesInsideStringLiteral() {
            JsonNode node = MetricSupport.extractJson("{\"reason\": \"格式 {bad} 但完整\", \"claims\": [\"a\"]}");

            assertThat(node).isNotNull();
            assertThat(node.get("reason").asText()).isEqualTo("格式 {bad} 但完整");
            assertThat(node.get("claims")).hasSize(1);
        }

        /** 转义引号后的 } 属于字符串，不能提前收尾 */
        @Test
        void shouldHonourEscapedQuote() {
            JsonNode node = MetricSupport.extractJson("{\"reason\": \"他说\\\"完了 }\\\"\", \"claims\": []}");

            assertThat(node).isNotNull();
            assertThat(node.get("claims")).isEmpty();
        }

        /** 括号没配上是废输出，返回 null 让调用方按「未评测」处理，而不是硬抠一段出来 */
        @Test
        void shouldReturnNullWhenUnbalanced() {
            assertThat(MetricSupport.extractJson("{\"claims\": [\"a\"")).isNull();
        }

        /** 压根没有 JSON 的纯文本输出返回 null */
        @Test
        void shouldReturnNullWhenNoJsonAtAll() {
            assertThat(MetricSupport.extractJson("我无法给出结论")).isNull();
            assertThat(MetricSupport.extractJson("  ")).isNull();
            assertThat(MetricSupport.extractJson(null)).isNull();
        }

        /** 半截围栏（只有开头没有结尾）也要尽力取出里面的 JSON */
        @Test
        void shouldHandleUnterminatedFence() {
            JsonNode node = MetricSupport.extractJson("```json\n{\"claims\": [\"a\"]}");

            assertThat(node).isNotNull();
            assertThat(node.get("claims")).hasSize(1);
        }
    }

    @Nested
    class ArrayAndText {

        /** 字段本身是数组直接返回，是对象则取字段 */
        @Test
        void arrayNodeShouldAcceptBothShapes() {
            JsonNode object = MetricSupport.extractJson("{\"claims\": [\"a\"]}");
            JsonNode array = MetricSupport.extractJson("[\"a\"]");

            assertThat(MetricSupport.arrayNode(object, "claims")).hasSize(1);
            assertThat(MetricSupport.arrayNode(array, "claims")).hasSize(1);
            assertThat(MetricSupport.arrayNode(null, "claims")).isNull();
            // 字段不是数组（模型写成对象了）不能硬当数组用
            assertThat(MetricSupport.arrayNode(
                    MetricSupport.extractJson("{\"claims\": {\"0\": \"a\"}}"), "claims")).isNull();
        }

        /** 文本项、对象项、混进来的数字与空白项：只认真正有文本的那些 */
        @Test
        void toTextListShouldKeepOnlyRealText() {
            JsonNode array = MetricSupport.extractJson(
                    "[\" 断言1 \", {\"text\": \"断言2\"}, {\"claim\": \"断言3\"}, 42, \"  \", {}]");

            assertThat(MetricSupport.toTextList(array)).containsExactly("断言1", "断言2", "断言3");
            assertThat(MetricSupport.toTextList(null)).isEmpty();
        }
    }

    @Nested
    class ParseIds {

        /** 纯字符串数组是主写法 */
        @Test
        void shouldParsePlainIdArray() {
            assertThat(MetricSupport.parseIds("[\"doc1\", \"doc2\", \" doc1 \"]"))
                    .containsExactly("doc1", "doc2");
        }

        /** 早期数据里是对象数组：取 documentId / paragraphId / id，别再拿整段 JSON 去比 ID */
        @Test
        void shouldReadIdFieldFromObjectItems() {
            assertThat(MetricSupport.parseIds("[{\"documentId\": \"doc1\", \"content\": \"x\"}]"))
                    .containsExactly("doc1");
            assertThat(MetricSupport.parseIds("[{\"paragraphId\": \"p1\"}]")).containsExactly("p1");
            assertThat(MetricSupport.parseIds("[{\"id\": \"i1\"}]")).containsExactly("i1");
        }

        /** 数字 ID 也能接住（有些库里主键是自增整型） */
        @Test
        void shouldAcceptNumericIds() {
            assertThat(MetricSupport.parseIds("[1, 2]")).containsExactly("1", "2");
        }

        /** 解不出的一律给空列表：标注写坏不该打断整轮评估，调用方按「没标注」处理 */
        @Test
        void shouldReturnEmptyForUnusableInput() {
            assertThat(MetricSupport.parseIds(null)).isEmpty();
            assertThat(MetricSupport.parseIds("  ")).isEmpty();
            assertThat(MetricSupport.parseIds("doc1,doc2")).isEmpty();
            assertThat(MetricSupport.parseIds("{\"docIds\": [\"doc1\"]}")).isEmpty();
            assertThat(MetricSupport.parseIds("[{}]")).isEmpty();
        }
    }

    @Nested
    class ToVerdict {

        /** 布尔、数字、字符串三形状的判定都得认 */
        @Test
        void shouldReadCommonVerdictShapes() {
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"supported\": true}"), "supported")).isTrue();
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"supported\": 1}"), "supported")).isTrue();
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"supported\": \"refuted\"}"), "supported")).isFalse();
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"relevant\": \"Yes\"}"), "relevant")).isTrue();
            // 顶层直接是布尔值的简化写法（[true, false]）：逐项传进来时也能判
            JsonNode bare = MetricSupport.extractJson("[true, false]");
            assertThat(MetricSupport.toVerdict(bare.get(0), "supported")).isTrue();
            assertThat(MetricSupport.toVerdict(bare.get(1), "supported")).isFalse();
        }

        /** 0.5 这类中间值说明模型没按口径来：宁可不判也别猜 */
        @Test
        void shouldRefuseAmbiguousValues() {
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"supported\": 0.5}"), "supported")).isNull();
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"supported\": \"maybe\"}"), "supported")).isNull();
            assertThat(MetricSupport.toVerdict(
                    MetricSupport.extractJson("{\"other\": true}"), "supported")).isNull();
            assertThat(MetricSupport.toVerdict(null, "supported")).isNull();
        }
    }

    @Nested
    class CosineAndTruncate {

        /** 同向 1.0、正交 0.0、反向也夹到 0.0（口径是「一致度」，不是线性映射） */
        @Test
        void shouldClampIntoUnitRange() {
            assertThat(MetricSupport.cosine(new float[]{1f, 0f}, new float[]{1f, 0f})).isEqualTo(1.0);
            assertThat(MetricSupport.cosine(new float[]{1f, 0f}, new float[]{0f, 1f})).isEqualTo(0.0);
            assertThat(MetricSupport.cosine(new float[]{1f, 0f}, new float[]{-1f, 0f})).isEqualTo(0.0);
            // 长度不一致 / 零向量 / null：都是「算不出来」，给 0 而不是抛异常
            assertThat(MetricSupport.cosine(new float[]{1f, 1f}, new float[]{1f})).isEqualTo(0.0);
            assertThat(MetricSupport.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})).isEqualTo(0.0);
            assertThat(MetricSupport.cosine(null, new float[]{1f})).isEqualTo(0.0);
        }

        /** 超长正文裁进 prompt，正常长度原样保留 */
        @Test
        void shouldTruncateOnlyOverlongText() {
            assertThat(MetricSupport.truncate(null)).isEmpty();
            assertThat(MetricSupport.truncate("短文本")).isEqualTo("短文本");

            String longText = "x".repeat(2500);
            String truncated = MetricSupport.truncate(longText);
            // 裁到 2000 正文 + 省略号：够判断「召回的是哪一段」，又不必把整篇文档烧进 prompt
            assertThat(truncated).hasSize(2003).endsWith("xx...");
        }

        /** 片段编号从 1 开始：判定输出里的 index 要能对上号 */
        @Test
        void shouldNumberFromOne() {
            assertThat(MetricSupport.numbered(List.of("甲", "乙")))
                    .isEqualTo("[1] 甲\n[2] 乙\n");
            assertThat(MetricSupport.numbered(List.of())).isEmpty();
        }
    }
}
