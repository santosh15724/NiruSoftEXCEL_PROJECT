package com.niruSoft.niruSoft.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.niruSoft.niruSoft.model.PDFData;
import com.niruSoft.niruSoft.service.GenerateBillService;
import com.niruSoft.niruSoft.utils.ExcelValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.niruSoft.niruSoft.utils.CommonUtils.formatDate;

@RestController
public class WorkOrders {

    @Autowired
    private final ExcelValidator excelValidator;

    @Autowired
    private final GenerateBillService generateBillService;

    public WorkOrders(ExcelValidator excelValidator, GenerateBillService generateBillService) {
        this.excelValidator = excelValidator;
        this.generateBillService = generateBillService;
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
                CompletableFuture<PDFData> pdfFuture = generatePDFFromJSONAsync(jsonData, farmerName, date);
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

    @Async
    public CompletableFuture<PDFData> generatePDFFromJSONAsync(String jsonData, String farmerName, String date) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        System.out.println(jsonNode);

        JsonNode itemsNode = jsonNode.get("ITEM");
        StringBuilder itemsText = new StringBuilder();
        if (itemsNode != null && itemsNode.isArray()) {
            boolean firstItem = true; // To keep track of the first item
            for (JsonNode item : itemsNode) {
                String itemValue = item.asText();
                if (!itemValue.isEmpty()) {
                    if (!firstItem) {
                        itemsText.append(", "); // Add a comma and space for subsequent items
                    }
                    itemsText.append(itemValue);
                    firstItem = false;
                }
            }
        }
        List<Map<String, BigDecimal>> bagsumDetailsList = new ArrayList<>();
        try {
            // Get the "BAGSUM" object
            JsonNode bagsumNode = jsonNode.get("BAGSUM");

            if (bagsumNode != null && bagsumNode.isObject()) {
                int numRates = bagsumNode.size(); // Number of rates

                int index = 0; // Index to keep track of the current position in arrays

                // Iterate through all keys within the "BAGSUM" object
                for (Iterator<String> it = bagsumNode.fieldNames(); it.hasNext(); ) {
                    String rateKey = it.next();
                    BigDecimal rate;
                    String rateString = "Rate: " + rateKey; // Default rate string

                    if (!"NO SALE".equals(rateKey)) {

                        rate = new BigDecimal(rateKey);
                    } else {
                        rate = BigDecimal.ZERO; // Set rate to BigDecimal.ZERO for "NO SALE"
                        rateString = "Rate: NO SALE"; // Hard-coded "Rate: NO SALE" for the output
                    }

                    JsonNode arrayToCalculate = bagsumNode.get(rateKey);

                    if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                        // Initialize variables to store amount and brief sum for the current rate
                        BigDecimal amount = BigDecimal.ZERO;
                        BigDecimal briefSum = BigDecimal.ZERO;

                        // Iterate through the values in the array and calculate the amount and brief sum
                        for (JsonNode value : arrayToCalculate) {
                            String stringValue = value.asText();

                            // Check if the value is "NO SALE" and skip it
                            if (!"NO SALE".equals(stringValue)) {
                                BigDecimal numericValue = new BigDecimal(stringValue);
                                amount = amount.add(numericValue.multiply(rate));
                                briefSum = briefSum.add(numericValue);
                            }
                        }

                        Map<String, BigDecimal> rateDetails = new HashMap<>();
                        rateDetails.put("Brief", briefSum);
                        rateDetails.put("Rate", rate);
                        rateDetails.put("Amount", amount);


                        // Add the rate details to the list
                        bagsumDetailsList.add(rateDetails);


//                                            System.out.println(rateString + ", Brief: " + briefSum + ", Amount: " + amount);
//
//                                            System.out.println("Brief: " + rateString);
//                                            System.out.println("Rate: " + rate);
//                                            System.out.println("Amount: " + amount);

                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        try {
            return CompletableFuture.supplyAsync(() -> {
                try (ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream(); PdfWriter pdfWriter = new PdfWriter(pdfOutputStream); PdfDocument pdfDocument = new PdfDocument(pdfWriter)) {
                    PageSize a5PageSize = new PageSize(PageSize.A5);
                    Document document = new Document(pdfDocument, a5PageSize);
//                    pdfDocument.getDocumentInfo().setTitle("Empty PDF");
                    ClassPathResource imageResource = new ClassPathResource("Image/SKTRADER.jpg");
                    ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
                    Image image = new Image(imageData);

                    float imageWidth = a5PageSize.getWidth() * 0.92f;
                    float imageHeight = (imageWidth * (image.getImageHeight() / image.getImageWidth())) + 5;
                    image.setWidth(imageWidth);
                    image.setHeight(imageHeight);
                    float leftMargin = 18;
                    float topMargin = 10;
                    image.setFixedPosition(leftMargin, pdfDocument.getDefaultPageSize().getTop() - imageHeight - topMargin);
                    document.add(image);

                    Paragraph nameAndDate = new Paragraph();
                    nameAndDate.setMarginTop(imageHeight);

                    Text msText = new Text("M/s : ");
                    Text farmerNameText = new Text(farmerName);
                    Text dateText = new Text("Date : ");
                    Text dateValueText = new Text(date);


                    nameAndDate.add(msText);
                    nameAndDate.add(farmerNameText);
                    nameAndDate.add(new Tab());
                    nameAndDate.add(new Tab());
                    nameAndDate.add(new Tab());
                    nameAndDate.add(dateText);
                    nameAndDate.add(dateValueText);
                    document.add(nameAndDate);

                    Paragraph particulars = new Paragraph();
                    Text msTextP = new Text("Particulars : ");
                    Text farmerNameTextP = new Text(itemsText.toString());
                    particulars.add(msTextP);
                    particulars.add(farmerNameTextP);
                    document.add(particulars);


                    float[] columnWidths = {100f, 350f, 70f, 80f};
                    int serialNumber = 1;
                    Table headerTable = new Table(columnWidths);
                    headerTable.setFontSize(11);
                    headerTable.addCell("SL NO");
                    headerTable.addCell("Brief");
                    headerTable.addCell("Rate");
                    headerTable.addCell("Amount");
                    document.add(headerTable);

                    for (Map<String, BigDecimal> bagsumDetails : bagsumDetailsList) {
                        String brief = bagsumDetails.get("Brief").toString();
                        String rate = bagsumDetails.get("Rate").toString();
                        String amount = bagsumDetails.get("Amount").toString();
                        Table dataTable = new Table(new float[]{100f, 350f, 70f, 80f});
//                        dataTable.setTextAlignment(TextAlignment.LEFT);
                        dataTable.setFontSize(11);

                        dataTable.addCell(String.format("%-2d", serialNumber));
                        dataTable.addCell(brief);
                        Paragraph rateParagraph = new Paragraph(rate);
                        rateParagraph.setTextAlignment(TextAlignment.CENTER);
                        dataTable.addCell(rateParagraph);
                        Paragraph amountParagraph = new Paragraph(amount);
                        amountParagraph.setTextAlignment(TextAlignment.CENTER);
                        dataTable.addCell(amountParagraph);
                        document.add(dataTable);


                        serialNumber++;
                    }


                    JsonNode kgsumNode = jsonNode.get("KGSUM");

                    Iterator<Map.Entry<String, JsonNode>> fieldIterator = kgsumNode.fields();
                    while (fieldIterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fieldIterator.next();
                        String rateKey = entry.getKey();
                        JsonNode arrayToCalculate = entry.getValue();

                        if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                            List<String> briefValues = new ArrayList<>();

                            for (JsonNode value : arrayToCalculate) {
                                String stringValue = value.asText();
                                briefValues.add(stringValue);
                            }

                            int numRows = (int) Math.ceil(briefValues.size() / 4.0);

                            for (int row = 0; row < numRows; row++) {
                                Table dataTable = new Table(new float[]{100f, 350f, 70f, 80f});
                                dataTable.setFontSize(11);
                                dataTable.addCell(String.format("%-2d", serialNumber));

                                String str = "";
                                for (int i = row * 4; i < (row + 1) * 4 && i < briefValues.size(); i++) {
                                    str += briefValues.get(i) + " " + " ";

                                }
                                dataTable.addCell(str);

                                String rateValue = "0".equals(rateKey) ? String.join(" ", briefValues) : rateKey;
                                Paragraph rateParagraph = new Paragraph(rateValue);
                                rateParagraph.setTextAlignment(TextAlignment.CENTER);
                                rateParagraph.setFontSize(8);
                                dataTable.addCell(rateParagraph);

                                double totalAmountByRate = 0;
                                if (!"0".equals(rateKey)) {
                                    totalAmountByRate = briefValues.subList(row * 4, Math.min((row + 1) * 4, briefValues.size())).stream().mapToDouble(Double::parseDouble).sum();
                                }
                                Paragraph amountParagraph = new Paragraph(String.valueOf(totalAmountByRate * Double.parseDouble(rateKey)));
                                amountParagraph.setTextAlignment(TextAlignment.CENTER);
                                dataTable.addCell(amountParagraph);

                                document.add(dataTable);

                                serialNumber++;
                            }
                        }
                    }

                    document.close();

                    String pdfFileName = farmerName + " - " + date + ".pdf";
                    byte[] pdfBytes = pdfOutputStream.toByteArray();

                    return new PDFData(pdfFileName, pdfBytes);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to generate PDF", e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF asynchronously", e);
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

