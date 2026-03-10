package com.kama.jchatmind.service.impl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符切片器
 * 按照优先级顺序进行递归拆分：\n\n, \n, 。, ！, ？, 空格
 */
@Component
@Slf4j
public class RecursiveCharacterSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 600;
    private static final int DEFAULT_OVERLAP = 60;

    private static final String[] SPLITTERS = {"\n\n", "\n", "。", "！", "？", " "};

    /**
     * 递归切片
     * 
     * @param text 原始文本
     * @param chunkSize 块大小
     * @param overlap 重叠大小
     * @param headingPath 标题路径（用于语义增强）
     * @return 切片列表
     */
    public List<Chunk> split(String text, int chunkSize, int overlap, String headingPath) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        String enhancedText = enhanceWithContext(text, headingPath);
        recursiveSplit(enhancedText, chunkSize, overlap, 0, chunks);
        
        log.info("递归切片完成: 原始长度={}, 切片数量={}", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 使用默认参数进行切片
     */
    public List<Chunk> split(String text, String headingPath) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP, headingPath);
    }

    /**
     * 递归拆分文本
     * 
     * @param text 待拆分文本
     * @param chunkSize 目标块大小
     * @param overlap 重叠大小
     * @param startIndex 起始索引
     * @param chunks 结果列表
     */
    private void recursiveSplit(String text, int chunkSize, int overlap, int startIndex, List<Chunk> chunks) {
        if (startIndex >= text.length()) {
            return;
        }

        int endIndex = Math.min(startIndex + chunkSize, text.length());
        String chunkText = text.substring(startIndex, endIndex);

        // 如果块大小超过目标大小，尝试在合适的位置切分
        if (chunkText.length() > chunkSize) {
            int splitPosition = findBestSplitPosition(chunkText, chunkSize);
            if (splitPosition > 0 && splitPosition < chunkText.length()) {
                chunkText = chunkText.substring(0, splitPosition);
                endIndex = startIndex + splitPosition;
            }
        }

        // 添加到结果列表
        chunks.add(new Chunk(chunkText, startIndex, endIndex));

        // 计算下一个块的起始位置（考虑重叠）
        int nextStartIndex = endIndex - overlap;
        if (nextStartIndex <= startIndex) {
            nextStartIndex = endIndex; // 避免死循环
        }

        // 递归处理剩余文本
        recursiveSplit(text, chunkSize, overlap, nextStartIndex, chunks);
    }

    /**
     * 寻找最佳切分位置
     * 按照优先级顺序尝试在合适的位置切分
     * 
     * @param text 待切分文本
     * @param targetSize 目标大小
     * @return 最佳切分位置
     */
    private int findBestSplitPosition(String text, int targetSize) {
        // 优先在目标大小附近寻找切分点
        int searchStart = Math.max(0, targetSize - 100);
        int searchEnd = Math.min(text.length(), targetSize + 100);

        for (String splitter : SPLITTERS) {
            int splitPos = findSplitPositionInRange(text, splitter, searchStart, searchEnd);
            if (splitPos > 0) {
                return splitPos + splitter.length();
            }
        }

        // 如果找不到合适的切分点，返回目标大小
        return Math.min(targetSize, text.length());
    }

    /**
     * 在指定范围内寻找切分位置
     * 
     * @param text 文本
     * @param splitter 切分符
     * @param start 起始位置
     * @param end 结束位置
     * @return 切分位置，如果未找到返回 -1
     */
    private int findSplitPositionInRange(String text, String splitter, int start, int end) {
        String searchRange = text.substring(start, Math.min(end, text.length()));
        int splitPos = searchRange.indexOf(splitter);
        
        if (splitPos >= 0) {
            return start + splitPos;
        }
        return -1;
    }

    /**
     * 语义增强：在内容头部注入标题路径信息
     * 
     * @param content 原始内容
     * @param headingPath 标题路径
     * @return 增强后的内容
     */
    private String enhanceWithContext(String content, String headingPath) {
        if (headingPath == null || headingPath.isEmpty()) {
            return content;
        }
        return "[Context: " + headingPath + "] " + content;
    }

    /**
     * 切片数据类
     */
    @Data
    public static class Chunk {
        /**
         * 切片内容
         */
        private String content;

        /**
         * 起始位置
         */
        private int startIndex;

        /**
         * 结束位置
         */
        private int endIndex;

        public Chunk(String content, int startIndex, int endIndex) {
            this.content = content;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public int getLength() {
            return endIndex - startIndex;
        }
    }
}
