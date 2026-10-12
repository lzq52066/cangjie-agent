package cn.cangjiecloud.observability.mapper;

/**
 * llm_trace 正文关键词检索所需的 trgm 索引维护。
 * <p>
 * 只有 DDL、没有实体，因此不挂 {@code BaseMapper}，也不进 {@code @MapperScan} 的实体约定。
 */
public interface LlmTraceIndexMapper {

    /** pg_trgm 扩展：部分精简镜像没装，装不上由调用方兜住并跳过索引 */
    void ensureTrgmExtension();

    /**
     * prompt 正文的 trgm 索引。
     * <p>
     * 必须是 CONCURRENTLY：普通 CREATE INDEX 持 SHARE 锁，会在建索引期间阻塞 llm_trace 的全部
     * 写入，而这是全库最膨胀的一张表。Flyway 迁移跑在事务里，事务内不允许 CONCURRENTLY，
     * 所以这段从迁移脚本里挪出来，由 {@code LlmTraceIndexBootstrap} 在启动后的后台线程执行。
     */
    void createPromptTrgmIndex();

    /** response 正文的 trgm 索引，同 {@link #createPromptTrgmIndex()} */
    void createResponseTrgmIndex();
}
