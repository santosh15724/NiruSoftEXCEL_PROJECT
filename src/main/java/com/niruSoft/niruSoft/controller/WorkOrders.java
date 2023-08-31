package com.niruSoft.niruSoft.controller;

import com.itextpdf.io.font.FontConstants;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.property.*;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.LineRenderer;
import com.niruSoft.niruSoft.service.GenerateBillService;
import com.niruSoft.niruSoft.utils.CustomCellRenderer;
import com.niruSoft.niruSoft.utils.ExcelValidator;
//import com.niruSoft.niruSoft.utils.NoBottomBorderCellRenderer;
import org.apache.poi.ss.usermodel.CellStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Block;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.itextpdf.layout.element.Paragraph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.poi.ss.util.CellUtil.setFont;

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


    @PostMapping("/uploadExcel")
    public ResponseEntity<?> generatePDF(@RequestParam("file") MultipartFile file) throws IOException {
        boolean isValid = ExcelValidator.validateExcel(file.getInputStream());

        if (!file.isEmpty() && isValid) {
            String jsonData = generateBillService.validateServices(file.getInputStream());

            // Generate the PDF from processed data
            byte[] pdfBytes = generateBillService.generatePdfFromJson(jsonData);

            // Prepare the PDF response
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "processedData.pdf");

            InputStreamResource inputStreamResource = new InputStreamResource(new ByteArrayInputStream(pdfBytes));

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(inputStreamResource);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid document");
        }
    }




    //    private void addJsonObjectToDocument(Document document, JSONObject jsonObject, int level, Map<String, List<String>> keyValueMap) {
//        for (String key : jsonObject.keySet()) {
//            Object value = jsonObject.get(key);
//
//            if (value instanceof JSONObject) {
//                // If the value is another JSONObject, recursively add it to the document
//                Paragraph keyParagraph = new Paragraph(key).setMarginLeft(level * 20);
//                document.add(keyParagraph);
//                addJsonObjectToDocument(document, (JSONObject) value, level + 1, keyValueMap);
//            } else if (value instanceof JSONArray) {
//                // If the value is an array, store it as a list of strings
//                JSONArray jsonArray = (JSONArray) value;
//                List<String> values = new ArrayList<>();
//                for (int i = 0; i < jsonArray.length(); i++) {
//                    values.add(jsonArray.optString(i));
//                }
//                keyValueMap.put(key, values);
//
//                // Add key-value pair to the document
//                Paragraph keyValueParagraph = new Paragraph(key + ": " + values.toString()).setMarginLeft(level * 20);
//                document.add(keyValueParagraph);
//            } else {
//                // If the value is not a JSONObject or an array, store it as a single string
//                Paragraph keyValueParagraph = new Paragraph(key + ": " + value).setMarginLeft(level * 20);
//                document.add(keyValueParagraph);
//                List<String> values = new ArrayList<>();
//                values.add(String.valueOf(value));
//                keyValueMap.put(key, values);
//            }
//        }
//    }
//private void addJsonObjectToDocument(Document document, JSONObject jsonObject, int level, Map<String, List<String>> keyValueMap) {
//    for (String key : jsonObject.keySet()) {
//        Object value = jsonObject.get(key);
//
//        if (value instanceof JSONObject) {
//            // If the value is another JSONObject, recursively add it to the document
//            Paragraph keyParagraph = new Paragraph(key).setMarginLeft(level * 20);
//            document.add(keyParagraph);
//            addJsonObjectToDocument(document, (JSONObject) value, level + 1, keyValueMap);
//        } else if (value instanceof JSONArray) {
//            // If the value is an array, store it as a list of strings
//            JSONArray jsonArray = (JSONArray) value;
//            List<String> values = new ArrayList<>();
//            for (int i = 0; i < jsonArray.length(); i++) {
//                values.add(jsonArray.optString(i));
//
//            }
//
//            keyValueMap.put(key, values);
//
//            // Add key-value pair to the document
//            Paragraph keyValueParagraph = new Paragraph(key + ": " + values.toString()).setMarginLeft(level * 20);
//            document.add(keyValueParagraph);
//
//            // Perform calculations based on specific keys
//            if (key.equals("RATE")) {
//                // Calculate something using the values in the rate array
//                double sum = 0.0;
//                for (String rateValue : values) {
//                    sum += Double.parseDouble(rateValue);
//                }
//                Paragraph calculationParagraph = new Paragraph("Sum of RATE values: " + sum).setMarginLeft(level * 20);
//                document.add(calculationParagraph);
//            }
//        } else {
//            // If the value is not a JSONObject or an array, store it as a single string
//            Paragraph keyValueParagraph = new Paragraph(key + ": " + value).setMarginLeft(level * 20);
//            document.add(keyValueParagraph);
//            List<String> values = new ArrayList<>();
//            values.add(String.valueOf(value));
//            keyValueMap.put(key, values);
//        }
//    }
//}




    @GetMapping("/generate-pdf")
    public ResponseEntity<byte[]> generatePdf() {
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
            addBody(document, particularsList, rate, amount);

            // Footer Section
            addFooter(document);

            document.close();

            byte[] pdfBytes = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("inline")
                    .filename("generated-pdf.pdf")
                    .build());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void addHeader(Document document, PageSize a3PageSize, String businessName, String date, List<String> particularsList, List<String> productList) throws IOException {
        ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
        ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
        Image image = new Image(imageData);

        float imageWidth = a3PageSize.getWidth() * 0.94f;
        image.setWidth(imageWidth);

        document.add(image);

        Paragraph paragraph = new Paragraph()
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
                .setFontSize(25)
                .setMarginTop(25) // Add some top margin for spacing
                .setWidth(imageWidth)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);


        TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
        paragraph.addTabStops(tabStop);

        Paragraph businessParagraph = new Paragraph()
                .setMarginLeft(40) // Add a left margin
                .add(new Text("M/s :    ")
                        .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD)))
                .add(new Text(businessName) // Add the businessName with bold font
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

        Paragraph particularsParagraph = new Paragraph(particularsLine.toString())
                .setMarginLeft(4)
                .setMarginTop(-3) // Add top margin for spacing
                .setFontSize(20)
                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);

        document.add(particularsParagraph);

        LineSeparator separator = new LineSeparator(new SolidLine(1f));
        document.add(separator);

    }

    private void addBody(Document document, List<String> particularsList, String rate, String amount) throws IOException {
        // Create a Div element for the table
        Div tableDiv = new Div()
                .setWidth(UnitValue.createPercentValue(100))
                .setHeight(UnitValue.createPercentValue(100));

        // Define a style for the table cells
        Style cellStyle = new Style()
                .setPadding(10) // Increase padding for cell content
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER) // Center-align text
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        // Define a style for the header cells
        Style cellStyleHeader = new Style()
                .setPadding(8) // Increase padding for cell content
                .setFontSize(18) // Increase font size for cell content
                .setTextAlignment(TextAlignment.CENTER) // Center-align text
                .setVerticalAlignment(VerticalAlignment.MIDDLE);

        // Create the table for SLno, Brief, Rate, and Amount
        Table table = new Table(UnitValue.createPercentArray(new float[]{14, 54, 13, 18}))
                .useAllAvailableWidth()
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1));

        // Add table headers
        table.addCell(createCell("SLno", true).addStyle(cellStyleHeader));
        table.addCell(createCell("Brief", true).addStyle(cellStyleHeader));
        table.addCell(createCell("Rate", true).addStyle(cellStyleHeader));
        table.addCell(createCell("Amount", true).addStyle(cellStyleHeader));


        // Usage in your code
        Color customBorderColor = new DeviceRgb(255, 255, 255);
        int numRows = (int) Math.ceil((double) particularsList.size() / 4); // Calculate number of rows needed

        for (int i = 0; i < numRows; i++) {
            boolean isLastRow = i == numRows - 1;

            Cell cell = createCell(String.valueOf(i + 1), false);
            applyCellStyle(cell, isLastRow, customBorderColor);
            table.addCell(cell);

            cell = createCell(getParticularsText(particularsList, i), false);
            applyCellStyle(cell, isLastRow, customBorderColor);
            table.addCell(cell);

            cell = createCell(rate, false);
            applyCellStyle(cell, isLastRow, customBorderColor);

            if (i == numRows - 1) {
                cell = createCell(amount, false);
                applyCellStyle(cell, true, null);
            } else {
                cell = createCell("", false);
                cell.setBorder(Border.NO_BORDER); // Empty cell in non-last rows
            }
            table.addCell(cell);
        }

        tableDiv.add(table);
        document.add(tableDiv);
        LineSeparator separator = new LineSeparator(new SolidLine(1f));
        Div separatorDiv = new Div().add(separator).setMarginTop(10).setMarginBottom(10);
        document.add(separatorDiv);
    }

    private void applyCellStyle(Cell cell, boolean isLastCell, Color customBorderColor) {
        if (!isLastCell) {
            cell.setBorderBottom(new SolidBorder(customBorderColor, 1)); // Custom bottom border
            cell.setNextRenderer(new CustomCellRenderer(cell, customBorderColor)); // Attach custom renderer
        }
        cell.setPadding(8);
        cell.setFontSize(18);
        cell.setTextAlignment(TextAlignment.CENTER);
        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private String getParticularsText(List<String> particularsList, int rowIndex) {
        int startIndex = rowIndex * 4;
        int endIndex = Math.min(startIndex + 4, particularsList.size());

        List<String> sublist = particularsList.subList(startIndex, endIndex);
        return String.join("\n", sublist);
    }


    private void addFooter(Document document) {
        // Add footer content here
        // For example, add your footer text, page number, etc.
        // Use the document.add() method to add elements to the footer
    }


//    @GetMapping("/generate-pdf")
//    public ResponseEntity<byte[]> generatePdfs() {
//        String businessName = "BCP MUNSWAMY";
//        String date = "14-8-2023";
//        List<String> particularsList = Arrays.asList("21 + 2", "15 + 3", "10 + 1", "21 + 2"); // Example particulars
//        List<String> productList = Arrays.asList("CUCUMBER", "TOMATOES", "CARROTS", "CUCUMBER", "TOMATOES", "CARROTS", "CUCUMBER", "TOMATOES", "CARROTS"); // Example products
//        String rate = "90";
//        String amount = "89";
//
//        try {
//            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//            PdfWriter pdfWriter = new PdfWriter(outputStream);
//
//            PageSize a3PageSize = PageSize.A3;
//
//            PdfDocument pdfDocument = new PdfDocument(pdfWriter);
//            pdfDocument.setDefaultPageSize(a3PageSize);
//            Document document = new Document(pdfDocument);
//
//            ClassPathResource imageResource = new ClassPathResource("Image/navBar.jpg");
//            ImageData imageData = ImageDataFactory.create(imageResource.getFile().getPath());
//            Image image = new Image(imageData);
//
//            // Calculate image width as 94% of A3 page width
//            float imageWidth = a3PageSize.getWidth() * 0.94f;
//            image.setWidth(imageWidth);
//
//            document.add(image);
//
//            // Add centered text below the image
//            Paragraph paragraph = new Paragraph()
//                    .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
//                    .setFontSize(25)
//                    .setMarginTop(25) // Add some top margin for spacing
//                    .setWidth(imageWidth)
//                    .setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//            // Create a tab stop to align the text
//            TabStop tabStop = new TabStop(imageWidth / 2, TabAlignment.CENTER);
//            paragraph.addTabStops(tabStop);
//
//            Paragraph businessParagraph = new Paragraph()
//                    .setMarginLeft(40) // Add a left margin
//                    .add(new Text("M/s :    ")
//                            .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD)))
//                    .add(new Text(businessName) // Add the businessName with bold font
//                            .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD)));
//
//            Text dateText = new Text("DATE :    " + date);
////                    .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD));
//
//// Add businessDiv and dateText with space between them
//            paragraph.add(businessParagraph);
//            paragraph.add(new Text("                              "));
//            paragraph.add(dateText);
//
//            document.add(paragraph);
//
//
//            // Add the "Particulars" line with dynamic values
//            StringBuilder particularsLine = new StringBuilder("Particulars : ");
//            for (int i = 0; i < particularsList.size(); i++) {
//                if (i > 0) {
//                    particularsLine.append("," + "\t");
//                }
//                particularsLine.append(particularsList.get(i)).append(" ").append(" ").append(productList.get(i));
//            }
//
//            Paragraph particularsParagraph = new Paragraph(particularsLine.toString())
//                    .setMarginLeft(4)
//                    .setMarginTop(-3) // Add top margin for spacing
//                    .setFontSize(20)
//                    .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD))
//                    .setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//            document.add(particularsParagraph);
//
//            // Create the table for SLno, Brief, Rate, and Amount
//            Table table = new Table(UnitValue.createPercentArray(new float[]{10, 40, 20, 30}))
//                    .useAllAvailableWidth()
//                    .setBorder(new SolidBorder(ColorConstants.BLACK, 1));
//
//            // Add table headers
//            table.addCell(createCell("SLno", true));
//            table.addCell(createCell("Brief", true));
//            table.addCell(createCell("Rate", true));
//            table.addCell(createCell("Amount", true));
//
//            // Add table rows with data
//            table.addCell(createCell(" ", false));
//            table.addCell(createCell(particularsList.toString(), false));
//            table.addCell(createCell(amount, false));
//            table.addCell(createCell(rate, false));
//
//
//            // Add the table to the document
//            document.add(table);
//
//            document.close();
//
//            byte[] pdfBytes = outputStream.toByteArray();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_PDF);
//            headers.setContentDisposition(ContentDisposition.builder("inline")
//                    .filename("generated-pdf.pdf")
//                    .build());
//
//            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
//        } catch (IOException e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }


    private Cell createCell(String content, boolean isHeader) throws IOException {
        Cell cell = new Cell();
        cell.add(new Paragraph(content)
                .setFont(PdfFontFactory.createFont(FontConstants.HELVETICA_BOLD)));
        return cell;
    }


    //@GetMapping("/generate-pdf")
//public ResponseEntity<byte[]> generatePdf() {
//    String businessName = "BCP MUNSWAMY";
//    String date = "14-8-2023";
//    List<String> particularsList = Arrays.asList("21 + 2", "15 + 3", "10 + 1"); // Example particulars
//    List<String> productList = Arrays.asList("CUCUMBER", "TOMATOES", "CARROTS"); // Example products
//
//    try {
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        PdfWriter pdfWriter = new PdfWriter(outputStream);
//
//        PageSize a3PageSize = PageSize.A3;
//
//        PdfDocument pdfDocument = new PdfDocument(pdfWriter);
//        pdfDocument.setDefaultPageSize(a3PageSize);
//        Document document = new Document(pdfDocument);
//
//        // ... (Image and other setup)
//
//        // Add centered text below the image
//        Paragraph paragraph = new Paragraph()
//                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_ROMAN))
//                .setFontSize(25)
//                .setMarginTop(25) // Add some top margin for spacing
////                .setWidth(imageWidth)
//                .setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//        // ... (businessParagraph, dateText, etc.)
//
//        document.add(paragraph);
//
//        // Add the "Particulars" line with dynamic values
//        StringBuilder particularsLine = new StringBuilder("Particulars : ");
//        for (int i = 0; i < particularsList.size(); i++) {
//            if (i > 0) {
//                particularsLine.append(","+"\t");
//            }
//            particularsLine.append(particularsList.get(i)).append(" ").append(" ").append(productList.get(i));
//        }
//
//        Paragraph particularsParagraph = new Paragraph(particularsLine.toString())
//                .setMarginLeft(4)
//                .setMarginTop(-3) // Add top margin for spacing
//                .setFontSize(20)
//                .setFont(PdfFontFactory.createFont(FontConstants.TIMES_BOLD))
//                .setHorizontalAlignment(HorizontalAlignment.CENTER);
//
//        document.add(particularsParagraph);
//
//        document.close();
//
//        byte[] pdfBytes = outputStream.toByteArray();
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_PDF);
//        headers.setContentDisposition(ContentDisposition.builder("inline")
//                .filename("generated-pdf.pdf")
//                .build());
//
//        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
//    } catch (IOException e) {
//        e.printStackTrace();
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//    }
//}
}

