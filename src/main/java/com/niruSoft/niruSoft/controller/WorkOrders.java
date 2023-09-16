package com.niruSoft.niruSoft.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.renderer.DocumentRenderer;
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

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.element.Table;
//import com.itextpdf.layout.border.SolidBorder;

import static com.niruSoft.niruSoft.utils.CommonUtils.formatDate;

//import com.syncfusion.pdf.*;
//import java.awt.Color;

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
    private static boolean isNumeric(String str) {
        try {
            new BigDecimal(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Async
    public CompletableFuture<PDFData> generatePDFFromJSONAsync(String jsonData, String farmerName, String date) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        JsonNode kgsumNode = jsonNode.get("KGSUM");
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

        JsonNode coolieNode = jsonNode.get("Coolie");
        JsonNode LuggageNode = jsonNode.get("Luggage");
        JsonNode SCNode = jsonNode.get("S.C");
//        JsonNode TOTALNode = subObjectData.get("TOTAL");
//        JsonNode EXPNode = subObjectData.get("EXP");
        JsonNode AmountNode = jsonNode.get("Amount");
        int Cooliesum = 0;
        for (JsonNode valueNode : coolieNode) {
            try {
                int value = Integer.parseInt(valueNode.asText());
                Cooliesum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
        int Luggagesum = 0;
        for (JsonNode valueNode : LuggageNode) {
            try {
                int value = Integer.parseInt(valueNode.asText());
                Luggagesum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
        int SCsum = 0;
        for (JsonNode valueNode : SCNode) {
            try {
                int value = Integer.parseInt(valueNode.asText());
                SCsum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
        int Amountsum = 0;
        for (JsonNode valueNode : AmountNode) {
            try {
                int value = Integer.parseInt(valueNode.asText());
                Amountsum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
//        System.out.println(Cooliesum);
        String coolieSumAsString = String.valueOf(Cooliesum);
        String LuggagesumAsString = String.valueOf(Luggagesum);
        String SCsumAsString = String.valueOf(SCsum);
        String AmountsumAsString = String.valueOf(Amountsum);
        double expToal = Cooliesum + Luggagesum + SCsum;
        String expToalAsString = String.valueOf(expToal);

        double total = Amountsum - expToal;
        String totalAsString = String.valueOf(total);

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

                    if ("0".equals(rateKey)) {
                        JsonNode arrayToCalculate = bagsumNode.get(rateKey);
                        if (arrayToCalculate != null && arrayToCalculate.isArray() && arrayToCalculate.size() > 0) {
                            // If rate is 0, set it to the first value in the associated array
                            JsonNode firstValue = arrayToCalculate.get(0);
                            if (firstValue.isTextual()) {
                                // Check if the first value is a text (non-numeric)
                                rateString = firstValue.asText();
                                rate = BigDecimal.ZERO; // Set rate to 0
                            } else if (firstValue.isNumber()) {
                                rate = new BigDecimal(firstValue.asText());
                                rateString = "Rate: " + rate;
                            } else {
                                // Handle other cases as needed
                                rate = BigDecimal.ZERO;
                                rateString = "Rate: 0";
                            }
                        } else {
                            // Handle the case when there's no valid value in the associated array
                            rate = BigDecimal.ZERO;
                            rateString = "Rate: 0";
                        }
                    } else {
                        // For other rateKey values, parse the rateKey to a BigDecimal
                        rate = new BigDecimal(rateKey);
                        rateString = "Rate: " + rate;
                    }
                    JsonNode arrayToCalculate = bagsumNode.get(rateKey);

                    if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                        // Initialize variables to store amount and brief sum for the current rate
                        BigDecimal amount = BigDecimal.ZERO;
                        BigDecimal briefSum = BigDecimal.ZERO;

                        // Iterate through the values in the array and calculate the amount and brief sum
                        for (JsonNode value : arrayToCalculate) {
                            String stringValue = value.asText();
                            BigDecimal numericValue;
                            if (isNumeric(stringValue)) {
                                numericValue = new BigDecimal(stringValue);
                                amount = amount.add(numericValue.multiply(rate));
                                briefSum = briefSum.add(numericValue);
                            } else {
                                // Handle non-numeric value (e.g., skip or set to a default)
                                numericValue = BigDecimal.ZERO; // For example, set to zero
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

//                    int minNumberOfRows = 9;
//                    int emptyRowsNeeded = Math.max(minNumberOfRows - bagsumDetailsList.size(), 0);
                    int minNumberOfRows = 9;
                    int bagsumDetailsRowCount = bagsumDetailsList.size();
                    int kgsumNodeRowCount = calculateKgsumNodeRowCount(kgsumNode); // Implement a function to calculate rows from kgsumNode
                    int totalRowCount = bagsumDetailsRowCount + kgsumNodeRowCount;
                    int emptyRowsNeeded = Math.max(minNumberOfRows - totalRowCount, 0);


                    Color whiteColor = new DeviceRgb(255, 255, 255);
                    Color blackColor = new DeviceRgb(0, 0, 0);
                    int serialNumber = 1;

                    Table dataTable = new Table(new float[]{100f, 350f, 70f, 80f});
                    SolidBorder whiteSolidBorder = new SolidBorder(1f);
                    whiteSolidBorder.setColor(whiteColor);
                    dataTable.setBorderBottom(whiteSolidBorder);
                    dataTable.addCell("SL NO");
                    dataTable.addCell("Brief");
                    dataTable.addCell("Rate");
                    dataTable.addCell("Amount");
                    for (Map<String, BigDecimal> bagsumDetails : bagsumDetailsList) {
                        String brief = bagsumDetails.get("Brief").toString();
                        String rate = bagsumDetails.get("Rate").toString();
                        String amount = bagsumDetails.get("Amount").toString();

                        Cell slNoCell = new Cell().add(new Paragraph(String.format("%-2d", serialNumber)));
                        slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                        Cell briefCell = new Cell().add(new Paragraph(brief));
                        briefCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                        Cell rateCell = new Cell().add(new Paragraph(rate));
                        rateCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                        rateCell.setTextAlignment(TextAlignment.CENTER);

                        Cell amountCell = new Cell().add(new Paragraph(amount));
                        amountCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                        amountCell.setTextAlignment(TextAlignment.CENTER);

                        dataTable.addCell(slNoCell);
                        dataTable.addCell(briefCell);
                        dataTable.addCell(rateCell);
                        dataTable.addCell(amountCell);

                        serialNumber++;
                    }

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
                                // Create a cell for SL NO with a white border
                                Cell slNoCell = new Cell();
                                slNoCell.add(new Paragraph(String.format("%-2d", serialNumber)));
                                slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                                // Create a cell for the Brief with a white border
                                Cell briefCell = new Cell();
                                String str = "";
                                for (int i = row * 4; i < (row + 1) * 4 && i < briefValues.size(); i++) {
                                    str += briefValues.get(i) + " ";
                                }
                                briefCell.add(new Paragraph(str));
                                briefCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                                // Create a cell for the Rate with a white border
                                Cell rateCell = new Cell();
                                String rateValue = "0".equals(rateKey) ? String.join(" ", briefValues) : rateKey;
                                rateCell.add(new Paragraph(rateValue).setTextAlignment(TextAlignment.CENTER));
                                rateCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                                // Create a cell for the Amount with a white border
                                Cell amountCell = new Cell();
                                double totalAmountByRate = 0;
                                if (!"0".equals(rateKey)) {
                                    totalAmountByRate = briefValues.subList(row * 4, Math.min((row + 1) * 4, briefValues.size()))
                                            .stream()
                                            .mapToDouble(Double::parseDouble)
                                            .sum();
                                }
                                amountCell.add(new Paragraph(String.valueOf(totalAmountByRate * Double.parseDouble(rateKey)))
                                        .setTextAlignment(TextAlignment.CENTER));
                                amountCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                                dataTable.addCell(slNoCell);
                                dataTable.addCell(briefCell);
                                dataTable.addCell(rateCell);
                                dataTable.addCell(amountCell);

                                serialNumber++;
                            }
                        }
                    }
                    // Add empty rows to dataTable
                    for (int i = 0; i < emptyRowsNeeded; i++) {
                        for (int j = 0; j < 4; j++) { // Add 4 empty cells per row
                            Cell emptyCell = new Cell()
                                    .add(new Paragraph(""))
                                    .setPadding(9) // Set padding to 5
                                    .setBorderBottom(new SolidBorder(whiteColor, 1f)); // Set white bottom border
                            dataTable.addCell(emptyCell);
                        }
                    }
                    document.add(dataTable);
                    LineSeparator line = new LineSeparator(new SolidLine());
                    line.setMarginTop(-2f);
                    document.add(line);


                    // Define the border color and width
                    Color borderColor = new DeviceRgb(0, 0, 0); // Replace with your desired color
                    float borderWidth = 1f;

                    Div contentDiv = new Div()
                            .setMarginTop(5)
                            .setBorder(new SolidBorder(borderColor, borderWidth));

                    Table expTable = new Table(UnitValue.createPercentArray(new float[]{70, 30}));

                    Cell headerCell = new Cell().add(new Paragraph("    EXP").setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE));
                    headerCell.setBorderRight(new SolidBorder(whiteColor, 1f));
                    expTable.addCell(headerCell);

                    headerCell = new Cell().add(new Paragraph().setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE));
                    expTable.addCell(headerCell);
                    expTable.addCell(new Cell().add(new Paragraph("Coolie").setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(coolieSumAsString).setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph("Luggage").setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(LuggagesumAsString).setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph("S.CASH").setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(SCsumAsString).setTextAlignment(TextAlignment.CENTER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    Paragraph tosta = new Paragraph()
                            .add(new Text(expToalAsString)).add("\n");
                    contentDiv.add(expTable);
                    contentDiv.add(tosta);

                    Paragraph valuesParagraphs2 = new Paragraph()
                            .setFontSize(14)
                            .setMarginLeft(90)
                            .setFontSize(14)
                            .setMarginTop(-90)
                            .add(new Text("Amount- "))
                            .add(new Text(AmountsumAsString)).add("\n")
                            .add(new Text("EXP- "))
                            .add(new Text(expToalAsString)).add("\n")
                            .add(new Text("TOTAL - "))
                            .add(new Text(totalAsString)).add("\n")
                            .setTextAlignment(TextAlignment.RIGHT);
                    contentDiv.add(valuesParagraphs2);

                    document.add(contentDiv);


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

    private static int calculateKgsumNodeRowCount(JsonNode kgsumNode) {
        int rowCount = 0;

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
                rowCount += numRows;
            }
        }

        return rowCount;
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

