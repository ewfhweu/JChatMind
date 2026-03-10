package com.kama.jchatmind.service;

public interface DocumentProcessor {
    void process(String kbId, String documentId, String filePath);
    boolean supports(String fileType);
}