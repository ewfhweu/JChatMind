package com.kama.jchatmind.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class HtmlToMarkdown {

    public static String convert(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        Document doc = Jsoup.parse(html);
        return processElement(doc.body());
    }

    private static String processElement(Element element) {
        StringBuilder sb = new StringBuilder();

        for (Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                sb.append(((TextNode) node).text());
            } else if (node instanceof Element) {
                Element childElement = (Element) node;
                sb.append(processElement(childElement));
            }
        }

        return formatElement(element, sb.toString());
    }

    private static String formatElement(Element element, String content) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            case "h1":
                return "# " + content + "\n\n";
            case "h2":
                return "## " + content + "\n\n";
            case "h3":
                return "### " + content + "\n\n";
            case "h4":
                return "#### " + content + "\n\n";
            case "h5":
                return "##### " + content + "\n\n";
            case "h6":
                return "###### " + content + "\n\n";
            case "p":
                return content + "\n\n";
            case "br":
                return "\n";
            case "strong":
            case "b":
                return "**" + content + "**";
            case "em":
            case "i":
                return "*" + content + "*";
            case "ul":
                return content;
            case "ol":
                return content;
            case "li":
                return "- " + content + "\n";
            case "blockquote":
                return "> " + content + "\n";
            case "code":
                return "`" + content + "`";
            case "pre":
                return "```\n" + content + "\n```\n\n";
            default:
                return content;
        }
    }
}