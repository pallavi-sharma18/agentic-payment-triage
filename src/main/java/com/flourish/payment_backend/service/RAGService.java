package com.flourish.payment_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RAGService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    @Value("classpath:visa.pdf")
    Resource visaPdfResource;

    public void ingestVectorStore(){

        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(visaPdfResource);
        List<Document> documents = pagePdfDocumentReader.read();

        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = tokenTextSplitter.apply(documents);

        vectorStore.add(chunks);
    }

}
