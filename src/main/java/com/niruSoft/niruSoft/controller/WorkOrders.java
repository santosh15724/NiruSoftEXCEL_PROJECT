package com.niruSoft.niruSoft.controller;

import com.niruSoft.niruSoft.model.PDFData;
import com.niruSoft.niruSoft.service.GenerateBillService;
import com.niruSoft.niruSoft.service.PDFGenerationService;
import com.niruSoft.niruSoft.utils.ExcelValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.niruSoft.niruSoft.utils.CommonUtils.formatDate;


@RestController
public class WorkOrders {

    private final ExcelValidator excelValidator;
    private final GenerateBillService generateBillService;

    private final PDFGenerationService pdfGenerationService;

    @Autowired
    public WorkOrders(ExcelValidator excelValidator, GenerateBillService generateBillService, PDFGenerationService pdfGenerationService) {
        this.excelValidator = excelValidator;
        this.generateBillService = generateBillService;
        this.pdfGenerationService = pdfGenerationService;
    }


    @PostMapping("/generate-pdf-zip")
    public ResponseEntity<InputStreamResource> generatePDFZip(@RequestParam("file") MultipartFile file) throws IOException, InterruptedException, ExecutionException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new InputStreamResource(new ByteArrayInputStream("No file uploaded".getBytes())));
        }
        boolean isValid = ExcelValidator.validateExcel(file.getInputStream());
        if (!isValid) {
            return ResponseEntity.badRequest().body(new InputStreamResource(new ByteArrayInputStream("Invalid Excel file".getBytes())));
        }
        JSONObject excelData = generateBillService.processExcelData(file.getInputStream());
        List<CompletableFuture<PDFData>> pdfFutures = new ArrayList<>();
        for (String farmerName : excelData.keySet()) {
            JSONObject farmerData = excelData.getJSONObject(farmerName);
            String jsonData = farmerData.toString();
            JSONArray dateArray = farmerData.getJSONArray("DATE");
            if (!dateArray.isEmpty()) {
                String date = formatDate(dateArray.getString(0));
                CompletableFuture<PDFData> pdfFuture = pdfGenerationService.generatePDFFromJSONAsync(jsonData, farmerName, date);
                pdfFutures.add(pdfFuture);
            }
        }

        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(pdfFutures.toArray(new CompletableFuture[0]));
            allOf.get();
            List<PDFData> pdfDataList = pdfFutures.stream().map(future -> {
                try {
                    return future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException("Failed to retrieve PDF data", e);
                }
            }).collect(Collectors.toList());
            byte[] zipBytes = createZipFile(pdfDataList);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "Farmer_Data.zip"); // Correct the filename here
            headers.setContentLength(zipBytes.length);
            InputStreamResource zipResource = new InputStreamResource(new ByteArrayInputStream(zipBytes));
            return ResponseEntity.ok().headers(headers).body(zipResource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new InputStreamResource(new ByteArrayInputStream("Error generating ZIP file".getBytes())));
        }
    }


    public static byte[] createZipFile(List<PDFData> pdfDataList) throws IOException {
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(zipOutputStream)) {
            for (PDFData pdfData : pdfDataList) {
                String pdfFileName = pdfData.fileName();
                byte[] pdfBytes = pdfData.pdfBytes();

                ZipEntry zipEntry = new ZipEntry(pdfFileName);
                zip.putNextEntry(zipEntry);
                zip.write(pdfBytes);
                zip.closeEntry();
            }
        }

        return zipOutputStream.toByteArray();
    }


}

