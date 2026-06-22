package com.ld.poetry.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档解析服务
 * <p>
 * 支持：
 * - 纯文本类（txt/md/markdown/csv/json）：直接 UTF-8 读取
 * - PDF：使用 Apache PDFBox 提取文本
 * - Office 类（doc/docx/wps/ppt/pptx/xls/xlsx）：使用 Apache POI 提取文本
 * <p>
 * 说明：扫描版 PDF、含公式的学术论文、复杂跨页表格等场景解析效果有限，
 * 如需更强能力可考虑外接 OCR（Tesseract）或专用解析服务。
 */
@Service
public class DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParser.class);

    /**
     * 单文档最大提取字符数（避免 token 爆炸）
     */
    private static final int MAX_CONTENT_CHARS = 50000;

    /**
     * 解析文档，返回纯文本内容
     *
     * @param file 上传的文档文件
     * @return 提取的文本内容
     */
    public ParseResult parse(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文档为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            filename = "";
        }
        String lowerName = filename.toLowerCase();
        String contentType = file.getContentType();
        byte[] data = file.getBytes();

        String text;
        if (isPlainText(lowerName, contentType)) {
            text = parsePlainText(data);
        } else if (lowerName.endsWith(".pdf")) {
            text = parsePdf(data);
        } else if (lowerName.endsWith(".docx")) {
            text = parseDocx(data);
        } else if (lowerName.endsWith(".doc") || lowerName.endsWith(".wps")) {
            text = parseDoc(data);
        } else if (lowerName.endsWith(".pptx")) {
            text = parsePptx(data);
        } else if (lowerName.endsWith(".ppt")) {
            text = parsePpt(data);
        } else if (lowerName.endsWith(".xlsx")) {
            text = parseXlsx(data);
        } else if (lowerName.endsWith(".xls")) {
            text = parseXls(data);
        } else {
            // 兜底：尝试按纯文本读取
            text = parsePlainText(data);
        }

        if (text == null) {
            text = "";
        }
        text = text.trim();
        if (text.length() > MAX_CONTENT_CHARS) {
            text = text.substring(0, MAX_CONTENT_CHARS) + "\n\n[文档内容已截断，共提取 " + text.length() + " 字符]";
        }
        return new ParseResult(text, contentType != null ? contentType : "text/plain");
    }

    private boolean isPlainText(String lowerName, String contentType) {
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown") || lowerName.endsWith(".csv")
                || lowerName.endsWith(".json")) {
            return true;
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.startsWith("text/") || ct.equals("application/json")
                    || ct.equals("application/csv")) {
                return true;
            }
        }
        return false;
    }

    private String parsePlainText(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * PDF 解析（PDFBox）
     */
    private String parsePdf(byte[] data) throws Exception {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * DOCX 解析（含表格）
     */
    private String parseDocx(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowSb = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null) {
                            cellText = cellText.trim();
                        }
                        rowSb.append(cellText != null ? cellText : "").append("\t");
                    }
                    if (rowSb.length() > 0) {
                        sb.append(rowSb).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * DOC/WPS 解析（旧版 Word 格式）
     */
    private String parseDoc(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            // WPS 文件可能不是真正的 OLE2 格式，尝试按纯文本读取
            logger.warn("DOC/WPS 解析失败，尝试按纯文本读取: {}", e.getMessage());
            return parsePlainText(data);
        }
    }

    /**
     * PPTX 解析
     */
    private String parsePptx(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             XMLSlideShow slideShow = new XMLSlideShow(is)) {
            StringBuilder sb = new StringBuilder();
            int slideIndex = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                sb.append("[第 ").append(slideIndex).append(" 页]\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
                slideIndex++;
            }
            return sb.toString();
        }
    }

    /**
     * PPT 解析（旧版 PowerPoint 格式）
     */
    private String parsePpt(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             HSLFSlideShow slideShow = new HSLFSlideShow(is)) {
            StringBuilder sb = new StringBuilder();
            int slideIndex = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                sb.append("[第 ").append(slideIndex).append(" 页]\n");
                List<List<HSLFTextParagraph>> paragraphs = slide.getTextParagraphs();
                for (List<HSLFTextParagraph> paraList : paragraphs) {
                    String text = HSLFTextParagraph.getText(paraList);
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append("\n");
                    }
                }
                sb.append("\n");
                slideIndex++;
            }
            return sb.toString();
        }
    }

    /**
     * XLSX 解析
     */
    private String parseXlsx(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             Workbook workbook = new XSSFWorkbook(is)) {
            return parseWorkbook(workbook);
        }
    }

    /**
     * XLS 解析（旧版 Excel 格式）
     */
    private String parseXls(byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data);
             Workbook workbook = new HSSFWorkbook(is)) {
            return parseWorkbook(workbook);
        }
    }

    private String parseWorkbook(Workbook workbook) {
        DataFormatter formatter = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        int sheetCount = workbook.getNumberOfSheets();
        for (int s = 0; s < sheetCount; s++) {
            Sheet sheet = workbook.getSheetAt(s);
            String sheetName = sheet.getSheetName();
            sb.append("[工作表: ").append(sheetName).append("]\n");
            for (Row row : sheet) {
                StringBuilder rowSb = new StringBuilder();
                boolean hasData = false;
                for (Cell cell : row) {
                    CellType type = cell.getCellType();
                    if (type == CellType.BLANK) {
                        rowSb.append("\t");
                        continue;
                    }
                    String value = formatter.formatCellValue(cell);
                    if (value != null && !value.isEmpty()) {
                        hasData = true;
                    }
                    rowSb.append(value).append("\t");
                }
                if (hasData) {
                    sb.append(rowSb).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析结果
     */
    public static class ParseResult {
        private final String content;
        private final String mimeType;

        public ParseResult(String content, String mimeType) {
            this.content = content;
            this.mimeType = mimeType;
        }

        public String getContent() {
            return content;
        }

        public String getMimeType() {
            return mimeType;
        }
    }
}
