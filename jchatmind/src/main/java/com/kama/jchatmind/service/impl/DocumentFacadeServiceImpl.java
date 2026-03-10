package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.constants.MilvusConstants;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.exception.ErrorCode;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.model.request.CreateDocumentRequest;
import com.kama.jchatmind.model.request.UpdateDocumentRequest;
import com.kama.jchatmind.model.response.CreateDocumentResponse;
import com.kama.jchatmind.model.response.GetDocumentsResponse;
import com.kama.jchatmind.model.vo.DocumentVO;
import com.kama.jchatmind.service.DocumentFacadeService;
import com.kama.jchatmind.service.DocumentProcessor;
import com.kama.jchatmind.service.DocumentProcessorFactory;
import com.kama.jchatmind.service.DocumentStorageService;
import com.kama.jchatmind.service.RagService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class DocumentFacadeServiceImpl implements DocumentFacadeService {

    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final RagService ragService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final MilvusServiceClient milvusServiceClient;
    private final DocumentProcessorFactory documentProcessorFactory;

    @Override
    public GetDocumentsResponse getDocuments() {
        List<Document> documents = documentMapper.selectAllNotDeleted();
        List<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO vo = documentConverter.toVO(document);
                result.add(vo);
            } catch (JsonProcessingException e) {
                log.error("转换文档VO失败: documentId={}", document.getId(), e);
                throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
            }
        }
        return GetDocumentsResponse.builder()
                .documents(result.toArray(new DocumentVO[0]))
                .build();
    }

    @Override
    public GetDocumentsResponse getDocumentsByKbId(String kbId) {
        List<Document> documents = documentMapper.selectByKbIdNotDeleted(kbId);
        List<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO vo = documentConverter.toVO(document);
                result.add(vo);
            } catch (JsonProcessingException e) {
                log.error("转换文档VO失败: documentId={}", document.getId(), e);
                throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
            }
        }
        return GetDocumentsResponse.builder()
                .documents(result.toArray(new DocumentVO[0]))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateDocumentResponse createDocument(CreateDocumentRequest request) {
        try {
            DocumentDTO documentDTO = documentConverter.toDTO(request);
            Document document = documentConverter.toEntity(documentDTO);

            LocalDateTime now = LocalDateTime.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);

            int result = documentMapper.insert(document);
            if (result <= 0) {
                throw BizException.of(ErrorCode.DOCUMENT_CREATE_FAILED);
            }

            return CreateDocumentResponse.builder()
                    .documentId(document.getId())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("创建文档时发生序列化错误", e);
            throw BizException.of(ErrorCode.DOCUMENT_CREATE_FAILED, "创建文档时发生序列化错误: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateDocumentResponse uploadDocument(String kbId, MultipartFile file) {
        if (file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        String filetype = getFileType(originalFilename);
        long fileSize = file.getSize();

        DocumentDTO documentDTO = DocumentDTO.builder()
                .kbId(kbId)
                .filename(originalFilename)
                .filetype(filetype)
                .size(fileSize)
                .status("PENDING")
                .build();

        Document document;
        try {
            document = documentConverter.toEntity(documentDTO);
        } catch (JsonProcessingException e) {
            log.error("转换文档实体失败", e);
            throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        int result = documentMapper.insert(document);
        if (result <= 0) {
            throw BizException.of(ErrorCode.DOCUMENT_CREATE_FAILED);
        }

        String documentId = document.getId();
        String filePath;
        try {
            filePath = documentStorageService.saveFile(kbId, documentId, file);
        } catch (IOException e) {
            log.error("文件保存失败: documentId={}", documentId, e);
            throw BizException.of(ErrorCode.FILE_WRITE_FAILED, "文件保存失败: " + e.getMessage());
        }

        // 更新文件路径
        DocumentDTO.MetaData metadata = new DocumentDTO.MetaData();
        metadata.setFilePath(filePath);
        documentDTO.setMetadata(metadata);
        documentDTO.setId(documentId);
        documentDTO.setCreatedAt(now);
        documentDTO.setUpdatedAt(now);

        Document updatedDocument;
        try {
            updatedDocument = documentConverter.toEntity(documentDTO);
            updatedDocument.setId(documentId);
            updatedDocument.setCreatedAt(now);
            updatedDocument.setUpdatedAt(now);
        } catch (JsonProcessingException e) {
            log.error("转换更新文档实体失败: documentId={}", documentId, e);
            throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
        }

        documentMapper.updateById(updatedDocument);
        log.info("文档上传成功: kbId={}, documentId={}, filename={}", kbId, documentId, originalFilename);

        DocumentProcessor processor = documentProcessorFactory.getProcessor(filetype);
        if (processor != null) {
            updateDocumentStatus(documentId, "PARSING");
            if ("pdf".equalsIgnoreCase(filetype)) {
                processDocumentAsync(processor, kbId, documentId, filePath);
            } else {
                try {
                    processor.process(kbId, documentId, filePath);
                    updateDocumentStatus(documentId, "SUCCESS");
                } catch (Exception e) {
                    log.error("处理文档失败: documentId={}", documentId, e);
                    updateDocumentStatusWithError(documentId, e.getMessage());
                    throw BizException.of(ErrorCode.DOCUMENT_PROCESS_FAILED, "处理文档失败: " + e.getMessage());
                }
            }
        } else {
            log.warn("暂不支持的文件类型: {}", filetype);
            updateDocumentStatus(documentId, "SUCCESS");
        }

        return CreateDocumentResponse.builder()
                .documentId(documentId)
                .build();
    }

    /**
     * 【重构修改点】：异常处理优化
     * - 原代码：catch Exception 后仅记录日志，异常被吞掉
     * - 新代码：继续抛出运行时异常，确保调用方知道更新失败
     * - 事务说明：此方法在事务中调用，抛出异常会导致事务回滚
     */
    private void updateDocumentStatus(String documentId, String status) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("更新文档状态失败，文档不存在: documentId={}", documentId);
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        document.setStatus(status);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        log.info("文档状态更新成功: documentId={}, status={}", documentId, status);
    }

    /**
     * 【重构修改点】：异常处理优化
     * - 原代码：catch Exception 后仅记录日志，异常被吞掉
     * - 新代码：继续抛出运行时异常，确保调用方知道更新失败
     * - 事务说明：此方法在事务中调用，抛出异常会导致事务回滚
     * - 修改为 public，供 DocumentProcessor 调用
     */
    public void updateDocumentStatusWithError(String documentId, String errorMessage) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("更新文档失败状态失败，文档不存在: documentId={}", documentId);
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        document.setStatus("FAILED");
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        log.info("文档状态更新为失败: documentId={}, error={}", documentId, errorMessage);
    }

    /**
     * 【重构说明】：异步方法事务处理
     * - 注意：@Async 方法的事务不会传播到新线程
     * - processor.process() 内部需要自行管理事务
     * - 状态更新通过单独方法调用，确保状态可见
     */
    @Async("taskExecutor")
    public void processDocumentAsync(DocumentProcessor processor, String kbId, String documentId, String filePath) {
        log.info("开始异步处理文档: kbId={}, documentId={}", kbId, documentId);
        try {
            processor.process(kbId, documentId, filePath);
            updateDocumentStatus(documentId, "SUCCESS");
            log.info("文档处理完成: kbId={}, documentId={}", kbId, documentId);
        } catch (Exception e) {
            log.error("处理文档失败: kbId={}, documentId={}", kbId, documentId, e);
            updateDocumentStatusWithError(documentId, e.getMessage());
            // 【重构修改点】：不再吞掉异常，抛出以确保异步任务状态正确
            throw new RuntimeException("处理文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }

        // 删除 Milvus 向量（非关键操作，失败继续）
        try {
            log.info("开始删除 Milvus 中的向量记录: documentId={}", documentId);
            milvusServiceClient.delete(
                DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr("metadata[\"docId\"] == \"" + documentId + "\"")
                    .build()
            );
            log.info("Milvus 向量记录删除完成: documentId={}", documentId);
        } catch (Exception e) {
            log.warn("删除 Milvus 向量记录失败，继续删除文档记录: documentId={}, error={}", documentId, e.getMessage());
        }

        // 删除物理文件（非关键操作，失败继续）
        try {
            DocumentDTO documentDTO = documentConverter.toDTO(document);
            if (documentDTO.getMetadata() != null && documentDTO.getMetadata().getFilePath() != null) {
                String filePath = documentDTO.getMetadata().getFilePath();
                documentStorageService.deleteFile(filePath);
            }
        } catch (Exception e) {
            log.warn("删除文件失败，继续删除文档记录: documentId={}, error={}", documentId, e.getMessage());
        }

        // 删除数据库记录（关键操作）
        chunkBgeM3Mapper.deleteByDocId(documentId);
        int result = documentMapper.softDeleteById(documentId);
        if (result <= 0) {
            throw BizException.of(ErrorCode.DOCUMENT_DELETE_FAILED);
        }
    }

    /**
     * 【公共方法】：供 Processor 调用，插入向量到 Milvus
     * - 此方法被 MarkdownDocumentProcessor 和 PdfDocumentProcessor 调用
     * - 不能删除
     */
    public void insertChunksToMilvus(String kbId, String docId, List<ChunkBgeM3> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("没有需要插入 Milvus 的 chunks: kbId={}, docId={}", kbId, docId);
            return;
        }

        try {
            log.info("开始插入 Milvus: kbId={}, docId={}, chunkCount={}", kbId, docId, chunks.size());

            // 先删除旧的向量
            milvusServiceClient.delete(
                DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr("metadata[\"docId\"] == \"" + docId + "\"")
                    .build()
            );

            List<String> ids = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();

            for (ChunkBgeM3 chunk : chunks) {
                ids.add(chunk.getId());
                texts.add(chunk.getContent());

                Map<String, String> metadata = new HashMap<>();
                metadata.put("kbId", kbId);
                metadata.put("docId", docId);
                metadata.put("parentId", chunk.getParentId());
                metadatas.add(metadata);
            }

            // 批量向量化
            List<float[]> embeddings = ragService.batchEmbed(texts);
            List<List<Float>> vectors = new ArrayList<>();
            for (float[] embedding : embeddings) {
                List<Float> floatList = new ArrayList<>();
                for (float f : embedding) {
                    floatList.add(f);
                }
                vectors.add(floatList);
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", ids));
            fields.add(new InsertParam.Field("vector", vectors));
            fields.add(new InsertParam.Field("metadata", metadatas));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            R<MutationResult> response = milvusServiceClient.insert(insertParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus 插入失败: " + response.getMessage());
            }

            log.info("Milvus 插入完成: kbId={}, docId={}, 插入数量={}", kbId, docId, ids.size());
        } catch (Exception e) {
            log.error("插入 Milvus 失败: kbId={}, docId={}", kbId, docId, e);
            throw new RuntimeException("插入 Milvus 失败: " + e.getMessage(), e);
        }
    }

    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(String documentId, UpdateDocumentRequest request) {
        Document existingDocument = documentMapper.selectById(documentId);
        if (existingDocument == null) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }

        DocumentDTO documentDTO;
        try {
            documentDTO = documentConverter.toDTO(existingDocument);
            documentConverter.updateDTOFromRequest(documentDTO, request);
        } catch (JsonProcessingException e) {
            log.error("转换文档DTO失败: documentId={}", documentId, e);
            throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
        }

        Document updatedDocument;
        try {
            updatedDocument = documentConverter.toEntity(documentDTO);
        } catch (JsonProcessingException e) {
            log.error("转换文档实体失败: documentId={}", documentId, e);
            throw BizException.of(ErrorCode.PARAM_INVALID, "转换文档数据失败: " + e.getMessage());
        }

        updatedDocument.setId(existingDocument.getId());
        updatedDocument.setKbId(existingDocument.getKbId());
        updatedDocument.setCreatedAt(existingDocument.getCreatedAt());
        updatedDocument.setUpdatedAt(LocalDateTime.now());

        int result = documentMapper.updateById(updatedDocument);
        if (result <= 0) {
            throw BizException.of(ErrorCode.DOCUMENT_UPDATE_FAILED);
        }
    }
}
