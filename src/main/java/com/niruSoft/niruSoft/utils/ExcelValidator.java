package com.niruSoft.niruSoft.utils;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ExcelValidator {

    private static final String[] EXPECTED_HEADERS = {
            "DATE", "SALESMAN", "FARMERNAME", "ITEMQTY","ITEM","COOLIE","LUGGAGE","ADV./CASH","C%","S.C","CUSTOMERNAME","QTY","RATE","UNIT","AMOUNT","NETAMT"
    };


    public static boolean validateExcel(InputStream excelInputStream) {
        try (Workbook workbook = WorkbookFactory.create(excelInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() <= 1) {
                return false;
            }
            Row headerRow = sheet.getRow(0);

            if (headerRow == null || headerRow.getPhysicalNumberOfCells() != EXPECTED_HEADERS.length) {
                return false;
            }
            System.out.println(EXPECTED_HEADERS.length);
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                Cell headerCell = headerRow.getCell(i);
                String cellValue = headerCell != null ? headerCell.getStringCellValue().replace(" ", "").toUpperCase() : "null";
//                System.out.println(cellValue);

                if (!isCellValid(cellValue, EXPECTED_HEADERS[i])) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isCellValid(String cellValue, String expectedValue) {
        return cellValue.equals(expectedValue);
    }
}
