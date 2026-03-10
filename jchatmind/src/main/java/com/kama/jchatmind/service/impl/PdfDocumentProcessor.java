package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.service.DocumentProcessor;
import com.kama.jchatmind.service.DocumentStorageService;
import com.kama.jchatmind.util.HtmlToMarkdown;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class PdfDocumentProcessor implements DocumentProcessor {

    private final DocumentStorageService documentStorageService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final HeadingTreeBuilder headingTreeBuilder;
    private final RecursiveCharacterSplitter recursiveCharacterSplitter;
    private final TikaDocumentParserService tikaDocumentParserService;
    private final DocumentFacadeServiceImpl documentFacadeService;

    /**
     * 【重构说明】：事务处理
     * - 添加 @Transactional 确保数据库操作原子性
     * - 异常时自动回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(String kbId, String documentId, String filePath) {
        log.info("开始处理 PDF 文档: kbId={}, documentId={}, filePath={}", kbId, documentId, filePath);

        // 1. 解析 PDF 为 HTML
        String htmlContent;
        try {
            Path path = documentStorageService.getFilePath(filePath);
            htmlContent = tikaDocumentParserService.parseDocumentToHtml(path.toFile());
        } catch (Exception e) {
            String errorMessage = "Tika 解析 PDF 文档失败: " + e.getMessage();
            log.error("解析 PDF 文档失败: documentId={}, filePath={}, error={}", documentId, filePath, errorMessage, e);
            documentFacadeService.updateDocumentStatusWithError(documentId, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }

        log.info("PDF 文档解析完成，HTML内容长度: {}", htmlContent.length());

        // 2. 将 HTML 转换为 Markdown
        String markdownContent;
        try {
            markdownContent = HtmlToMarkdown.convert(htmlContent);
        } catch (Exception e) {
            String errorMessage = "HTML 转 Markdown 失败: " + e.getMessage();
            log.error("HTML 转 Markdown 失败: documentId={}, error={}", documentId, errorMessage, e);
            documentFacadeService.updateDocumentStatusWithError(documentId, errorMessage);
            throw new RuntimeException(errorMessage, e);
        }

        // 3. 处理解析后的内容
        processParsedContent(kbId, documentId, markdownContent);

        log.info("PDF 文档处理完成: kbId={}, documentId={}", kbId, documentId);
    }

    /**
     * 【重构修改点】：优化异常处理，不再吞掉异常
     */
    private void processParsedContent(String kbId, String documentId, String content) {
        log.info("开始处理解析后的内容: kbId={}, documentId={}", kbId, documentId);

        if (content == null || content.isEmpty()) {
            log.warn("解析后的内容为空: documentId={}", documentId);
            return;
        }

        // 构建标题树
        List<HeadingTreeBuilder.HeadingNode> headingNodes = headingTreeBuilder.buildHeadingTree(content);
        
        // 兜底逻辑：如果没有找到标题节点，直接进行暴力切分
        if (headingNodes.isEmpty()) {
            log.info("未找到标题节点，使用兜底逻辑进行暴力切分: documentId={}", documentId);
            processContentWithFallback(kbId, documentId, content);
            return;
        }

        headingTreeBuilder.assignContentToHeadings(headingNodes, content);

        // 删除旧的 chunks（事务内操作）
        chunkBgeM3Mapper.deleteByDocId(documentId);

        // 构建新的 chunks
        List<ChunkBgeM3> allChunks = buildChunks(kbId, documentId, headingNodes);

        // 批量插入数据库
        if (!allChunks.isEmpty()) {
            chunkBgeM3Mapper.batchInsert(allChunks);
            log.info("Chunks 批量插入完成: documentId={}, 数量={}", documentId, allChunks.size());
        }

        // 插入向量（非事务操作，失败不影响数据库）
        List<ChunkBgeM3> type1Chunks = allChunks.stream()
                .filter(chunk -> chunk.getType() == 1)
                .collect(Collectors.toList());

        if (!type1Chunks.isEmpty()) {
            documentFacadeService.insertChunksToMilvus(kbId, documentId, type1Chunks);
        }

        log.info("解析内容处理完成: documentId={}, 标题节点数={}, 总切分数={}", 
                documentId, headingNodes.size(), allChunks.size());
    }

    /**
     * 【重构修改点】：提取 chunk 构建逻辑，提高可读性
     */
    private List<ChunkBgeM3> buildChunks(String kbId, String documentId, 
            List<HeadingTreeBuilder.HeadingNode> headingNodes) {
        List<ChunkBgeM3> allChunks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (HeadingTreeBuilder.HeadingNode headingNode : headingNodes) {
            String headingId = UUID.randomUUID().toString();
            
            // 创建标题 chunk
            ChunkBgeM3 headingChunk = ChunkBgeM3.builder()
                    .id(headingId)
                    .parentId(headingNode.getParentId())
                    .kbId(kbId)
                    .docId(documentId)
                    .content(headingNode.getTitle())
                    .type(0)
                    .headingPath(headingNode.getHeadingPath())
                    .headingLevel(headingNode.getLevel())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            
            allChunks.add(headingChunk);

            // 切分内容并创建内容 chunks
            if (headingNode.getContent() != null && !headingNode.getContent().isEmpty()) {
                List<RecursiveCharacterSplitter.Chunk> splitChunks = recursiveCharacterSplitter.split(
                        headingNode.getContent(), 
                        headingNode.getHeadingPath()
                );

                for (RecursiveCharacterSplitter.Chunk splitChunk : splitChunks) {
                    String chunkId = UUID.randomUUID().toString();
                    
                    ChunkBgeM3 contentChunk = ChunkBgeM3.builder()
                            .id(chunkId)
                            .parentId(headingId)
                            .kbId(kbId)
                            .docId(documentId)
                            .content(splitChunk.getContent())
                            .type(1)
                            .headingPath(headingNode.getHeadingPath())
                            .headingLevel(headingNode.getLevel())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    
                    allChunks.add(contentChunk);
                }
            }
        }

        return allChunks;
    }

    /**
     * 【重构修改点】：优化异常处理，不再吞掉异常
     */
    private void processContentWithFallback(String kbId, String documentId, String content) {
        log.info("开始使用兜底逻辑处理内容: kbId={}, documentId={}", kbId, documentId);

        // 删除旧的 chunks
        chunkBgeM3Mapper.deleteByDocId(documentId);

        List<ChunkBgeM3> allChunks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 创建一个默认标题
        String defaultHeadingId = UUID.randomUUID().toString();
        String defaultHeading = "文档内容";
        String defaultHeadingPath = defaultHeading;

        ChunkBgeM3 defaultHeadingChunk = ChunkBgeM3.builder()
                .id(defaultHeadingId)
                .parentId(null)
                .kbId(kbId)
                .docId(documentId)
                .content(defaultHeading)
                .type(0)
                .headingPath(defaultHeadingPath)
                .headingLevel(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
        allChunks.add(defaultHeadingChunk);

        // 对纯文本进行递归切分
        List<RecursiveCharacterSplitter.Chunk> splitChunks = recursiveCharacterSplitter.split(
                content,
                defaultHeadingPath
        );

        for (RecursiveCharacterSplitter.Chunk splitChunk : splitChunks) {
            String chunkId = UUID.randomUUID().toString();

            ChunkBgeM3 contentChunk = ChunkBgeM3.builder()
                    .id(chunkId)
                    .parentId(defaultHeadingId)
                    .kbId(kbId)
                    .docId(documentId)
                    .content(splitChunk.getContent())
                    .type(1)
                    .headingPath(defaultHeadingPath)
                    .headingLevel(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            allChunks.add(contentChunk);
        }

        // 批量插入
        if (!allChunks.isEmpty()) {
            chunkBgeM3Mapper.batchInsert(allChunks);
        }

        // 插入向量
        List<ChunkBgeM3> type1Chunks = allChunks.stream()
                .filter(chunk -> chunk.getType() == 1)
                .collect(Collectors.toList());

        if (!type1Chunks.isEmpty()) {
            documentFacadeService.insertChunksToMilvus(kbId, documentId, type1Chunks);
        }

        log.info("兜底逻辑处理完成: documentId={}, 总切分数={}", documentId, allChunks.size());
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
}
