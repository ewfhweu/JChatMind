package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.constants.MilvusConstants;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.exception.ErrorCode;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.service.RagService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagServiceImpl.class);

    private final WebClient webClient;
    private final MilvusServiceClient milvusServiceClient;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;

    public RagServiceImpl(WebClient.Builder builder, MilvusServiceClient milvusServiceClient, ChunkBgeM3Mapper chunkBgeM3Mapper) {
        this.webClient = builder.baseUrl("http://localhost:11434").build();
        this.milvusServiceClient = milvusServiceClient;
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
    }

    @Data
    private static class EmbeddingResponse {
        private float[] embedding;
    }

    /**
     * 【重构修改点】：优化异常处理，不再返回 null
     * - 原代码：返回 null 可能导致 NPE
     * - 新代码：抛出异常，调用方必须处理
     */
    private float[] doEmbed(String text) {
        try {
            EmbeddingResponse resp = webClient.post()
                    .uri("/api/embeddings")
                    .bodyValue(Map.of(
                            "model", "bge-m3",
                            "prompt", text
                    ))
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .block();
            
            if (resp == null || resp.getEmbedding() == null) {
                logger.error("Embedding API 返回空响应");
                throw BizException.of(ErrorCode.EMBEDDING_API_ERROR, "Embedding API 返回空响应");
            }
            
            return resp.getEmbedding();
        } catch (Exception e) {
            logger.error("调用 Embedding API 失败: text={}", text.substring(0, Math.min(50, text.length())), e);
            throw BizException.of(ErrorCode.EMBEDDING_API_ERROR, "调用 Embedding API 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        return doEmbed(text);
    }

    @Override
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(doEmbed(text));
        }
        return embeddings;
    }

    /**
     * 【重构修改点】：优化异常处理
     * - 原代码：catch Exception 后返回空列表，调用方无法区分"无结果"和"出错"
     * - 新代码：抛出异常，让调用方知道发生了错误
     */
    @Override
    public List<String> similaritySearch(String kbId, String title) {
        logger.info("开始相似性搜索: kbId={}, title={}", kbId, title);

        try {
            float[] queryVector = doEmbed(title);

            List<Float> floatList = new ArrayList<>();
            for (float f : queryVector) {
                floatList.add(f);
            }

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withMetricType(MetricType.L2)
                    .withTopK(3)
                    .withVectorFieldName("vector")
                    .withVectors(List.of(floatList))
                    .withParams("{\"nprobe\": 10}")
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withExpr("metadata[\"kbId\"] == \"" + kbId + "\"")
                    .build();

            R<SearchResults> response = milvusServiceClient.search(searchParam);
            if (response.getStatus() != 0) {
                logger.error("Milvus 搜索失败: {}", response.getMessage());
                throw BizException.of(ErrorCode.MILVUS_SEARCH_FAILED, "Milvus 搜索失败: " + response.getMessage());
            }

            SearchResultsWrapper resultsWrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResultsWrapper> resultsList = resultsWrapper.getResultsWrapper();
            
            if (resultsList == null || resultsList.isEmpty()) {
                logger.info("相似性搜索完成: 未找到匹配结果");
                return new ArrayList<>();
            }

            List<SearchResult> searchResults = resultsList.stream()
                    .map(result -> {
                        SearchResult searchResult = new SearchResult();
                        searchResult.setId(result.getStrID());
                        searchResult.setScore(result.getScore());
                        return searchResult;
                    })
                    .collect(Collectors.toList());

            List<String> enhancedResults = new ArrayList<>();
            for (SearchResult searchResult : searchResults) {
                String enhancedContent = reconstructContext(searchResult);
                enhancedResults.add(enhancedContent);
            }

            logger.info("相似性搜索完成: 返回 {} 个结果", enhancedResults.size());
            return enhancedResults;

        } catch (BizException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            logger.error("相似性搜索失败", e);
            throw BizException.of(ErrorCode.EMBEDDING_ERROR, "相似性搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 【重构修改点】：优化异常处理，不再吞掉异常
     */
    private String reconstructContext(SearchResult searchResult) {
        String chunkId = searchResult.getId();

        try {
            ChunkBgeM3 chunk = chunkBgeM3Mapper.selectById(chunkId);

            if (chunk == null) {
                logger.warn("未找到 chunk: {}", chunkId);
                return "[内容已丢失]";
            }

            String parentId = chunk.getParentId();
            if (parentId == null || parentId.isEmpty()) {
                return chunk.getContent();
            }

            ChunkBgeM3 parentChunk = chunkBgeM3Mapper.selectById(parentId);
            if (parentChunk == null) {
                return chunk.getContent();
            }

            List<ChunkBgeM3> siblingChunks = chunkBgeM3Mapper.selectByParentId(parentId);
            siblingChunks.sort(Comparator.comparing(ChunkBgeM3::getCreatedAt));

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("标题: ").append(parentChunk.getContent()).append("\n\n");

            for (ChunkBgeM3 sibling : siblingChunks) {
                contextBuilder.append(sibling.getContent()).append("\n\n");
            }

            logger.debug("上下文重组完成: chunkId={}, parentId={}, 标题={}",
                    chunkId, parentId, parentChunk.getContent());

            return contextBuilder.toString().trim();

        } catch (Exception e) {
            logger.error("上下文重组失败: chunkId={}", chunkId, e);
            // 【重构修改点】：不再返回原始内容，而是抛出异常
            throw BizException.of(ErrorCode.EMBEDDING_ERROR, "上下文重组失败: " + e.getMessage(), e);
        }
    }

    @Data
    private static class SearchResult {
        private String id;
        private String content;
        private float score;
    }
}
