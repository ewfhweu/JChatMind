package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChunkBgeM3DTO {
    private String id;

    private String kbId;

    private String docId;

    private String content;

    private MetaData metadata;

    private float[] embedding;
    // 添加标题相关字段
    private String parentId;
    private Integer type;
    private String headingPath;
    private Integer headingLevel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
    }
}
