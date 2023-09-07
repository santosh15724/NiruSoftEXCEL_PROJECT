package com.niruSoft.niruSoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.FontConstants;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TabAlignment;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.niruSoft.niruSoft.model.SubObjectData;
import com.niruSoft.niruSoft.service.impl.GenerateBillImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
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
import java.util.stream.Collectors;
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
            int unitColumnIndex = columnIndexes.getOrDefault("UNIT", -1);
            int rateColumnIndex = columnIndexes.getOrDefault("Rate", -1);

            // Loop through the rows and extract data for all farmer names
            for (int rowIndex = 1; rowIndex < sheet.getPhysicalNumberOfRows(); rowIndex++) {
                Row dataRow = sheet.getRow(rowIndex);

                // Add null check for dataRow
                if (dataRow != null) {
                    Cell farmerNameCell = dataRow.getCell(farmerNameColumnIndex);

                    if (farmerNameCell != null) {
                        String farmerName = getCellValueAsString(farmerNameCell, formulaEvaluator);
                        String itemQty = itemQtyColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(itemQtyColumnIndex), formulaEvaluator) : "";
                        String item = itemColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(itemColumnIndex), formulaEvaluator) : "";
                        String unit = unitColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(unitColumnIndex), formulaEvaluator) : "";
                        String rate = rateColumnIndex != -1 ? getCellValueAsString(dataRow.getCell(rateColumnIndex), formulaEvaluator) : "";

                        Map<String, String> dataMap = new HashMap<>();
                        IntStream.range(0, headerRow.getPhysicalNumberOfCells()).forEach(cellIndex -> {
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Convert the original resultMap to a JSONObject
        JSONObject originalJson = new JSONObject(resultMap);

        // Modify the JSON structure to include KGSUM and BAGSUM
        JSONObject modifiedJson = modifyJsonStructure(originalJson);

        System.out.println(modifiedJson);
        return modifiedJson; // Return the modified JSON
    }

    private JSONObject modifyJsonStructure(JSONObject originalJson) {
        JSONObject modifiedJson = new JSONObject();

        for (String farmerName : originalJson.keySet()) {
            JSONArray farmerDataArray = originalJson.getJSONArray(farmerName);
            JSONObject farmerDataObject = new JSONObject();

            // Initialize KGSUM and BAGSUM structures
            JSONObject kgSum = new JSONObject();
            JSONObject bagSum = new JSONObject();

            for (int i = 0; i < farmerDataArray.length(); i++) {
                JSONObject rowData = farmerDataArray.getJSONObject(i);

                String unit = rowData.optString("UNIT", "");
                String rate = rowData.optString("Rate", "");
                String qty = rowData.optString("QTY", "");

                // Check if the unit is KG's or BAG's
                if ("KG'S".equalsIgnoreCase(unit)) {
                    // Check if rate exists in KGSUM
                    if (!rate.isEmpty()) {
                        if (!kgSum.has(rate)) {
                            kgSum.put(rate, new JSONArray());
                        }
                        kgSum.getJSONArray(rate).put(qty);
                    }
                } else if ("BAG'S".equalsIgnoreCase(unit)) {
                    // Check if rate exists in BAGSUM
                    if (!rate.isEmpty()) {
                        if (!bagSum.has(rate)) {
                            bagSum.put(rate, new JSONArray());
                        }
                        bagSum.getJSONArray(rate).put(qty);
                    }
                }

                for (String header : rowData.keySet()) {
                    if (!farmerDataObject.has(header)) {
                        farmerDataObject.put(header, new JSONArray());
                    }
                    JSONArray headerArray = farmerDataObject.getJSONArray(header);
                    headerArray.put(rowData.getString(header));
                }
            }

            // Add KGSUM and BAGSUM to farmerDataObject
            farmerDataObject.put("KGSUM", kgSum);
            farmerDataObject.put("BAGSUM", bagSum);

            modifiedJson.put(farmerName, farmerDataObject);
        }

        return modifiedJson;
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
//            System.out.println(jsonNode);

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

        Paragraph paragraph = new Paragraph().setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(70).setMarginTop(30) // Add some top margin for spacing
                .setWidth(imageWidth).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);

        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
        paragraph.addTabStops(tabStop);

        StringBuilder largeSpace = new StringBuilder();
        for (int i = 0; i < 70; i++) { // Add 100 spaces for a large space
            largeSpace.append(" ");
        }

        Paragraph headerParagraph = new Paragraph().setMarginLeft(40).add(new Text("M/s: ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(19)).add(new Text("      ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(18)).add(new Text(subObjectName + largeSpace.toString()) // Add the subObjectName with a large space
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(20)).add(new Text("DATE:").setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(18)).add(new Text("    ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setFontSize(17)).add(new Text(date).setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(18));

        System.out.println(dateList);
        String datesOfparticuler = String.join(",   ", dateList);


        Paragraph headerParagraph2 = new Paragraph()
                .setMarginRight(17).
                add(new Text("Particular: ")
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD))
                        .setFontSize(19)).add(new Text("    ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).
                        setFontSize(18)).add(new Text(datesOfparticuler).setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                        .setFontSize(18));

        document.add(paragraph);
        document.add(headerParagraph);
        document.add(headerParagraph2);
    }

    private void addBody(Document document, String subObjectName, JsonNode subObjectData) throws IOException {
        List<Map<String, Map<String, Double>>> detailsList = new ArrayList<>(); // Use Map<String, Map<String, Double>> for detailsList


        List<Map<String, BigDecimal>> bagsumDetailsList = new ArrayList<>();
        try {
            // Get the "BAGSUM" object
            JsonNode bagsumNode = subObjectData.get("BAGSUM");

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


                        System.out.println(rateString + ", Brief: " + briefSum + ", Amount: " + amount);

                        System.out.println("Brief: " + rateString);
                        System.out.println("Rate: " + rate);
                        System.out.println("Amount: " + amount);

                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        PageSize a3PageSize = PageSize.A3;
        document.getPdfDocument().setDefaultPageSize(a3PageSize);
        float[] columnWidths = {50f, 400f, 70f, 80f};
        int serialNumber = 1;
        Table headerTable = new Table(columnWidths);
        headerTable.setTextAlignment(TextAlignment.CENTER);
        headerTable.setMarginLeft(30);
        headerTable.setMarginTop(30);

        headerTable.setFontSize(12);

        headerTable.addCell("SL NO");
        headerTable.addCell("Brief");
        headerTable.addCell("Rate");
        headerTable.addCell("Amount");
        headerTable.setPadding(5);
        document.add(headerTable);

        for (Map<String, BigDecimal> bagsumDetails : bagsumDetailsList) {
            String brief = bagsumDetails.get("Brief").toString();
            String rate = bagsumDetails.get("Rate").toString();
            String amount = bagsumDetails.get("Amount").toString();
            Table dataTable = new Table(columnWidths);
            dataTable.setTextAlignment(TextAlignment.LEFT);
            dataTable.setMarginLeft(30);
            dataTable.setFontSize(12);
            dataTable.addCell(String.format("%-2d", serialNumber));
            dataTable.addCell(brief);
            Paragraph rateParagraph = new Paragraph(rate);
            rateParagraph.setTextAlignment(TextAlignment.CENTER);
            dataTable.addCell(rateParagraph);
            Paragraph amountParagraph = new Paragraph(amount);
            amountParagraph.setTextAlignment(TextAlignment.CENTER);
            dataTable.addCell(amountParagraph);
            dataTable.setPadding(10);
            dataTable.setHeight(50);
            document.add(dataTable);


            serialNumber++;
        }
        // Loop over detailsList
        // Loop over detailsList
        JsonNode kgsumNode = subObjectData.get("KGSUM");

///Use the fieldIterator to loop over kgsumNode fields
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = kgsumNode.fields();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = fieldIterator.next();
            String rateKey = entry.getKey(); // Store the rateKey value before entering the loop
            JsonNode arrayToCalculate = entry.getValue();

            if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                List<String> briefValues = new ArrayList<>();

                for (JsonNode value : arrayToCalculate) {
                    String stringValue = value.asText();
                    briefValues.add(stringValue);
                }

                // Calculate the number of rows needed for "Brief" values
                int numRows = (int) Math.ceil(briefValues.size() / 4.0);

                for (int row = 0; row < numRows; row++) {
                    // Create a new data table for the details
                    Table dataTable = new Table(columnWidths);
                    dataTable.setTextAlignment(TextAlignment.LEFT);
                    dataTable.setMarginLeft(30);
                    dataTable.setFontSize(12);

                    dataTable.addCell(String.format("%-2d", serialNumber));

                    String str = "";
                    for (int i = row * 4; i < (row + 1) * 4 && i < briefValues.size(); i++) {
                        str += briefValues.get(i) + " " + " ";

                    }
                    dataTable.addCell(str);


//                    Paragraph rateParagraph = new Paragraph(String.valueOf(rateKey));
                    Paragraph rateParagraph;
                    if ("0".equals(rateKey)) {
                        rateParagraph = new Paragraph("NO SALE");
                    } else {
                        rateParagraph = new Paragraph(String.valueOf(rateKey));
                    }
                    rateParagraph.setTextAlignment(TextAlignment.CENTER);
                    dataTable.addCell(rateParagraph);

                    // Calculate the total amount for the "Amount" column based on this set of "Brief" values
//                    double totalAmountByRate = briefValues.subList(row * 4, Math.min((row + 1) * 4, briefValues.size()))
//                            .stream()
//                            .mapToDouble(Double::parseDouble)
//                            .sum();
                    double totalAmountByRate;
                    if ("0".equals(rateKey)) {
                        totalAmountByRate = 0.0; // Set a fixed value for "NO SALE"
                    } else {
                        totalAmountByRate = briefValues.subList(row * 4, Math.min((row + 1) * 4, briefValues.size()))
                                .stream()
                                .mapToDouble(Double::parseDouble)
                                .sum();
                    }
                    Paragraph amountParagraph = new Paragraph(String.valueOf(totalAmountByRate * Double.parseDouble(rateKey)));
                    amountParagraph.setTextAlignment(TextAlignment.CENTER);
                    dataTable.addCell(amountParagraph);

                    dataTable.setPadding(10);
                    dataTable.setHeight(50);
                    document.add(dataTable);

                    // Increment the serial number for the next set of "Brief" values
                    serialNumber++;
                }
            }
        }


        JsonNode coolieNode = subObjectData.get("Coolie");
        JsonNode LuggageNode = subObjectData.get("Luggage");
        JsonNode SCNode = subObjectData.get("S.C");
        JsonNode AmountNode = subObjectData.get("Amount");

        // Calculate the sum of "Coolie" values
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

        System.out.println("Sum of Coolie values: " + Cooliesum);
        String coolieSumAsString = String.valueOf(Cooliesum);
        System.out.println("Sum of Coolie values: " + Luggagesum);
        String LuggagesumAsString = String.valueOf(Luggagesum);
        System.out.println("Sum of Coolie values: " + SCsum);
        String SCsumAsString = String.valueOf(SCsum);

        double expToal = Cooliesum + Luggagesum + SCsum;
        String expToalAsString = String.valueOf(expToal);

        System.out.println("Sum of Coolie values: " + expToal);
        System.out.println("Sum of Coolie values: " + Amountsum);
        String AmountsumAsString = String.valueOf(Amountsum);

        double total = Amountsum - expToal;
        String totalAsString = String.valueOf(total);


        Paragraph valuesParagraphs = new Paragraph()
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                .setFontSize(14)
                .add(new Text("Coolie - ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(coolieSumAsString)).add("\n")
                .add(new Text("S.C - ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(SCsumAsString)).add("\n")
                .add(new Text("Luggage - ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(LuggagesumAsString)).add("\n")
                .add(new Text("EXP - ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(expToalAsString));

        document.add(valuesParagraphs);

        Paragraph valuesParagraphs2 = new Paragraph()
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                .setFontSize(14)
                .setMarginLeft(80)
                .add(new Text("Amount- ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(AmountsumAsString)).add("\n")
                .add(new Text("EXP- ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(expToalAsString)).add("\n")
                .add(new Text("TOTAL - ").setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)))
                .add(new Text(totalAsString)).add("\n");


        document.add(valuesParagraphs2);


        document.close();

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


}

