package com.ruoyi.history.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * 按页抽取 PDF 文本，便于片段携带 pageNo。
 */
public final class HistoryPdfTextExtractor
{
    private HistoryPdfTextExtractor()
    {
    }

    public static List<PageText> extractPages(byte[] pdfBytes) throws IOException
    {
        if (pdfBytes == null || pdfBytes.length == 0)
        {
            return List.of();
        }
        List<PageText> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes)))
        {
            int total = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= total; page++)
            {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                pages.add(new PageText(page, text == null ? "" : text));
            }
        }
        return pages;
    }

    public record PageText(int pageNo, String text)
    {
    }
}
