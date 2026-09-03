package com.ruoyi.exam.poc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Compatibility only: no server, no real customer files, no security certification. */
@Timeout(15)
class ParserCompatibilityTest {
    private Path fixture(String name) {
        Path result = Path.of(System.getProperty("exam.fixtures")).resolve(name).normalize();
        assertTrue(Files.isRegularFile(result), "Generate fixtures first: " + result);
        return result;
    }

    @Test void readsChineseUtf8WithoutReplacement() throws Exception {
        String text = Files.readString(fixture("source-basic.txt"), StandardCharsets.UTF_8);
        assertTrue(text.contains("三个工作日"));
        assertFalse(text.contains("\uFFFD"));
    }

    @Test void rejectsMalformedUtf8InsteadOfSilentlyReplacingIt() {
        assertThrows(IOException.class, () -> Files.readString(fixture("source-invalid-utf8.txt"), StandardCharsets.UTF_8));
    }

    @Test void docxBodyOrderKeepsTableBetweenParagraphs() throws Exception {
        List<String> ordered = new ArrayList<>();
        try (var input = Files.newInputStream(fixture("source-basic.docx")); var document = new XWPFDocument(input)) {
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) ordered.add(paragraph.getText());
                if (element instanceof XWPFTable table) {
                    for (var row : table.getRows()) for (var cell : row.getTableCells()) ordered.add(cell.getText());
                }
            }
        }
        String text = String.join("\n", ordered);
        assertTrue(text.indexOf("先核对请求编号") < text.indexOf("待确认"));
        assertTrue(text.indexOf("已完成") < text.indexOf("内部培训资料不得"));
        assertTrue(text.contains("记录交付时间并归档"));
    }

    @Test void pdfExtractionPreservesRealPageBoundaries() throws Exception {
        try (var pdf = PDDocument.load(fixture("source-text.pdf").toFile())) {
            assertEquals(2, pdf.getNumberOfPages());
            var stripper = new PDFTextStripper();
            stripper.setStartPage(1); stripper.setEndPage(1);
            String first = stripper.getText(pdf);
            assertTrue(first.contains("三个工作日"));
            assertFalse(first.contains("员工离职"));
            stripper.setStartPage(2); stripper.setEndPage(2);
            assertTrue(stripper.getText(pdf).contains("员工离职后应及时收回系统访问权限"));
        }
    }

    @Test void imageOnlyPdfHasNoTextAndRequiresExplicitUnsupportedResult() throws Exception {
        try (var pdf = PDDocument.load(fixture("source-image-only.pdf").toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(pdf).isBlank());
            assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
    }

    @Test void encryptedPdfRequiresPassword() {
        assertThrows(InvalidPasswordException.class, () -> {
            try (var pdf = PDDocument.load(fixture("source-encrypted.pdf").toFile())) { fail("Unexpected decrypted PDF"); }
        });
    }

    @Test void corruptPdfDoesNotLookLikeAValidEmptyDocument() {
        assertThrows(IOException.class, () -> {
            try (var pdf = PDDocument.load(fixture("source-corrupt.pdf").toFile())) { fail("Unexpected parsed PDF"); }
        });
    }

    @Test void duplicateZipPartIsDetectedBeforePassingToLegacyPoi() {
        assertThrows(IllegalArgumentException.class, () -> {
            var names = new HashSet<String>();
            try (var zip = new ZipFile(fixture("source-duplicate-part.docx").toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (!names.add(entries.nextElement().getName())) throw new IllegalArgumentException("DUPLICATE_ZIP_PART");
                }
            }
        });
    }

    @Test void twoColumnsNeedLayoutPolicyNotJustSuccessfulTextExtraction() throws Exception {
        try (var pdf = PDDocument.load(fixture("source-columns.pdf").toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("左栏 1"));
            assertTrue(text.contains("右栏 3"));
            // Documenting a real boundary: input order interleaves columns, not article reading order.
            assertTrue(text.indexOf("右栏 1") < text.indexOf("左栏 2"));
        }
    }
}
