package com.niruSoft.niruSoft.service;

import com.niruSoft.niruSoft.service.impl.GenerateBillImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;


@Slf4j
@Service
public class GenerateBillService implements GenerateBillImpl {


    @Override
    public JSONObject processExcelData(InputStream inputStream) {
        Map<String, List<Map<String, String>>> resultMap = new HashMap<>();
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

                        resultMap.computeIfAbsent(farmerName, k -> new ArrayList<>()).add(dataMap);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Convert the original resultMap to a JSONObject
        JSONObject originalJson = new JSONObject(resultMap);

        JSONObject modifiedJson = modifyJsonStructure(originalJson);
        System.out.print(modifiedJson);
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

            // Initialize SERIALITEM array to store ITEM values
            JSONArray serialItemArray = new JSONArray();

            StringBuilder particulersumBuilder = new StringBuilder();


            for (int i = 0; i < farmerDataArray.length(); i++) {
                JSONObject rowData = farmerDataArray.getJSONObject(i);

                String unit = rowData.optString("UNIT", "");
                String rate = rowData.optString("Rate", "");
                String qty = rowData.optString("QTY", "");
                String customerName = rowData.optString("CUSTOMER NAME", ""); // New line to get CUSTOMER NAME
                String itemQty = rowData.optString("Item qty", "");
                String item = rowData.optString("ITEM", "");

                if (!itemQty.isEmpty() && !item.isEmpty()) {
                    particulersumBuilder.append(itemQty).append(" ").append(item).append(", ");
                }
                // Check if the unit is KG's or BAG's
                if ("KG'S".equalsIgnoreCase(unit)) {
                    // Check if rate exists in KGSUM

                    if (isNumeric(rate) && Double.parseDouble(rate) == 0) {
                        // When Rate is 0, include CUSTOMER NAME in KGSUM instead of QTY
                        if (!kgSum.has("0")) {
                            kgSum.put("0", new JSONArray());
                        }
                        kgSum.getJSONArray("0").put(customerName);
                    } else if (!rate.isEmpty()) {
                        if (!kgSum.has(rate)) {
                            kgSum.put(rate, new JSONArray());
                        }
                        kgSum.getJSONArray(rate).put(qty);
                    }


                } else if ("BAG'S".equalsIgnoreCase(unit)) {
                    // Check if rate is numeric and equal to zero
                    if (isNumeric(rate) && Double.parseDouble(rate) == 0) {
                        // When Rate is 0, include CUSTOMER NAME in BAGSUM instead of QTY
                        if (!bagSum.has("0")) {
                            bagSum.put("0", new JSONArray());
                        }
                        bagSum.getJSONArray("0").put(customerName);
                    } else if (!rate.isEmpty()) {
                        if (!bagSum.has(rate)) {
                            bagSum.put(rate, new JSONArray());
                        }
                        bagSum.getJSONArray(rate).put(qty);
                    }
                }

                // Add ITEM value to SERIALITEM array
                serialItemArray.put(rowData.optString("ITEM", ", "));

                for (String header : rowData.keySet()) {
                    if (!farmerDataObject.has(header)) {
                        farmerDataObject.put(header, new JSONArray());
                    }
                    JSONArray headerArray = farmerDataObject.getJSONArray(header);
                    headerArray.put(rowData.getString(header));
                }
            }
            farmerDataObject.put("KGSUM", kgSum);
            farmerDataObject.put("BAGSUM", bagSum);
            farmerDataObject.put("SERIALITEM", serialItemArray);
            farmerDataObject.put("PARTICULERSUM", particulersumBuilder.toString().trim());

            modifiedJson.put(farmerName, farmerDataObject);
        }

        return modifiedJson;
    }


    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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


}

