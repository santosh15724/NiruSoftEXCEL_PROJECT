package com.niruSoft.niruSoft.service;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;


@Slf4j
@Service
public class GenerateBillService implements GenerateBillImpl {

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

        // Use the modifyJsonStructureWithSum function to add "BAGSUM" and "KGSUM" to the JSON
        JSONObject modifyJsonWithSum = modifyJsonStructureWithSum(originalJson);

        // Merge the two JSON objects
        for (String farmerName : modifyJsonWithSum.keySet()) {
            if (modifiedJson.has(farmerName)) {
                JSONObject farmerData = modifiedJson.getJSONObject(farmerName);
                JSONObject farmerDataWithSum = modifyJsonWithSum.getJSONObject(farmerName);

                // Merge "BAGSUM" and "KGSUM" into the existing farmerData
                farmerData.put("BAGSUM", farmerDataWithSum.get("BAGSUM"));
                farmerData.put("KGSUM", farmerDataWithSum.get("KGSUM"));
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
                        return String.valueOf(cell.getNumericCellValue());
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    CellValue cellValue = formulaEvaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
                            return String.valueOf(cellValue.getNumberValue());
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

            // Initialize BagSum and KgSum
            JSONObject bagSum = new JSONObject();
            JSONObject kgSum = new JSONObject();

            for (int i = 0; i < farmerDataArray.length(); i++) {
                JSONObject rowData = farmerDataArray.getJSONObject(i);

                // Extract relevant data
                String rate = rowData.getString("Rate");
                String qty = rowData.getString("QTY");

                // Add quantity to BagSum or KgSum based on Rate
                if (!rate.isEmpty() && !qty.isEmpty()) {
                    double rateValue = Double.parseDouble(rate);
                    double qtyValue = Double.parseDouble(qty);

                    // Determine whether to use BagSum or KgSum based on Rate
                    String sumKey = rateValue >= 100.0 ? "BAGSUM" : "KGSUM";

                    // Add the quantity to the appropriate sum
                    JSONObject sumObject = sumKey.equals("BAGSUM") ? bagSum : kgSum;
                    if (!sumObject.has(rate)) {
                        sumObject.put(rate, new JSONArray());
                    }
                    sumObject.getJSONArray(rate).put(String.valueOf(qtyValue)); // Convert qtyValue to String
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

            // Add the modified farmerDataObject to the modifiedJson
            modifiedJson.put(farmerName, farmerDataObject);
        }

        return modifiedJson;
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