package com.kama.jchatmind.service.impl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class HeadingTreeBuilder {

    /**
     * 从HTML内容构建标题树
     * @param htmlContent HTML内容
     * @return 标题节点列表
     */
    public List<HeadingNode> buildHeadingTree(String htmlContent) {
        List<HeadingNode> headingNodes = new ArrayList<>();
        
        // 提取所有h1-h6标签
        Pattern headingPattern = Pattern.compile("<h([1-6])>(.*?)</h[1-6]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher headingMatcher = headingPattern.matcher(htmlContent);
        
        int lastLevel = 0;
        HeadingNode lastHeading = null;
        List<HeadingNode> parentStack = new ArrayList<>();
        
        while (headingMatcher.find()) {
            int level = Integer.parseInt(headingMatcher.group(1));
            String title = headingMatcher.group(2).trim();
            
            // 清理标题中的HTML标签
            title = title.replaceAll("<[^>]*>", "");
            
            HeadingNode node = new HeadingNode();
            node.setLevel(level);
            node.setTitle(title);
            
            // 构建层级关系
            if (level > lastLevel) {
                if (lastHeading != null) {
                    parentStack.add(lastHeading);
                }
            } else if (level < lastLevel) {
                while (!parentStack.isEmpty() && parentStack.get(parentStack.size() - 1).getLevel() >= level) {
                    parentStack.remove(parentStack.size() - 1);
                }
            }
            
            if (!parentStack.isEmpty()) {
                HeadingNode parent = parentStack.get(parentStack.size() - 1);
                node.setParentId(parent.getId());
                node.setHeadingPath(parent.getHeadingPath() + " > " + title);
            } else {
                node.setParentId(null);
                node.setHeadingPath(title);
            }
            
            headingNodes.add(node);
            lastHeading = node;
            lastLevel = level;
        }
        
        log.info("构建标题树完成，共找到 {} 个标题节点", headingNodes.size());
        return headingNodes;
    }

    /**
     * 将内容分配给对应的标题
     * @param headingNodes 标题节点列表
     * @param htmlContent HTML内容
     */
    public void assignContentToHeadings(List<HeadingNode> headingNodes, String htmlContent) {
        if (headingNodes.isEmpty()) {
            log.warn("标题节点为空，使用兜底逻辑");
            return;
        }
        
        for (int i = 0; i < headingNodes.size(); i++) {
            HeadingNode currentNode = headingNodes.get(i);
            
            // 确定当前标题的结束位置
            int startIndex = htmlContent.indexOf("<h" + currentNode.getLevel() + ">" + currentNode.getTitle(), 0);
            if (startIndex == -1) {
                // 如果找不到标题标签，尝试使用更宽松的匹配
                startIndex = htmlContent.indexOf(currentNode.getTitle(), 0);
                if (startIndex == -1) {
                    continue;
                }
            }
            
            // 找到标题标签的结束位置
            int titleEndIndex = htmlContent.indexOf("</h" + currentNode.getLevel() + ">", startIndex);
            if (titleEndIndex == -1) {
                continue;
            }
            titleEndIndex += ("</h" + currentNode.getLevel() + ">").length();
            
            // 确定下一个标题的开始位置
            int endIndex;
            if (i < headingNodes.size() - 1) {
                HeadingNode nextNode = headingNodes.get(i + 1);
                endIndex = htmlContent.indexOf("<h" + nextNode.getLevel() + ">", titleEndIndex);
                if (endIndex == -1) {
                    endIndex = htmlContent.length();
                }
            } else {
                endIndex = htmlContent.length();
            }
            
            // 提取标题之间的内容
            String content = htmlContent.substring(titleEndIndex, endIndex).trim();
            
            // 清理内容，提取<p>和<table>标签的内容
            content = extractContentFromHtml(content);
            
            currentNode.setContent(content);
            log.debug("分配内容给标题: {}，内容长度: {}", currentNode.getTitle(), content.length());
        }
    }

    /**
     * 从HTML中提取内容
     * @param html HTML内容
     * @return 提取后的文本内容
     */
    private String extractContentFromHtml(String html) {
        StringBuilder contentBuilder = new StringBuilder();
        
        // 提取<p>标签内容
        Pattern pPattern = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher pMatcher = pPattern.matcher(html);
        while (pMatcher.find()) {
            String pContent = pMatcher.group(1).trim();
            // 清理HTML标签
            pContent = pContent.replaceAll("<[^>]*>", "");
            if (!pContent.isEmpty()) {
                contentBuilder.append(pContent).append("\n\n");
            }
        }
        
        // 提取<table>标签内容
        Pattern tablePattern = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher tableMatcher = tablePattern.matcher(html);
        while (tableMatcher.find()) {
            String tableContent = tableMatcher.group(1).trim();
            // 清理HTML标签
            tableContent = tableContent.replaceAll("<[^>]*>", "");
            if (!tableContent.isEmpty()) {
                contentBuilder.append("[表格内容]\n").append(tableContent).append("\n\n");
            }
        }
        
        // 如果没有找到<p>或<table>标签，使用兜底逻辑
        if (contentBuilder.length() == 0) {
            // 清理所有HTML标签
            String plainText = html.replaceAll("<[^>]*>", "");
            contentBuilder.append(plainText);
        }
        
        return contentBuilder.toString().trim();
    }

    @Data
    public static class HeadingNode {
        private String id;
        private String parentId;
        private String title;
        private String content;
        private int level;
        private String headingPath;
        
        public HeadingNode() {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }
}
