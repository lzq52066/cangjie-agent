package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 文档解析器（基于 Apache POI）
 * <p>
 * 支持 .xls（HSSF）和 .xlsx（XSSF）格式。
 * 每个 Sheet 作为一个段落块，单元格之间用制表符分隔，行间用换行分隔。
 */
@Slf4j
@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<String> parts = new ArrayList<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                List<String> rows = new ArrayList<>();

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = getCellValue(cell);
                        if (value != null && !value.isBlank()) {
                            cells.add(value.trim());
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.add(String.join("\t", cells));
                    }
                }

                if (!rows.isEmpty()) {
                    if (workbook.getNumberOfSheets() > 1) {
                        parts.add("[" + sheetName + "]");
                    }
                    parts.addAll(rows);
                }
            }

            String result = String.join("\n", parts);
            log.debug("Excel 文档解析: {} → {} 个 Sheet, {} 字符", fileName, workbook.getNumberOfSheets(), result.length());
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Excel 文档解析失败: " + fileName, e);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double num = cell.getNumericCellValue();
                        yield String.valueOf(num);
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> null;
        };
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }
}
