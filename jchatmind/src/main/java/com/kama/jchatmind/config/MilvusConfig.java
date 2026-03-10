package com.kama.jchatmind.config;

import com.kama.jchatmind.client.MilvusClientFactory;
import com.kama.jchatmind.constants.MilvusConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 配置类
 * 负责创建和管理 MilvusServiceClient Bean
 */
@Configuration
public class MilvusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建 MilvusServiceClient Bean
     * 
     * @return MilvusServiceClient 实例
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        logger.info("正在初始化 Milvus 客户端...");
        milvusClient = milvusClientFactory.createClient();
        logger.info("Milvus 客户端初始化完成");
        
        // 加载 collection 到内存
        loadCollection();
        
        return milvusClient;
    }

    /**
     * 加载 collection 到内存
     */
    private void loadCollection() {
        logger.info("正在加载 Milvus collection: {}", MilvusConstants.MILVUS_COLLECTION_NAME);
        String collectionName=MilvusConstants.MILVUS_COLLECTION_NAME;
        HasCollectionParam hasParam=HasCollectionParam.newBuilder()
        .withCollectionName(collectionName)
        .build();
        R<Boolean> hashResp=milvusClient.hasCollection(hasParam);
        if(!hashResp.getData()){
            logger.error("loadCollection不存在",collectionName);
            return;
        }
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        R<RpcStatus> response = milvusClient.loadCollection(loadParam);
        if (response.getStatus() != 0) {
            logger.warn("加载 collection 失败: {}", response.getMessage());
            // 加载失败不抛出异常，继续执行
        } else {
            logger.info("Collection 加载成功: {}", MilvusConstants.MILVUS_COLLECTION_NAME);
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            logger.info("正在关闭 Milvus 客户端连接...");
            milvusClient.close();
            logger.info("Milvus 客户端连接已关闭");
        }
    }
}

