package com.kama.jchatmind.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentProcessorFactory {

    private final List<DocumentProcessor> processors;

    public DocumentProcessorFactory(List<DocumentProcessor> processors) {
        this.processors = processors;
    }

    public DocumentProcessor getProcessor(String fileType) {
        for (DocumentProcessor processor : processors) {
            if (processor.supports(fileType)) {
                return processor;
            }
        }
        return null;
    }
}