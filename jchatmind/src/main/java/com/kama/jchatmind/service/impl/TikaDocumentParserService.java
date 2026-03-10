package com.kama.jchatmind.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class TikaDocumentParserService {

    private final Tika tika;
    private final AutoDetectParser parser;

    public TikaDocumentParserService() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    /**
     * 解析文档内容为HTML
     * @param file 文件
     * @return 解析后的HTML内容
     * @throws SAXException 
     */
    public String parseDocumentToHtml(File file) throws SAXException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return parseDocumentToHtml(inputStream);
        } catch (IOException | TikaException e) {
            log.error("解析文档失败: {}", file.getAbsolutePath(), e);
            throw new RuntimeException("解析文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析文档内容为HTML
     * @param inputStream 输入流
     * @return 解析后的HTML内容
     * @throws SAXException 
     */
    public String parseDocumentToHtml(InputStream inputStream) throws IOException, TikaException, SAXException {
        ToHTMLContentHandler handler = new ToHTMLContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        parser.parse(inputStream, handler, metadata, context);
        
        log.info("文档解析完成，HTML内容长度: {}", handler.toString().length());
        return handler.toString();
    }

    /**
     * 检测文件类型
     * @param file 文件
     * @return 文件类型
     */
    public String detectFileType(File file) {
        try {
            return tika.detect(file);
        } catch (IOException e) {
            log.error("检测文件类型失败: {}", file.getAbsolutePath(), e);
            return "application/octet-stream";
        }
    }

    /**
     * 检测文件类型
     * @param inputStream 输入流
     * @param filename 文件名
     * @return 文件类型
     * @throws IOException 
     */
    public String detectFileType(InputStream inputStream, String filename) throws IOException {
        return tika.detect(inputStream, filename);
    }
}
