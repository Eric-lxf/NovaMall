package com.ruoyi.mall.product.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 商品详情富文本白名单。禁止脚本、事件属性、内联样式和危险协议。
 */
public final class MallProductRichTextSanitizer
{
    private static final Safelist SAFELIST = safelist();

    private MallProductRichTextSanitizer()
    {
    }

    public static String sanitize(String html)
    {
        return html == null ? null
                : Jsoup.clean(html, "", SAFELIST, new Document.OutputSettings().prettyPrint(false));
    }

    private static Safelist safelist()
    {
        return Safelist.none()
                .addTags("p", "div", "span", "strong", "b", "em", "i", "u", "s", "blockquote", "pre",
                        "code", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "table",
                        "thead", "tbody", "tr", "td", "th", "br", "hr", "a", "img")
                .addAttributes("a", "href", "target", "title")
                .addAttributes("img", "src", "width", "height", "alt", "title")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("th", "colspan", "rowspan")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                .preserveRelativeLinks(true);
    }
}
