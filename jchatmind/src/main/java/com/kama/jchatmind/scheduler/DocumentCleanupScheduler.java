package com.kama.jchatmind.scheduler;

import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.service.DocumentStorageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class DocumentCleanupScheduler {

    private final DocumentMapper documentMapper;
    private final DocumentStorageService documentStorageService;

    // 每天凌晨2点执行清理任务
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupDeletedDocuments() {
        try {
            log.info("开始清理已删除的文档");

            List<Document> deletedDocuments = documentMapper.selectDeleted();
            log.info("找到 {} 个已删除的文档", deletedDocuments.size());

            for (Document document : deletedDocuments) {
                try {
                    // 这里可以添加删除文件和Milvus向量的逻辑
                    // 由于之前在软删除时已经处理了这些操作，这里主要是清理数据库记录
                    documentMapper.deleteById(document.getId());
                    log.info("清理文档: id={}, filename={}", document.getId(), document.getFilename());
                } catch (Exception e) {
                    log.error("清理文档失败: id={}, error={}", document.getId(), e.getMessage());
                }
            }

            log.info("文档清理完成");
        } catch (Exception e) {
            log.error("执行文档清理任务失败", e);
        }
    }
}