package cn.cangjiecloud.knowledge.rag;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExcelDocumentParser} 单元测试。
 * <p>
 * 使用内存构造的真实 xlsx 工作簿验证：制表符拼接、空行空单元格跳过、
 * 多 Sheet 名称前缀、日期/整数格式化、公式回退与坏文件异常包装。
 */
class ExcelDocumentParserTest {

    private final ExcelDocumentParser parser = new ExcelDocumentParser();

    private static byte[] workbookBytes(Consumer<Workbook> builder) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.accept(workbook);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String parse(byte[] bytes, String fileName) {
        return new ExcelDocumentParser().parse(new ByteArrayInputStream(bytes), fileName);
    }

    @Test
    void 应解析单元格并用制表符拼接行() throws IOException {
        byte[] bytes = workbookBytes(workbook -> {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("年龄");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("张三");
            data.createCell(1).setCellValue(25);
        });

        assertThat(parse(bytes, "员工.xlsx")).isEqualTo("姓名\t年龄\n张三\t25");
    }

    @Test
    void 应跳过空行与空白单元格() throws IOException {
        byte[] bytes = workbookBytes(workbook -> {
            Sheet sheet = workbook.createSheet("S");
            Row first = sheet.createRow(0);
            first.createCell(0).setCellValue("A");
            first.createCell(1).setCellValue("  ");
            first.createCell(2).setCellValue("B");
            sheet.createRow(1);
            Row third = sheet.createRow(2);
            third.createCell(0).setCellValue("C");
        });

        assertThat(parse(bytes, "空行.xlsx")).isEqualTo("A\tB\nC");
    }

    @Test
    void 多Sheet应添加名称前缀() throws IOException {
        byte[] bytes = workbookBytes(workbook -> {
            workbook.createSheet("第一页").createRow(0).createCell(0).setCellValue("A");
            workbook.createSheet("第二页").createRow(0).createCell(0).setCellValue("B");
        });

        assertThat(parse(bytes, "多页.xlsx")).isEqualTo("[第一页]\nA\n[第二页]\nB");
    }

    @Test
    void 日期格式单元格应输出ISO日期() throws IOException {
        byte[] bytes = workbookBytes(workbook -> {
            Sheet sheet = workbook.createSheet("S");
            Cell cell = sheet.createRow(0).createCell(0);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(dateStyle);
            cell.setCellValue(LocalDate.of(2024, 1, 15));
        });

        assertThat(parse(bytes, "日期.xlsx")).isEqualTo("2024-01-15");
    }

    @Test
    void 公式单元格应回退读取缓存数值() throws IOException {
        byte[] bytes = workbookBytes(workbook -> {
            Sheet sheet = workbook.createSheet("S");
            Cell cell = sheet.createRow(0).createCell(0);
            cell.setCellFormula("2+3");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
        });

        assertThat(parse(bytes, "公式.xlsx")).isEqualTo("5.0");
    }

    @Test
    void 非法文件应包装为RuntimeException() {
        byte[] bytes = "这不是一个 Excel 文件".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parse(bytes, "bad.xlsx"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Excel 文档解析失败: bad.xlsx");
    }

    @Test
    void supports应识别xls与xlsx() {
        assertThat(parser.supports("data.xls")).isTrue();
        assertThat(parser.supports("DATA.XLSX")).isTrue();
        assertThat(parser.supports("data.csv")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }
}
