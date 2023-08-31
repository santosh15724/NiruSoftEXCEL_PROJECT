package com.niruSoft.niruSoft.service;

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
import com.niruSoft.niruSoft.service.impl.GenerateBillImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;


@Slf4j
@Service
public class GenerateBillService implements GenerateBillImpl {

    //    private static final Logger log = LoggerFactory.getLogger(GenerateBillService.class);
    @Override
    public String validateServices(InputStream inputStream) {
        Map<String, List<Map<String, String>>> resultMap = new HashMap<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() <= 1) {
                log.info("No data or only header row found.");
//                System.out.println("No data or only header row found.");
                return resultMap.toString();
            }

            Row headerRow = sheet.getRow(0);
            int farmerNameColumnIndex = IntStream.range(0, headerRow.getPhysicalNumberOfCells()).filter(i -> "FARMERNAME".equals(headerRow.getCell(i).getStringCellValue().replace(" ", "").toUpperCase())).findFirst().orElse(-1);

            if (farmerNameColumnIndex == -1) {
                log.info("Column 'FARMER NAME' not found in the header.");
//                System.out.println("Column 'FARMER NAME' not found in the header.");
                return resultMap.toString();
            }

            // Loop through the rows and extract data for all farmer names
            for (int rowIndex = 1; rowIndex < sheet.getPhysicalNumberOfRows(); rowIndex++) {
                Row dataRow = sheet.getRow(rowIndex);
                Cell farmerNameCell = dataRow.getCell(farmerNameColumnIndex);

                if (farmerNameCell != null) {
                    String farmerName = getCellValueAsString(farmerNameCell, formulaEvaluator);

                    Map<String, String> dataMap = new HashMap<>();
                    IntStream.range(0, headerRow.getPhysicalNumberOfCells()).forEach(cellIndex -> {
                        Cell dataCell = dataRow.getCell(cellIndex);
                        String cellValue = getCellValueAsString(dataCell, formulaEvaluator);
                        dataMap.put(headerRow.getCell(cellIndex).getStringCellValue(), cellValue);
                    });

                    resultMap.computeIfAbsent(farmerName, k -> new ArrayList<>()).add(dataMap);
                }
            }
        } catch (Exception e) {
            log.error("An error occurred while validating services.", e);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonResult = "";
        try {
            jsonResult = objectMapper.writeValueAsString(resultMap);

        } catch (Exception e) {
            log.error("An error occurred while serializing the result map to JSON.", e);
        }

        log.info("Processed data in JSON format: {}", jsonResult);
        System.out.println(jsonResult);
//        ProcessedFarmerData(jsonResult);
        return String.valueOf((jsonResult));
    }

//    public JSONObject ProcessedFarmerData(String jsonResult) {
//        JSONObject jsonObject = new JSONObject(jsonResult);
//        JSONObject processedData = new JSONObject();
//        // Create a new JSONObject for extractedRateData
//
//        for (String farmerName : jsonObject.keySet()) {
//            JSONArray farmerDataArray = jsonObject.getJSONArray(farmerName);
//
//
//            JSONObject farmerData = new JSONObject();
//
//            for (int i = 0; i < farmerDataArray.length(); i++) {
//                JSONObject itemData = farmerDataArray.getJSONObject(i);
//                String unit = itemData.optString("UNIT");
//                String rate = itemData.optString("Rate");
//                String qty = itemData.optString("QTY");
//                String itemName = itemData.optString("ITEM");
//                String coolie = itemData.optString("Coolie");
//                String luggage = itemData.optString("Luggage");
//                String sc = itemData.optString("S.C");
//                String itemQty = itemData.optString(" Item qty");
//                String ExcelDate = itemData.optString("DATE");
////                System.out.println(ExcelDate);
//
//                JSONObject unitData = farmerData.optJSONObject(unit);
//                if (unitData == null) {
//                    unitData = new JSONObject();
//                    farmerData.put(unit, unitData);
//                }
//
//                JSONObject rateObject = unitData.optJSONObject("RATE");
//                if (rateObject == null) {
//                    rateObject = new JSONObject();
//                    unitData.put("RATE", rateObject);
//                }
//
//                JSONArray rateData = rateObject.optJSONArray(rate);
//                if (rateData == null) {
//                    rateData = new JSONArray();
//                    rateObject.put(rate, rateData);
//                }
//
//                if (!qty.isEmpty()) {
//                    rateData.put(qty);
//                }
//
//                // Store additional fields
//                storeFieldData(unitData, "ITEM", itemName);
//                storeFieldData(unitData, "Coolie", coolie);
//                storeFieldData(unitData, "Luggage", luggage);
//                storeFieldData(unitData, "S.C", sc);
//                storeFieldData(unitData, "Item qty", itemQty);
//            }
//
//            processedData.put(farmerName, farmerData);
//        }
//
//        JSONObject extractedRateData = new JSONObject();
//
//        // Iterate through each farmer
//        for (String farmerName : processedData.keySet()) {
//            JSONObject farmerData = processedData.getJSONObject(farmerName);
//
//            // Create a new JSONObject to store the combined RATE data for the current farmer
//            JSONObject combinedRateData = new JSONObject();
//
//            // Iterate through each unit for the current farmer
//            for (String unitName : farmerData.keySet()) {
//                JSONObject unitData = farmerData.getJSONObject(unitName);
//
//                // Check if the current unit is "BAG'S"
//                if (unitName.equals("BAG'S") && unitData.has("RATE")) {
//                    JSONObject rateData = unitData.getJSONObject("RATE");
//                    for (String rate : rateData.keySet()) {
//                        JSONArray qtyArray = rateData.getJSONArray(rate);
//                        double sumQty = 0.0;
//                        for (int i = 0; i < qtyArray.length(); i++) {
//                            sumQty += Double.parseDouble(qtyArray.getString(i));
//                        }
//                        combinedRateData.put(rate, sumQty);
//                    }
//                } else {
//                    // For units other than "BAG'S", retain the existing "RATE" structure
//                    combinedRateData.put(unitName, unitData.getJSONObject("RATE"));
//                }
//            }
//
//            // Add the combined RATE data to the extractedRateData object
//            if (!combinedRateData.isEmpty()) {
//                extractedRateData.put(farmerName, combinedRateData);
//            }
//        }
//
//        // Create the final ProcessedFarmer object containing extracted combined RATE data
//        JSONObject finalProcessedFarmer = new JSONObject();
//        finalProcessedFarmer.put("ProcessedFarmer", extractedRateData);
//
//        System.out.println("Final Processed Farmer Data with Extracted Combined RATE: " + finalProcessedFarmer.toString(4));
//
////        System.out.println("My Requirement data " + processedData.toString(4));
//        newagsahsjData(String.valueOf(processedData), extractedRateData);
//        return processedData;
//    }

//    public static void newagsahsjData(String processedData, JSONObject extractedRateData) {
//        JSONObject processedFarmerData = new JSONObject(processedData);
//
//        // Iterate through top-level keys (e.g., "ARUNA", "KG'S", etc.)
//        for (String topLevelKey : processedFarmerData.keySet()) {
//            JSONObject topLevelObject = processedFarmerData.getJSONObject(topLevelKey);
//
//            // Create a new JSONObject to hold merged values
//            JSONObject mergedValues = new JSONObject();
//
//            // Process nested objects and arrays under the top-level object
//            for (String nestedKey : topLevelObject.keySet()) {
//                Object nestedValue = topLevelObject.get(nestedKey);
//
//                if (nestedValue instanceof JSONObject) {
//                    JSONObject nestedObject = (JSONObject) nestedValue;
//
//                    // Retrieve arrays for different fields from the nested object
//                    JSONArray itemsArray = nestedObject.optJSONArray("ITEM");
//                    JSONArray itemQtyArray = nestedObject.optJSONArray("Item qty");
//                    JSONArray Coolie = nestedObject.optJSONArray("Coolie");
//                    JSONArray SC = nestedObject.optJSONArray("S.C");
//
//                    if (itemsArray != null && itemQtyArray != null) {
//                        for (int i = 0; i < itemsArray.length(); i++) {
//                            mergedValues.append("ITEM", itemsArray.getString(i));
//                            mergedValues.append("Item qty", itemQtyArray.getString(i));
//                            mergedValues.append("Coolie", Coolie.getString(i));
//                            mergedValues.append("S.C", SC.getString(i));
//                        }
//                    }
//                }
//            }
//
//            // Print the merged values
////            System.out.println("Merged values: " + extractedRateData);
//
//            // Call a function or perform any additional processing here if needed
//            OneLeveDeep(mergedValues, topLevelKey, "DATE", extractedRateData);
//        }
//    }


//    public static void OneLeveDeep(JSONObject mergedValues, String topLevelKey, String uniqueDatesString, JSONObject extractedRateData) {
//        List<JSONArray> itemsArrays = new ArrayList<>();
//        List<JSONArray> coolieArrays = new ArrayList<>();
//        List<JSONArray> scArrays = new ArrayList<>();
//        List<JSONArray> luggageArrays = new ArrayList<>();
//        List<JSONArray> itemQtyArrays = new ArrayList<>();
//        List<String> combinedPairs = new ArrayList<>();
//        Set<String> uniqueItems = new HashSet<>();
//
//
//        double SCtotalSum = 0.0;
//        double LuggageSum = 0.0;
//        double CoolieSum = 0.0;
//
//
//        JSONArray itemsArray = mergedValues.optJSONArray("ITEM");
//        if (itemsArray != null) {
//            itemsArrays.add(itemsArray);
//        }
//
//        JSONArray coolieArray = mergedValues.optJSONArray("Coolie");
//        if (coolieArray != null) {
//            coolieArrays.add(coolieArray);
//        }
//
//        JSONArray scArray = mergedValues.optJSONArray("S.C");
//        if (scArray != null) {
//            scArrays.add(scArray);
//        }
//
//        JSONArray luggageArray = mergedValues.optJSONArray("Luggage");
//        if (luggageArray != null) {
//            luggageArrays.add(luggageArray);
//        }
//
//        JSONArray itemQtyArray = mergedValues.optJSONArray("Item qty");
//        if (itemQtyArray != null) {
//            itemQtyArrays.add(itemQtyArray);
//        }
//
//        for (String key : mergedValues.keySet()) {
//            if (!"ITEM".equals(key) && !"Coolie".equals(key) && !"S.C".equals(key) && !"Luggage".equals(key) && !"Item qty".equals(key)) {
////                System.out.println("Key: " + key + " - Value: " + mergedValues.get(key));
//            }
//        }
//
//        System.out.println(uniqueDatesString);
////        for (JSONArray array : itemsArrays) {
////            System.out.println(array);
////        }
//        System.out.println(extractedRateData);
//
//        for (JSONArray array : scArrays) {
//            double sum = 0.0;
//            for (int i = 0; i < array.length(); i++) {
//                String valueStr = array.optString(i);
//                double value = Double.parseDouble(valueStr);
////                System.out.println(value);
//                sum += value;
//            }
//            SCtotalSum += sum;
//        }
//        System.out.println("Sum of values in scArrays: " + SCtotalSum);
//
//        for (JSONArray array : luggageArrays) {
//            double sum = 0.0;
//            for (int i = 0; i < array.length(); i++) {
//                String valueStr = array.optString(i);
//
//                // Check if the value string is not empty and is numeric
//                if (!valueStr.isEmpty()) {
//                    try {
//                        double value = Double.parseDouble(valueStr);
////                        System.out.println(value);
//                        sum += value;
//                    } catch (NumberFormatException e) {
//                        System.out.println("Invalid numeric value: " + valueStr);
//                        // You can choose to handle this error case as needed
//                    }
//                }
//            }
//            LuggageSum += sum;
//        }
//        System.out.println("Sum of values in Luggage: " + LuggageSum);
//
//        for (JSONArray array : coolieArrays) {
//            double sum = 0.0;
//            for (int i = 0; i < array.length(); i++) {
//                String valueStr = array.optString(i);
//
//                // Check if the value string is not empty and is numeric
//                if (!valueStr.isEmpty()) {
//                    try {
//                        double value = Double.parseDouble(valueStr);
////                        System.out.println(value);
//                        sum += value;
//                    } catch (NumberFormatException e) {
//                        System.out.println("Invalid numeric value: " + valueStr);
//                        // You can choose to handle this error case as needed
//                    }
//                }
//            }
//            CoolieSum += sum;
//        }
//        System.out.println("Sum of values in Coolie: " + CoolieSum);
//
//        int minSize = Math.min(itemsArrays.size(), itemQtyArrays.size());
//        for (int i = 0; i < minSize; i++) {
//            JSONArray itemsArray1 = itemsArrays.get(i);
//            JSONArray itemQtyArray1 = itemQtyArrays.get(i);
//
//            // Check if both arrays are not empty
//            if (!itemsArray1.isEmpty() && !itemQtyArray1.isEmpty()) {
//                Set<String> uniquePairs = new HashSet<>();
//
//                for (int j = 0; j < itemsArray1.length() && j < itemQtyArray1.length(); j++) {
//                    String item = itemsArray1.optString(j);
//                    String itemQty = itemQtyArray1.optString(j);
//
//                    // Check if the item is unique, and if so, add it to the uniqueItems set
//                    if (uniqueItems.add(item)) {
//                        // Create a unique pair based on quantity and item
//                        String uniquePair = itemQty + " " + item;
//                        uniquePairs.add(uniquePair);
//                    }
//                }
//
//                // Join unique pairs with commas and add to the result list
//                String combinedLine = String.join(", ", uniquePairs);
//                System.out.println(combinedLine);
//
//                combinedPairs.add(combinedLine);
//            }
//        }
//
//
//        double TotalSCtotalSumLuggageSumCoolieSum = SCtotalSum + LuggageSum + CoolieSum;
//
//    }

    private void storeFieldData(JSONObject unitData, String fieldName, String value) {
        JSONArray fieldData = unitData.optJSONArray(fieldName);
        if (fieldData == null) {
            fieldData = new JSONArray();
            unitData.put(fieldName, fieldData);
        }
        fieldData.put(value);
    }


    private static String getCellValueAsString(Cell cell, FormulaEvaluator formulaEvaluator) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING -> {
                return cell.getStringCellValue();
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Handle date cells
                    return cell.getDateCellValue().toString();
                } else {
                    // Handle numeric cells
                    return String.valueOf(cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> {
                return String.valueOf(cell.getBooleanCellValue());
            }
            case FORMULA -> {
                CellValue evaluatedCellValue = formulaEvaluator.evaluate(cell);
                return evaluatedCellValue.formatAsString(); // Return the raw formula expression
            }
            default -> {
                return "";
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    public byte[] generatePdfFromJson(String jsonData) {
//      public void  gettingvalue(String topLevelKey, String combinedLine){
//
//        }
        String businessName = "BCP MUNSWAMY";
        String date = "14-8-2023";
        List<String> particularsList = Arrays.asList("21 + 2", "15 + 3", "10 + 1", "21 + 2"); // Example particulars
        List<String> productList = Arrays.asList("CUCUMBER", "TOMATOES", "CARROTS", "CUCUMBER"); // Example products
        String rate = "90";
        String amount = "89";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter pdfWriter = new PdfWriter(outputStream);

            PageSize a3PageSize = PageSize.A3;

            PdfDocument pdfDocument = new PdfDocument(pdfWriter);
            pdfDocument.setDefaultPageSize(a3PageSize);
            Document document = new Document(pdfDocument);

            // Header Section
            addHeader(document, a3PageSize, businessName, date, particularsList, productList);

            // Body Section
//            addBody(document, particularsList, rate, amount);

            // Footer Section
//            addFooter(document);

            document.close();

            document.close();

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private void addHeader(Document document, PageSize a3PageSize, String businessName, String date, List<String> particularsList, List<String> productList) throws IOException {
        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
        Image image = new Image(imageData);

        float imageWidth = a3PageSize.getWidth() * 0.94f;
        image.setWidth(imageWidth);

        document.add(image);

        Paragraph paragraph = new Paragraph().setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN)).setFontSize(25).setMarginTop(25) // Add some top margin for spacing
                .setWidth(imageWidth).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);


        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
        paragraph.addTabStops(tabStop);

        Paragraph businessParagraph = new Paragraph().setMarginLeft(40) // Add a left margin
                .add(new Text("M/s :    ").setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD))).add(new Text(businessName) // Add the businessName with bold font
                        .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)));

        Text dateText = new Text("DATE :    " + date);
//                    .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD));

// Add businessDiv and dateText with space between them
        paragraph.add(businessParagraph);
        paragraph.add(new Text("                              "));
        paragraph.add(dateText);

        document.add(paragraph);


        // Add the "Particulars" line with dynamic values
        StringBuilder particularsLine = new StringBuilder("Particulars : ");
        for (int i = 0; i < particularsList.size(); i++) {
            if (i > 0) {
                particularsLine.append("," + "\t");
            }
            particularsLine.append(particularsList.get(i)).append(" ").append(" ").append(productList.get(i));
        }

        Paragraph particularsParagraph = new Paragraph(particularsLine.toString()).setMarginLeft(4).setMarginTop(-3) // Add top margin for spacing
                .setFontSize(20).setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)).setHorizontalAlignment(HorizontalAlignment.CENTER);

        document.add(particularsParagraph);

        LineSeparator separator = new LineSeparator(new SolidLine(1f));
        document.add(separator);

    }


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