package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.exception.ErrorCode;
import com.kama.jchatmind.service.DocumentProcessor;
import com.kama.jchatmind.service.DocumentStorageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class MarkdownDocumentProcessor implements DocumentProcessor {

    private final DocumentStorageService documentStorageService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final HeadingTreeBuilder headingTreeBuilder;
    private final RecursiveCharacterSplitter recursiveCharacterSplitter;
    private final DocumentFacadeServiceImpl documentFacadeService;

    /**
     * 【重构说明】：事务处理
     * - 添加 @Transactional 确保数据库操作原子性
     * - 先删除旧数据，再插入新数据
     * - 异常时自动回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(String kbId, String documentId, String filePath) {
        log.info("开始处理 Markdown 文档: kbId={}, documentId={}, filePath={}", kbId, documentId, filePath);

        // 1. 读取文件内容
        String markdownContent;
        try {
            Path path = documentStorageService.getFilePath(filePath);
            markdownContent = Files.readString(path);
        } catch (IOException e) {
            String errorMessage = "读取 Markdown 文件失败: " + e.getMessage();
            log.error("读取 Markdown 文件失败: documentId={}, filePath={}, error={}", documentId, filePath, errorMessage, e);
            documentFacadeService.updateDocumentStatusWithError(documentId, errorMessage);
            throw BizException.of(ErrorCode.FILE_READ_FAILED, errorMessage, e);
        }

        if (markdownContent == null || markdownContent.isEmpty()) {
            log.warn("Markdown 文档内容为空: documentId={}", documentId);
            return;
        }

        // 2. 构建标题树
        List<HeadingTreeBuilder.HeadingNode> headingNodes = headingTreeBuilder.buildHeadingTree(markdownContent);
        headingTreeBuilder.assignContentToHeadings(headingNodes, markdownContent);

        if (headingNodes.isEmpty()) {
            log.warn("Markdown 文档解析后没有找到任何标题节点: documentId={}", documentId);
            // 【重构修改点】：没有标题时使用兜底逻辑
            processContentWithFallback(kbId, documentId, markdownContent);
            return;
        }

        // 3. 删除旧的 chunks（事务内操作）
        chunkBgeM3Mapper.deleteByDocId(documentId);

        // 4. 构建新的 chunks
        List<ChunkBgeM3> allChunks = buildChunks(kbId, documentId, headingNodes);

        // 5. 批量插入数据库
        if (!allChunks.isEmpty()) {
            chunkBgeM3Mapper.batchInsert(allChunks);
            log.info("Chunks 批量插入完成: documentId={}, 数量={}", documentId, allChunks.size());
        }

        // 6. 插入向量（非事务操作，失败不影响数据库）
        List<ChunkBgeM3> type1Chunks = allChunks.stream()
                .filter(chunk -> chunk.getType() == 1)
                .collect(Collectors.toList());

        if (!type1Chunks.isEmpty()) {
            documentFacadeService.insertChunksToMilvus(kbId, documentId, type1Chunks);
        }

        log.info("Markdown 文档处理完成: documentId={}, 标题节点数={}, 总切分数={}", 
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
     * 【新增方法】：兜底逻辑 - 无标题时直接切分
     */
    private void processContentWithFallback(String kbId, String documentId, String content) {
        log.info("使用兜底逻辑处理 Markdown: kbId={}, documentId={}", kbId, documentId);

        // 删除旧的 chunks
        chunkBgeM3Mapper.deleteByDocId(documentId);

        List<ChunkBgeM3> allChunks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 创建默认标题
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
        return "md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType);
    }
}
