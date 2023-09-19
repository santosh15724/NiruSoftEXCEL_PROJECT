package com.niruSoft.niruSoft.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.niruSoft.niruSoft.model.PDFData;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public interface GenerateBillImpl {

    public JSONObject processExcelData(InputStream inputStream);


}
