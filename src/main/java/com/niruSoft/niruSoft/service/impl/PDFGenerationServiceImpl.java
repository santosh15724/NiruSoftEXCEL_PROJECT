package com.niruSoft.niruSoft.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.niruSoft.niruSoft.model.PDFData;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;

public interface PDFGenerationServiceImpl {

    public CompletableFuture<PDFData> generatePDFFromJSONAsync(String jsonData, String farmerName, String date) throws JsonProcessingException;
}
