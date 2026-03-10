package com.kama.jchatmind.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用错误
    SUCCESS(200, "操作成功"),
    ERROR(500, "服务器内部错误"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_INVALID(4001, "参数无效"),
    PARAM_MISSING(4002, "参数缺失"),

    // 文档相关错误
    DOCUMENT_NOT_FOUND(30001, "文档不存在"),
    DOCUMENT_CREATE_FAILED(30002, "创建文档失败"),
    DOCUMENT_UPDATE_FAILED(30003, "更新文档失败"),
    DOCUMENT_DELETE_FAILED(30004, "删除文档失败"),
    DOCUMENT_UPLOAD_FAILED(30005, "上传文档失败"),
    DOCUMENT_PARSE_FAILED(30006, "解析文档失败"),
    DOCUMENT_PROCESS_FAILED(30007, "处理文档失败"),

    // 文件相关错误
    FILE_ERROR(100001, "文件操作失败"),
    FILE_READ_FAILED(100002, "读取文件失败"),
    FILE_WRITE_FAILED(100003, "写入文件失败"),
    FILE_DELETE_FAILED(100004, "删除文件失败"),
    FILE_TYPE_NOT_SUPPORTED(100005, "不支持的文件类型"),
    FILE_EMPTY(100006, "文件为空"),

    // 向量数据库相关错误
    MILVUS_ERROR(80001, "向量数据库操作失败"),
    MILVUS_INSERT_FAILED(80002, "插入向量失败"),
    MILVUS_SEARCH_FAILED(80003, "向量搜索失败"),
    MILVUS_DELETE_FAILED(80004, "删除向量失败"),

    // 向量化相关错误
    EMBEDDING_ERROR(90001, "向量化失败"),
    EMBEDDING_API_ERROR(90002, "调用向量化 API 失败"),

    // 知识库相关错误
    KNOWLEDGE_BASE_NOT_FOUND(40001, "知识库不存在"),
    KNOWLEDGE_BASE_CREATE_FAILED(40002, "创建知识库失败"),
    KNOWLEDGE_BASE_UPDATE_FAILED(40003, "更新知识库失败"),
    KNOWLEDGE_BASE_DELETE_FAILED(40004, "删除知识库失败"),

    // 会话相关错误
    CHAT_SESSION_NOT_FOUND(50001, "会话不存在"),
    CHAT_SESSION_CREATE_FAILED(50002, "创建会话失败"),
    CHAT_SESSION_UPDATE_FAILED(50003, "更新会话失败"),
    CHAT_SESSION_DELETE_FAILED(50004, "删除会话失败"),

    // 消息相关错误
    CHAT_MESSAGE_SEND_FAILED(60001, "发送消息失败"),
    CHAT_MESSAGE_NOT_FOUND(60002, "消息不存在"),
    CHAT_MESSAGE_CREATE_FAILED(60003, "创建消息失败"),
    CHAT_MESSAGE_UPDATE_FAILED(60004, "更新消息失败"),
    CHAT_MESSAGE_DELETE_FAILED(60005, "删除消息失败"),
    CHAT_MESSAGE_APPEND_FAILED(60006, "追加消息内容失败"),

    // 智能体相关错误
    AGENT_NOT_FOUND(70001, "智能体不存在"),
    AGENT_CREATE_FAILED(70002, "创建智能体失败"),
    AGENT_UPDATE_FAILED(70003, "更新智能体失败"),
    AGENT_DELETE_FAILED(70004, "删除智能体失败");

    private final int code;
    private final String message;

    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return ERROR;
    }
}
