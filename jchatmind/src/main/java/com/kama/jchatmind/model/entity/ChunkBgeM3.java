package com.kama.jchatmind.model.entity;

import java.time.LocalDateTime;
import java.util.Arrays;

import lombok.Builder;
import lombok.Data;

/**
 * @TableName chunk_bge_m3
 */
@Data
@Builder
public class ChunkBgeM3 {
    private String id;

    private String kbId;

    private String docId;

    private String content;

    private String metadata;

    private float[] embedding;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 父级标题 ID（用于构建标题树）
     */
    private String parentId;

    /**
     * 类型：0=标题, 1=分片
     */
    private Integer type;

    /**
     * 标题路径（如：[一级标题 > 二级标题 > 当前标题]）
     */
    private String headingPath;

    /**
     * 标题层级（1-6）
     */
    private Integer headingLevel;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        ChunkBgeM3 other = (ChunkBgeM3) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getKbId() == null ? other.getKbId() == null : this.getKbId().equals(other.getKbId()))
            && (this.getDocId() == null ? other.getDocId() == null : this.getDocId().equals(other.getDocId()))
            && (this.getContent() == null ? other.getContent() == null : this.getContent().equals(other.getContent()))
            && (this.getMetadata() == null ? other.getMetadata() == null : this.getMetadata().equals(other.getMetadata()))
            && (this.getEmbedding() == null ? other.getEmbedding() == null : Arrays.equals(this.getEmbedding(), other.getEmbedding()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()))
            && (this.getParentId() == null ? other.getParentId() == null : this.getParentId().equals(other.getParentId()))
            && (this.getType() == null ? other.getType() == null : this.getType().equals(other.getType()))
            && (this.getHeadingPath() == null ? other.getHeadingPath() == null : this.getHeadingPath().equals(other.getHeadingPath()))
            && (this.getHeadingLevel() == null ? other.getHeadingLevel() == null : this.getHeadingLevel().equals(other.getHeadingLevel()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getKbId() == null) ? 0 : getKbId().hashCode());
        result = prime * result + ((getDocId() == null) ? 0 : getDocId().hashCode());
        result = prime * result + ((getContent() == null) ? 0 : getContent().hashCode());
        result = prime * result + ((getMetadata() == null) ? 0 : getMetadata().hashCode());
        result = prime * result + ((getEmbedding() == null) ? 0 : Arrays.hashCode(getEmbedding()));
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        result = prime * result + ((getParentId() == null) ? 0 : getParentId().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        result = prime * result + ((getHeadingPath() == null) ? 0 : getHeadingPath().hashCode());
        result = prime * result + ((getHeadingLevel() == null) ? 0 : getHeadingLevel().hashCode());
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" +
                "Hash = " + hashCode() +
                ", id=" + id +
                ", kbId=" + kbId +
                ", docId=" + docId +
                ", content=" + content +
                ", metadata=" + metadata +
                ", embedding=" + Arrays.toString(embedding) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", parentId=" + parentId +
                ", type=" + type +
                ", headingPath=" + headingPath +
                ", headingLevel=" + headingLevel +
                "]";
    }
}