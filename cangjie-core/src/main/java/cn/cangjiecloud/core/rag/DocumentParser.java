package cn.cangjiecloud.core.rag;

import java.io.InputStream;

/**
 * 文档解析器接口
 * <p>
 * 支持多种文档格式的解析，将文件内容提取为纯文本。
 * 每种格式（PDF、Word、Markdown、TXT）对应一个实现。
 */
public interface DocumentParser {

    /**
     * 解析文档流为纯文本
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于推断格式）
     * @return 解析后的文本内容
     */
    String parse(InputStream inputStream, String fileName);

    /**
     * 是否支持该文件格式
     *
     * @param fileName 文件名
     * @return true 如果支持
     */
    boolean supports(String fileName);
}
