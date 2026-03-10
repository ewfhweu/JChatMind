package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.ChunkBgeM3;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author charon
 * @description 针对表【chunk_bge_m3】的数据库操作Mapper
 * @createDate 2025-12-02 15:44:34
 * @Entity com.kama.jchatmind.model.entity.ChunkBgeM3
 */
@Mapper
public interface ChunkBgeM3Mapper {
    /**
     * 插入文档切分块
     */
    int insert(ChunkBgeM3 chunkBgeM3);

    /**
     * 批量插入文档切分块
     */
    int batchInsert(@Param("list") List<ChunkBgeM3> list);

    /**
     * 根据 ID 查询
     */
    ChunkBgeM3 selectById(String id);

    /**
     * 根据 docId 查询所有切分块
     */
    List<ChunkBgeM3> selectByDocId(String docId);

    /**
     * 根据 parentId 查询子块
     */
    List<ChunkBgeM3> selectByParentId(String parentId);

    /**
     * 根据 ID 删除
     */
    int deleteById(String id);

    /**
     * 根据 docId 删除所有切分块
     */
    int deleteByDocId(String docId);

    /**
     * 更新文档切分块
     */
    int updateById(ChunkBgeM3 chunkBgeM3);

    /**
     * 相似性搜索
     */
    List<ChunkBgeM3> similaritySearch(
            @Param("kbId") String kbId,
            @Param("vectorLiteral") String vectorLiteral,
            @Param("limit") int limit
    );
}
