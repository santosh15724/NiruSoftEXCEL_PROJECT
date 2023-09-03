package com.niruSoft.niruSoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.FontConstants;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TabAlignment;
import com.niruSoft.niruSoft.model.SubObjectData;
import com.niruSoft.niruSoft.service.impl.GenerateBillImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;


@Slf4j
@Service
public class GenerateBillService implements GenerateBillImpl {

    private static final int SCALE = 4;

    @Override

    public JSONObject processExcelData(InputStream inputStream) {
        Map<String, List<Map<String, String>>> resultMap = new HashMap<>();
        Set<String> itemsWithQty = new HashSet<>();
        Set<String> seenItems = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() <= 1) {
                System.out.println("No data or only header row found.");
                return new JSONObject(resultMap); // Convert resultMap to JSONObject and return it
            }

            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndexes = findColumnIndexes(headerRow);

            int farmerNameColumnIndex = columnIndexes.getOrDefault("FARMERNAME", -1);
            int itemQtyColumnIndex = columnIndexes.getOrDefault("ITEMQTY", -1);
            int itemColumnIndex = columnIndexes.getOrDefault("ITEM", -1);

            // Loop through the rows and extract data for all farmer names
            for (int rowIndex = 1; rowIndex < sheet.getPhysicalNumberOfRows(); rowIndex++) {
                Row dataRow = sheet.getRow(rowIndex);
                Cell farmerNameCell = dataRow.getCell(farmerNameColumnIndex);

                if (farmerNameCell != null) {
                    String farmerName = getCellValueAsString(farmerNameCell, formulaEvaluator);
                    String itemQty = itemQtyColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(itemQtyColumnIndex), formulaEvaluator) : "";
                    String item = itemColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(itemColumnIndex), formulaEvaluator) : "";

                    Map<String, String> dataMap = new HashMap<>();
                    IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                            .forEach(cellIndex -> {
                                Cell dataCell = dataRow.getCell(cellIndex);
                                String cellValue = getCellValueAsString(dataCell, formulaEvaluator);
                                dataMap.put(headerRow.getCell(cellIndex).getStringCellValue(), cellValue);
                            });

                    if (!itemQty.isEmpty() || !item.isEmpty()) {
                        if (!itemQty.isEmpty() && !item.isEmpty()) {
                            // Concatenate "Item qty" and "ITEM" with a space
                            dataMap.put("ITEM", itemQty + " " + item);
                        } else if (itemQty.isEmpty()) {
                            // If "Item qty" is empty, combine values under "ITEM"
                            itemsWithQty.add(item);
                        }
                    }

                    if (itemQty.isEmpty() && seenItems.contains(item)) {
                        dataMap.put("ITEM", ""); // Set "ITEM" to empty
                    } else {
                        seenItems.add(item);
                    }

                    resultMap.computeIfAbsent(farmerName, k -> new ArrayList<>()).add(dataMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Convert the original resultMap to a JSONObject
        JSONObject originalJson = new JSONObject(resultMap);

        // Use the modifyJsonStructure function to restructure the JSON
        JSONObject modifiedJson = modifyJsonStructure(originalJson);
        JSONObject modifyJsonWithSum = modifyJsonStructureWithSum(originalJson);
        // Merge the two JSON objects
        for (String farmerName : modifyJsonWithSum.keySet()) {
            if (modifiedJson.has(farmerName)) {
                JSONObject farmerData = modifiedJson.getJSONObject(farmerName);
                JSONObject farmerDataWithSum = modifyJsonWithSum.getJSONObject(farmerName);

                // Merge "BAGSUM" and "KGSUM" into the existing farmerData
                farmerData.put("BAGSUM", farmerDataWithSum.get("BAGSUM"));
                farmerData.put("KGSUM", farmerDataWithSum.get("KGSUM"));
                farmerData.put("S.C", farmerDataWithSum.get("S.C"));
                farmerData.put("Luggage", farmerDataWithSum.get("Luggage"));
                farmerData.put("Coolie", farmerDataWithSum.get("Coolie"));
                farmerData.put("Amount", farmerDataWithSum.get("Amount"));
                farmerData.put("EXP", farmerDataWithSum.get("EXP"));
                farmerData.put("TOTAL", farmerDataWithSum.get("TOTAL"));//TOTAL

            }
        }

        // Remove empty strings from the "ITEM" arrays
        for (String farmerName : modifiedJson.keySet()) {
            JSONArray itemArray = modifiedJson.getJSONObject(farmerName).getJSONArray("ITEM");
            JSONArray filteredItemArray = new JSONArray();
            for (int i = 0; i < itemArray.length(); i++) {
                String itemValue = itemArray.getString(i);
                if (!itemValue.isEmpty()) {
                    filteredItemArray.put(itemValue);
                }
            }
            modifiedJson.getJSONObject(farmerName).put("ITEM", filteredItemArray);
        }

        return modifiedJson; // Return the modified JSON
    }

    private Map<String, Integer> findColumnIndexes(Row headerRow) {
        Map<String, Integer> columnIndexes = new HashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getPhysicalNumberOfCells(); cellIndex++) {
            String header = headerRow.getCell(cellIndex).getStringCellValue().replace(" ", "").toUpperCase();
            columnIndexes.put(header, cellIndex);
        }
        return columnIndexes;
    }


    private String getCellValueAsString(Cell cell, FormulaEvaluator formulaEvaluator) {
        if (cell != null) {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        // Use BigDecimal for numeric values
                        BigDecimal numericValue = new BigDecimal(cell.getNumericCellValue());
                        return numericValue.toString();
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    CellValue cellValue = formulaEvaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
                            // Use BigDecimal for numeric values in formulas
                            BigDecimal formulaNumericValue = new BigDecimal(cellValue.getNumberValue());
                            return formulaNumericValue.toString();
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                    }
                default:
                    return "";
            }
        }
        return "";
    }

    private JSONObject modifyJsonStructure(JSONObject originalJson) {
        JSONObject modifiedJson = new JSONObject();

        for (String farmerName : originalJson.keySet()) {
            JSONArray farmerDataArray = originalJson.getJSONArray(farmerName);
            JSONObject farmerDataObject = new JSONObject();

            for (int i = 0; i < farmerDataArray.length(); i++) {
                JSONObject rowData = farmerDataArray.getJSONObject(i);

                for (String header : rowData.keySet()) {
                    if (!farmerDataObject.has(header)) {
                        farmerDataObject.put(header, new JSONArray());
                    }
                    JSONArray headerArray = farmerDataObject.getJSONArray(header);
                    headerArray.put(rowData.getString(header));
                }
            }

            modifiedJson.put(farmerName, farmerDataObject);
        }

        return modifiedJson;
    }

    private JSONObject modifyJsonStructureWithSum(JSONObject originalJson) {
        JSONObject modifiedJson = new JSONObject();

        for (String farmerName : originalJson.keySet()) {
            JSONArray farmerDataArray = originalJson.getJSONArray(farmerName);
            JSONObject farmerDataObject = new JSONObject();

            // Initialize BagSum, KgSum, and other sums
            JSONObject bagSum = new JSONObject();
            JSONObject kgSum = new JSONObject();
            BigDecimal scTotal = BigDecimal.ZERO;
            BigDecimal coolieTotal = BigDecimal.ZERO;
            BigDecimal luggageTotal = BigDecimal.ZERO;
            BigDecimal amountTotal = BigDecimal.ZERO; // Initialize Amount total

            for (int i = 0; i < farmerDataArray.length(); i++) {
                JSONObject rowData = farmerDataArray.getJSONObject(i);

                // Extract relevant data
                String rate = rowData.getString("Rate");
                String qty = rowData.getString("QTY");
                String sc = rowData.getString("S.C");
                String coolie = rowData.getString("Coolie");
                String luggage = rowData.getString("Luggage");
                String amount = rowData.getString("Amount"); // Amount value

                if (!rate.isEmpty() && !qty.isEmpty()) {
                    double rateValue = Double.parseDouble(rate);
                    double qtyValue = Double.parseDouble(qty);

                    if (rateValue == 0.0) {
                        // Append "NO SALE" to BAGSUM when rate is 0
                        if (!bagSum.has(rate)) {
                            bagSum.put(rate, new JSONArray());
                        }
                        bagSum.getJSONArray(rate).put("NO SALE");
                    } else {
                        // Determine whether to use BagSum or KgSum based on Rate
                        String sumKey = rateValue >= 100.0 ? "BAGSUM" : "KGSUM";
                        // Add the quantity to the appropriate sum
                        JSONObject sumObject = sumKey.equals("BAGSUM") ? bagSum : kgSum;
                        if (!sumObject.has(rate)) {
                            sumObject.put(rate, new JSONArray());
                        }
                        sumObject.getJSONArray(rate).put(String.valueOf(qtyValue));

                    }
                }

                // Add S.C value to the total as BigDecimal
                if (!sc.isEmpty()) {
                    BigDecimal scValue = new BigDecimal(sc);
                    scTotal = scTotal.add(scValue);
                }

                // Add Coolie value to the total as BigDecimal
                if (!coolie.isEmpty()) {
                    BigDecimal coolieValue = new BigDecimal(coolie);
                    coolieTotal = coolieTotal.add(coolieValue);
                }

                // Add Luggage value to the total as BigDecimal
                if (!luggage.isEmpty()) {
                    BigDecimal luggageValue = new BigDecimal(luggage);
                    luggageTotal = luggageTotal.add(luggageValue);
                }

                // Add Amount value to the total as BigDecimal
                if (!amount.isEmpty()) {
                    BigDecimal amountValue = new BigDecimal(amount);
                    amountTotal = amountTotal.add(amountValue);
                }

                // Add other data to the farmerDataObject
                for (String header : rowData.keySet()) {
                    if (!header.equals("QTY")) {
                        if (!farmerDataObject.has(header)) {
                            farmerDataObject.put(header, new JSONArray());
                        }
                        JSONArray headerArray = farmerDataObject.getJSONArray(header);
                        headerArray.put(rowData.getString(header));
                    }
                }
            }

            // Add BagSum and KgSum to the farmerDataObject
            farmerDataObject.put("BAGSUM", bagSum);
            farmerDataObject.put("KGSUM", kgSum);

            // Add S.CTOTAL to the farmerDataObject as a rounded integer
            int scTotalRounded = scTotal.setScale(0, RoundingMode.HALF_UP).intValue();
            farmerDataObject.put("S.C", String.valueOf(scTotalRounded));

            // Add CoolieTOTAL to the farmerDataObject as a rounded integer
            int coolieTotalRounded = coolieTotal.setScale(0, RoundingMode.HALF_UP).intValue();
            farmerDataObject.put("Coolie", String.valueOf(coolieTotalRounded));

            // Add LuggageTOTAL to the farmerDataObject as a rounded integer
            int luggageTotalRounded = luggageTotal.setScale(0, RoundingMode.HALF_UP).intValue();
            farmerDataObject.put("Luggage", String.valueOf(luggageTotalRounded));

            // Add AmountTOTAL to the farmerDataObject as a rounded integer
            int amountTotalRounded = amountTotal.setScale(0, RoundingMode.HALF_UP).intValue();
            farmerDataObject.put("Amount", String.valueOf(amountTotalRounded));

            // Calculate EXP sum as the sum of S.CTOTAL, CoolieTOTAL, and LuggageTOTAL
            BigDecimal expValue = scTotal.add(coolieTotal).add(luggageTotal);
            int expSumRounded = expValue.setScale(0, RoundingMode.HALF_UP).intValue();

            // Add EXP to the farmerDataObject
            farmerDataObject.put("EXP", String.valueOf(expSumRounded));

            BigDecimal TotalValue = amountTotal.subtract(expValue);
            int totalValueExp = TotalValue.setScale(0, RoundingMode.HALF_UP).intValue();
            farmerDataObject.put("TOTAL", String.valueOf(totalValueExp));

            // Add the modified farmerDataObject to the modifiedJson
            modifiedJson.put(farmerName, farmerDataObject);
        }

        return modifiedJson;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
//    public List<byte[]> generatePdfFromJson(String jsonData) {
//        List<byte[]> pdfs = new ArrayList<>();
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            JsonNode jsonNode = objectMapper.readTree(jsonData);
//            System.out.println(jsonNode);
//
//            if (jsonNode.isObject()) {
//                // Iterate over each sub-object
//                jsonNode.fields().forEachRemaining(entry -> {
//                    String subObjectName = entry.getKey();
//                    JsonNode subObjectData = entry.getValue();
//
//                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//
//                    PdfWriter pdfWriter = new PdfWriter(outputStream);
//                    PdfDocument pdfDocument = new PdfDocument(pdfWriter);
//
//                    pdfDocument.addNewPage();
//
//                    // Create a document
//                    try (Document document = new Document(pdfDocument)) {
//                        // Customize content based on subObjectData
//                        document.add(new Paragraph("Sub-Object Name: " + subObjectName));
//
//                        // Add more content based on the sub-object's data
//                        // You can extract data from subObjectData here
//                    }
//
//                    pdfDocument.close(); // Close the PdfDocument
//                    byte[] pdfBytes = outputStream.toByteArray();
//                    pdfs.add(pdfBytes);
//                });
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return pdfs;
//    }
//    public List<byte[]> generatePdfFromJson(String jsonData) {
//        List<byte[]> pdfs = new ArrayList<>();
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            JsonNode jsonNode = objectMapper.readTree(jsonData);
//
//            if (jsonNode.isObject()) {
//                // Iterate over each sub-object
//                jsonNode.fields().forEachRemaining(entry -> {
//                    String subObjectName = entry.getKey();
//                    JsonNode subObjectData = entry.getValue();
//
//                    try {
//                        // Deserialize JSON data into a SubObjectData object
//                        SubObjectData subObject = objectMapper.readValue(subObjectData.toString(), SubObjectData.class);
//
//                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                        PdfWriter pdfWriter = new PdfWriter(outputStream);
//
//                        // Set A3 page size here
////                        PdfDocument pdfDocument = new PdfDocument(pdfWriter, PageSize.A3);
//                        PdfDocument pdfDocument = new PdfDocument(pdfWriter);
//                        pdfDocument.setDefaultPageSize(PageSize.A3);
//                        pdfDocument.addNewPage();
//
//                        // Create a document
//                        try (Document document = new Document(pdfDocument)) {
//                            // Header Section
////                            createHeader(document, subObjectName, subObject);
//
//                            // Body Section
////                            createBody(document, subObject);
//
//                            // Footer Section
////                            createFooter(document, subObject);
//                        }
//
//                        pdfDocument.close(); // Close the PdfDocument
//                        byte[] pdfBytes = outputStream.toByteArray();
//                        pdfs.add(pdfBytes);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                });
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return pdfs;
//    }
    public List<byte[]> generatePdfFromJson(String jsonData) {
        List<byte[]> pdfs = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            System.out.println(jsonNode);

            if (jsonNode.isObject()) {
                // Iterate over each sub-object
                jsonNode.fields().forEachRemaining(entry -> {
                    String subObjectName = entry.getKey();
                    JsonNode subObjectData = entry.getValue();

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    try {
                        PdfWriter pdfWriter = new PdfWriter(outputStream);
                        PdfDocument pdfDocument = new PdfDocument(pdfWriter);
                        PageSize a3PageSize = PageSize.A3;
                        pdfDocument.setDefaultPageSize(a3PageSize);
                        pdfDocument.addNewPage();

                        // Create a document
                        try (Document document = new Document(pdfDocument)) {
                            // Add header section
                            addHeader(document, subObjectName, subObjectData);

                            // Add body section
                            addBody(document, subObjectName, subObjectData);

                            // Add footer section
//                            addFooter(document, subObjectName, subObjectData);
                        }

                        pdfDocument.close(); // Close the PdfDocument
                        byte[] pdfBytes = outputStream.toByteArray();
                        pdfs.add(pdfBytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pdfs;
    }

    private void addHeader(Document document, String subObjectName, JsonNode subObjectData) throws IOException {
        String date = null;
        List<String> dateList = new ArrayList<>();
        JsonNode dateNode = subObjectData.get("DATE");
        if (dateNode != null && dateNode.isArray()) {
            for (JsonNode dateValue : dateNode) {
                date = formatDate(dateValue.asText());
            }
        }
        JsonNode dateNode2 = subObjectData.get("ITEM");

        if (dateNode2 != null && dateNode2.isArray()) {
            for (JsonNode dateValue : dateNode2) {
                String value = dateValue.asText();
                dateList.add(value);
            }
        }

        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
        Image image = new Image(imageData);

        float imageWidth = PageSize.A3.getWidth() * 0.94f; // Use A3 size directly
        image.setWidth(imageWidth);

        document.add(image);

        Paragraph paragraph = new Paragraph()
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                .setFontSize(70)
                .setMarginTop(30) // Add some top margin for spacing
                .setWidth(imageWidth)
                .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);

        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
        paragraph.addTabStops(tabStop);

        StringBuilder largeSpace = new StringBuilder();
        for (int i = 0; i < 70; i++) { // Add 100 spaces for a large space
            largeSpace.append(" ");
        }

        Paragraph headerParagraph = new Paragraph()
                .setMarginLeft(40)
                .add(new Text("M/s: ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(19))
                .add(new Text("      ")
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(18))
                .add(new Text(subObjectName + largeSpace.toString()) // Add the subObjectName with a large space
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(20))
                .add(new Text("DATE:").setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(18))
                .add(new Text("    ")
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(17))
                .add(new Text(date)
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                        .setFontSize(18));

        System.out.println(dateList);
        String datesOfparticuler = String.join(",   ", dateList);


        Paragraph headerParagraph2 = new Paragraph()
                .setMarginRight(17)
                .add(new Text("Particular: ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(19))
                .add(new Text("    ")
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(18))
                .add(new Text(datesOfparticuler)
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(18));

        document.add(paragraph);
        document.add(headerParagraph);
        document.add(headerParagraph2);
    }

//    private void  addBody(Document document, String subObjectName, JsonNode subObjectData){
//
//        try {
//            // Get the "BAGSUM" object
//            JsonNode bagsumNode = subObjectData.get("BAGSUM");
//
//            if (bagsumNode != null && bagsumNode.isObject()) {
//                // Initialize variables to store sum, rate, and amount
//                double sum = 0.0;
//                double rate = 0.0;
//
//                // Iterate through all keys within the "BAGSUM" object
//                for (Iterator<String> it = bagsumNode.fieldNames(); it.hasNext();) {
//                    String key = it.next();
//                    JsonNode arrayToCalculate = bagsumNode.get(key);
//
//                    if (arrayToCalculate != null && arrayToCalculate.isArray()) {
//                        // Iterate through the values in the array and calculate the sum
//                        for (JsonNode value : arrayToCalculate) {
//                            String stringValue = value.asText();
//                            double numericValue = Double.parseDouble(stringValue);
//                            sum += numericValue;
//                        }
//                    }
//                }
//
//                // Get the "Rate" and "Amount" from the subObjectData
//                JsonNode rateNode = subObjectData.get("Rate");
//                JsonNode amountNode = subObjectData.get("Amount");
//
//                if (rateNode != null && rateNode.isArray() && rateNode.size() > 0) {
//                    rate = Double.parseDouble(rateNode.get(0).asText());
//                }
//
//                if (amountNode != null && !amountNode.isMissingNode()) {
//                    double amount = Double.parseDouble(amountNode.asText());
//
//                    // Print the desired output
//                    System.out.println("Brief: " + sum + " Rate: " + rate + " Amount: " + (sum * rate));
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    private void addBody(Document document, String subObjectName, JsonNode subObjectData) {
//        try {
//            // Get the "BAGSUM" object
//            JsonNode bagsumNode = subObjectData.get("BAGSUM");
//
//            if (bagsumNode != null && bagsumNode.isObject()) {
//                int numRates = bagsumNode.size(); // Number of rates
//
//                // Initialize arrays to store rate, amount, and brief sum for each rate
//                double[] rates = new double[numRates];
//                double[] amounts = new double[numRates];
//                double[] briefSums = new double[numRates];
//
//                int index = 0; // Index to keep track of the current position in arrays
//
//                // Iterate through all keys within the "BAGSUM" object
//                for (Iterator<String> it = bagsumNode.fieldNames(); it.hasNext();) {
//                    String rateKey = it.next();
//                    double rate = Double.parseDouble(rateKey);
//                    JsonNode arrayToCalculate = bagsumNode.get(rateKey);
//
//                    if (arrayToCalculate != null && arrayToCalculate.isArray()) {
//                        // Initialize variables to store amount and brief sum for the current rate
//                        double amount = 0.0;
//                        double briefSum = 0.0;
//
//                        // Iterate through the values in the array and calculate the amount and brief sum
//                        for (JsonNode value : arrayToCalculate) {
//                            String stringValue = value.asText();
//                            double numericValue = Double.parseDouble(stringValue);
//                            amount += numericValue * rate;
//                            briefSum += numericValue;
//                        }
//
//                        // Store the calculated values in arrays
//                        rates[index] = rate;
//                        amounts[index] = amount;
//                        briefSums[index] = briefSum;
//
//                        index++; // Move to the next position in arrays
//                    }
//                }
//
//                // Now you have arrays containing the calculated values for each rate
//                // You can access these arrays for further processing or printing if needed
//                for (int i = 0; i < numRates; i++) {
//                    System.out.println("Rate: " + rates[i] + ", Brief: " + briefSums[i] + ", Amount: " + amounts[i]);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//







    private void addBody(Document document, String subObjectName, JsonNode subObjectData) {
        try {
            // Get the "BAGSUM" object
            JsonNode bagsumNode = subObjectData.get("BAGSUM");

            if (bagsumNode != null && bagsumNode.isObject()) {
                int numRates = bagsumNode.size(); // Number of rates

                // Initialize arrays to store rate, amount, and brief sum for each rate
                BigDecimal[] rates = new BigDecimal[numRates];
                BigDecimal[] amounts = new BigDecimal[numRates];
                BigDecimal[] briefSums = new BigDecimal[numRates];

                int index = 0; // Index to keep track of the current position in arrays

                // Iterate through all keys within the "BAGSUM" object
                for (Iterator<String> it = bagsumNode.fieldNames(); it.hasNext();) {
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

                        // Store the calculated values in arrays
                        rates[index] = rate;
                        amounts[index] = amount;
                        briefSums[index] = briefSum;

                        // Output the rate string and calculated values
                        System.out.println(rateString + ", Brief: " + briefSums[index] + ", Amount: " + amounts[index]);

                        index++; // Move to the next position in arrays
                    }
                }

                // Now you have arrays containing the calculated values for each rate
                // You can access these arrays for further processing or printing if needed
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String formatDate(String originalDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
            Date date = inputFormat.parse(originalDate);

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return "";
    }


//    private void addHeader(Document document, String subObjectName, JsonNode subObjectData) throws IOException {
//        String date = null;
//        JsonNode dateNode = subObjectData.get("DATE");
//        if (dateNode != null && dateNode.isArray()) {
//            for (JsonNode dateValue : dateNode) {
//                date = dateValue.asText();
//
//            }
//        }
//        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
//        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
//        Image image = new Image(imageData);
//
//        float imageWidth = PageSize.A3.getWidth() * 0.94f; // Use A3 size directly
//        image.setWidth(imageWidth);
//
//        document.add(image);
//
//        Paragraph paragraph = new Paragraph()
//                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
//                .setFontSize(35)
//                .setMarginTop(30) // Add some top margin for spacing
//                .setWidth(imageWidth)
//                .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
//
//        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
//        paragraph.addTabStops(tabStop);
//
//        Paragraph businessParagraph = new Paragraph().setMarginLeft(40) // Add a left margin
//                .add(new Text("M/s :    ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD)))
//                .add(new Text(subObjectName) // Add the subObjectName with bold font
//                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
//                .add(new Tab())
//                .add(new Text("DATE: ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD)))
//                .add(new Text(date) // Add the date
//                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)));
//
//        document.add(paragraph);
//
//
//        document.add(businessParagraph);
//    }


//    private void addHeader(Document document, String subObjectName, JsonNode subObjectData) throws IOException {
//        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
//        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
//        Image image = new Image(imageData);
//
//        float imageWidth = document.getPdfDocument().getDefaultPageSize().getWidth() * 0.94f;
//        image.setWidth(imageWidth);
//
//        document.add(image);
//
//        Paragraph paragraph = new Paragraph()
//                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
//                .setFontSize(25)
//                .setMarginTop(25) // Add some top margin for spacing
//                .setWidth(imageWidth)
//                .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
//
//        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
//        paragraph.addTabStops(tabStop);
//
//        Paragraph businessParagraph = new Paragraph().setMarginLeft(40) // Add a left margin
//                .add(new Text("M/s :    ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD))).add(new Text(subObjectName) // Add the businessName with bold font
//                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)));
//
//
//        document.add(paragraph);
//        LineSeparator separator = new LineSeparator(new SolidLine(1f));
//        document.add(separator);
//        paragraph.add(businessParagraph);
//        paragraph.add(new Text("                              "));
////        paragraph.add(dateText);
//
//        document.add(paragraph);
//    }


//    private void addHeader(Document document,String subObjectName,JsonNode subObjectData) throws IOException {
//        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
//        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
//        Image image = new Image(imageData);
//
//        float imageWidth = a3PageSize.getWidth() * 0.94f;
//        image.setWidth(imageWidth);
//
//        document.add(image);
//
//        Paragraph paragraph = new Paragraph().setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(25).setMarginTop(25) // Add some top margin for spacing
//                .setWidth(imageWidth).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
//
//
//        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
//        paragraph.addTabStops(tabStop);
//
//        Paragraph businessParagraph = new Paragraph().setMarginLeft(40) // Add a left margin
//                .add(new Text("M/s :    ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD))).add(new Text(businessName) // Add the businessName with bold font
//                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)));
//
//        Text dateText = new Text("DATE :    " + date);
////                    .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD));
//
//// Add businessDiv and dateText with space between them
//        paragraph.add(businessParagraph);
//        paragraph.add(new Text("                              "));
//        paragraph.add(dateText);
//
//        document.add(paragraph);
//
//
//        // Add the "Particulars" line with dynamic values
//        StringBuilder particularsLine = new StringBuilder("Particulars : ");
//        for (int i = 0; i < particularsList.size(); i++) {
//            if (i > 0) {
//                particularsLine.append("," + "\t");
//            }
//            particularsLine.append(particularsList.get(i)).append(" ").append(" ").append(productList.get(i));
//        }
//
//        Paragraph particularsParagraph = new Paragraph(particularsLine.toString()).setMarginLeft(4).setMarginTop(-3) // Add top margin for spacing
//                .setFontSize(20).setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//        document.add(particularsParagraph);
//
//        LineSeparator separator = new LineSeparator(new SolidLine(1f));
//        document.add(separator);
//
//    }


    private void addJsonObjectToDocument(Document document, JSONObject jsonObject, int level, Map<String, List<String>> keyValueMap) {
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);

            if (value instanceof JSONObject) {
                // If the value is another JSONObject, recursively add it to the document
//                System.out.println("Processing JSONObject: " + key);
                Paragraph keyParagraph = new Paragraph(key).setMarginLeft(level * 20);
                document.add(keyParagraph);
                addJsonObjectToDocument(document, (JSONObject) value, level + 1, keyValueMap);
            } else if (value instanceof JSONArray) {
                // If the value is an array, store it as a list of strings
                JSONArray jsonArray = (JSONArray) value;
                List<String> values = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    values.add(jsonArray.optString(i));
                }
                keyValueMap.put(key, values);

                // Perform calculations based on specific keys
                if (key.equals("RATE")) {
//                    System.out.println("Calculating RATE values.");
                    double sum = 0.0;
                    for (String rateValue : values) {
                        sum += Double.parseDouble(rateValue);
                    }
                    Paragraph calculationParagraph = new Paragraph("Sum of RATE values: " + sum).setMarginLeft(level * 20);
                    document.add(calculationParagraph);
                }
            } else {
                // If the value is not a JSONObject or an array, store it as a single string
//                System.out.println("Processing value: " + value);
                Paragraph keyValueParagraph = new Paragraph(key + ": " + value).setMarginLeft(level * 20);
                document.add(keyValueParagraph);
                List<String> values = new ArrayList<>();
                values.add(String.valueOf(value));
                keyValueMap.put(key, values);

                // Check if the key is a unit ("BAG'S" or "KG'S")
                if (key.equals("BAG'S") || key.equals("KG'S")) {
                    JSONObject unitData = jsonObject.getJSONObject(key);
                    System.out.println("Processing unit: " + key);

                    // Merge and collect values for specific fields
                    List<String> items = new ArrayList<>();
                    List<String> coolies = new ArrayList<>();
                    List<String> scValues = new ArrayList<>();
                    List<String> luggages = new ArrayList<>();
                    List<String> itemQtys = new ArrayList<>();

                    for (String unitKey : unitData.keySet()) {
                        JSONObject itemObject = unitData.getJSONObject(unitKey);
                        items.addAll(getValuesFromObject(itemObject, "ITEM"));
                        coolies.addAll(getValuesFromObject(itemObject, "Coolie"));
                        scValues.addAll(getValuesFromObject(itemObject, "S.C"));
                        luggages.addAll(getValuesFromObject(itemObject, "Luggage"));
                        itemQtys.addAll(getValuesFromObject(itemObject, "Item qty"));
                    }

                    // Perform calculations and add them to the document
                    double scSum = 0.0;
                    for (String scValue : scValues) {
                        scSum += Double.parseDouble(scValue);
                    }
                    System.out.println("Sum of S.C values: " + scSum);
                    Paragraph scCalculation = new Paragraph("Sum of S.C values: " + scSum).setMarginLeft(level * 20);
                    document.add(scCalculation);
                }
            }
        }
    }

    // Helper method to get values from a JSONObject for a specific key
    private List<String> getValuesFromObject(JSONObject jsonObject, String key) {
        List<String> values = new ArrayList<>();
        if (jsonObject.has(key)) {
            JSONArray jsonArray = jsonObject.getJSONArray(key);
            for (int i = 0; i < jsonArray.length(); i++) {
                values.add(jsonArray.optString(i));
            }
        }
        return values;
    }


}


//        try {
//            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//            PdfWriter pdfWriter = new PdfWriter(outputStream);
//            PageSize a3PageSize = PageSize.A3;
//            PdfDocument pdfDocument = new PdfDocument(pdfWriter);
//            pdfDocument.setDefaultPageSize(a3PageSize);
//            Document document = new Document(pdfDocument);
//            // Header Section
//            addHeader(document, a3PageSize, businessName, date, particularsList, productList);
//
//            // Body Section
////            addBody(document, particularsList, rate, amount);
//
//            // Footer Section
////            addFooter(document);
//
//            document.close();
//
//            return outputStream.toByteArray();
//        } catch (Exception e) {
//            e.printStackTrace();
//            return new byte[0];
//        }
//PageSize a3PageSize = PageSize.A3;
//                pdfDocument.setDefaultPageSize(a3PageSize);
//                Document document = new Document(pdfDocument);


//    private void addHeader(Document document,String subObjectName,JsonNode subObjectData) throws IOException {
//        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
//        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
//        Image image = new Image(imageData);
//
//        float imageWidth = a3PageSize.getWidth() * 0.94f;
//        image.setWidth(imageWidth);
//
//        document.add(image);
//
//        Paragraph paragraph = new Paragraph().setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(25).setMarginTop(25) // Add some top margin for spacing
//                .setWidth(imageWidth).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
//
//
//        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
//        paragraph.addTabStops(tabStop);
//
//        Paragraph businessParagraph = new Paragraph().setMarginLeft(40) // Add a left margin
//                .add(new Text("M/s :    ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD))).add(new Text(businessName) // Add the businessName with bold font
//                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)));
//
//        Text dateText = new Text("DATE :    " + date);
////                    .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD));
//
//// Add businessDiv and dateText with space between them
//        paragraph.add(businessParagraph);
//        paragraph.add(new Text("                              "));
//        paragraph.add(dateText);
//
//        document.add(paragraph);
//
//
//        // Add the "Particulars" line with dynamic values
//        StringBuilder particularsLine = new StringBuilder("Particulars : ");
//        for (int i = 0; i < particularsList.size(); i++) {
//            if (i > 0) {
//                particularsLine.append("," + "\t");
//            }
//            particularsLine.append(particularsList.get(i)).append(" ").append(" ").append(productList.get(i));
//        }
//
//        Paragraph particularsParagraph = new Paragraph(particularsLine.toString()).setMarginLeft(4).setMarginTop(-3) // Add top margin for spacing
//                .setFontSize(20).setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//        document.add(particularsParagraph);
//
//        LineSeparator separator = new LineSeparator(new SolidLine(1f));
//        document.add(separator);
//
//    }