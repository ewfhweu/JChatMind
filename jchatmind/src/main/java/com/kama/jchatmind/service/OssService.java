package com.kama.jchatmind.service;

import java.io.InputStream;

/**
 * OSS 服务接口
 */
public interface OssService {

    /**
     * 上传图片到 OSS
     *
     * @param inputStream 图片输入流
     * @param fileName    文件名
     * @param contentType 内容类型
     * @return 上传后的图片访问 URL
     */
    String uploadImage(InputStream inputStream, String fileName, String contentType);

    /**
     * 生成唯一的文件名
     *
     * @param originalFileName 原始文件名
     * @return 生成的唯一文件名
     */
    String generateUniqueFileName(String originalFileName);
}
