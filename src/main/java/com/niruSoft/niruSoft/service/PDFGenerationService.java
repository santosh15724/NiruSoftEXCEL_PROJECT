package com.niruSoft.niruSoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.niruSoft.niruSoft.model.PDFData;
import com.niruSoft.niruSoft.service.impl.PDFGenerationServiceImpl;
import com.niruSoft.niruSoft.utils.PageNumberEventHandler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;


@Service

public class PDFGenerationService implements PDFGenerationServiceImpl {

    public static String currencySymbol = "\u20B9";

    @Async
    @Override
    public CompletableFuture<PDFData> generatePDFFromJSONAsync(String jsonData, String farmerName, String date) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonData);
        JsonNode kgsumNode = jsonNode.get("KGSUM");

//        JsonNode serialNumbersNode = jsonNode.get("SERIALITEM");
//        Iterator<JsonNode> serialNumbersIterator = serialNumbersNode.elements();
//
//        String firstItem = null;
//        boolean allSame = true;
//
//        if (serialNumbersIterator.hasNext()) {
//            firstItem = serialNumbersIterator.next().asText();
//
//            while (serialNumbersIterator.hasNext()) {
//                String currentItem = serialNumbersIterator.next().asText();
//
//                if (!currentItem.equals(firstItem)) {
//                    allSame = false;
//                    break;
//                }
//            }
//        }
//
//        System.out.println("All elements are the same: " + allSame);
//        System.out.println(allSame);

        JsonNode serialNumbersNode = jsonNode.get("SERIALITEM");

        Iterator<JsonNode> serialNumbersIterator = serialNumbersNode.elements();

        ClassPathResource fontResource = new ClassPathResource("Image/ariel.ttf");
        PdfFont font = PdfFontFactory.createFont(fontResource.getFile().getAbsolutePath(), PdfEncodings.IDENTITY_H);

        StringBuilder itemsText = new StringBuilder();
        JsonNode particulersumNode = jsonNode.get("PARTICULERSUM");
        if (particulersumNode != null) {
            String particulersumValue = particulersumNode.toString(); // Convert the JSON object to a string
            particulersumValue = particulersumValue.replaceAll("\"", "").replaceAll(",$", "");
            if (!particulersumValue.isEmpty()) {
                if (itemsText.length() > 0) {
                    itemsText.append(", "); // Add a comma and space if itemsText already has content
                }
                itemsText.append(particulersumValue);
            }
        }


        JsonNode coolieNode = jsonNode.get("Coolie");
        JsonNode LuggageNode = jsonNode.get("Luggage");
        JsonNode SCNode = jsonNode.get("S.C");
        JsonNode AmountNode = jsonNode.get("Amount");
        JsonNode AdvanceNode = jsonNode.get("Adv./Cash");
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
                double doubleValue = Double.parseDouble(valueNode.asText());
                int value = (int) Math.ceil(doubleValue);
                SCsum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
//        System.out.println(SCsum);
        int Amountsum = 0;
        for (JsonNode valueNode : AmountNode) {
            try {
                double doubleValue = Double.parseDouble(valueNode.asText());
                int value = (int) Math.ceil(doubleValue);
                Amountsum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
        int AdvanceSum = 0;
        for (JsonNode valueNode : AdvanceNode) {
            try {
                double doubleValue = Double.parseDouble(valueNode.asText());
                int value = (int) Math.ceil(doubleValue);
                AdvanceSum += value;
            } catch (NumberFormatException e) {
                // Ignore non-integer values
            }
        }
        DecimalFormat df = new DecimalFormat("#,###");

        String coolieSumAsString = df.format(Cooliesum);
        String LuggagesumAsString = df.format(Luggagesum);
        String SCsumAsString = df.format(SCsum);
        String AmountsumAsString = df.format(Amountsum);

        String AdvancesumAsString = df.format(AdvanceSum);

        int expToal = Cooliesum + Luggagesum + SCsum + AdvanceSum;
        String expToalAsString = df.format(expToal);


        int total = Amountsum - expToal;
        String totalAsString = df.format(total);

        List<Map<String, String>> bagsumDetailsList = new ArrayList<>();
        try {
            // Get the "BAGSUM" object
            JsonNode bagsumNode = jsonNode.get("BAGSUM");

            if (bagsumNode != null && bagsumNode.isObject()) {
                int numRates = bagsumNode.size(); // Number of rates

                // Iterate through all keys within the "BAGSUM" object
                for (Iterator<String> it = bagsumNode.fieldNames(); it.hasNext(); ) {
                    String rateKey = it.next();
                    BigDecimal rate;
                    String rateString; // Default rate string

                    if ("0".equals(rateKey)) {
                        rate = BigDecimal.ZERO;

                        JsonNode arrayToCalculate = bagsumNode.get(rateKey);
                        if (arrayToCalculate != null && arrayToCalculate.isArray() && !arrayToCalculate.isEmpty()) {
                            rateString = arrayToCalculate.get(0).asText();
                        } else {
                            rateString = "NO SALE";
                        }
                    } else {
                        rate = new BigDecimal(rateKey);
                        rateString = "Rate: " + rate;
                    }

                    JsonNode arrayToCalculate = bagsumNode.get(rateKey);

                    if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                        BigDecimal amount = BigDecimal.ZERO;
                        BigDecimal briefSum = BigDecimal.ZERO;

                        for (JsonNode value : arrayToCalculate) {
                            String stringValue = value.asText();
                            BigDecimal numericValue;
                            if (isNumeric(stringValue)) {
                                numericValue = new BigDecimal(stringValue);
                                BigDecimal product = numericValue.multiply(rate);
                                amount = amount.add(product.setScale(0, RoundingMode.CEILING));
                                briefSum = briefSum.add(numericValue);
                            } else {
                                numericValue = BigDecimal.ZERO; // For example, set to zero
                            }
                        }
                        Map<String, String> rateDetails = new HashMap<>();
                        rateDetails.put("Brief", briefSum.toString());
                        rateDetails.put("Rate", rate.toString()); // Convert BigDecimal to String
                        rateDetails.put("Amount", amount.toString());
                        rateDetails.put("RateString", rateString);

                        bagsumDetailsList.add(rateDetails);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
//        System.out.println(bagsumDetailsList);
        int sum1 = calculateSumFromJsonBAG(jsonData);
        int sum2 = calculateSumFromJsonKG(jsonData);


        int TotalSumKgBAg = sum1 + sum2;
        String strTotalSumKgBAg = df.format(TotalSumKgBAg);
//        System.out.println(TotalSumKgBAg);

        int kgsumNodeRowCount = calculateKgsumNodeRowCount(kgsumNode);
        int minNumberOfRows = 10;
        int bagsumDetailsRowCount = bagsumDetailsList.size();
        int totalRowCount = bagsumDetailsRowCount + kgsumNodeRowCount;
        int emptyRowsNeeded = Math.max(minNumberOfRows - totalRowCount, 0);


        try {
//            boolean finalAllSame = allSame;
            return CompletableFuture.supplyAsync(() -> {
                try (ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream(); PdfWriter pdfWriter = new PdfWriter(pdfOutputStream); PdfDocument pdfDocument = new PdfDocument(pdfWriter)) {
                    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberEventHandler());
                    PdfFont timesNewRomanFont = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
                    PageSize a5PageSize = new PageSize(PageSize.A5);
                    Document document = new Document(pdfDocument, a5PageSize);
                    document.setMargins(20, 20, 20, 20);
                    document.setFont(timesNewRomanFont);

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

                    Paragraph dateParagraph = new Paragraph();
                    Text dateText = new Text("Date : ");
                    Text dateValueText = new Text(date);
                    dateParagraph.add(dateText);
                    dateParagraph.add(dateValueText);
                    dateParagraph.setMarginTop(imageHeight);
                    dateParagraph.setTextAlignment(TextAlignment.RIGHT);
                    dateParagraph.setMarginRight(25);

                    Paragraph msParagraph = new Paragraph();
                    Text msText = new Text("M/s :         ").setFont(PdfFontFactory.createFont(StandardFonts.TIMES_BOLD));
                    Text farmerNameText = new Text(farmerName).setFont(PdfFontFactory.createFont(StandardFonts.TIMES_BOLD));
                    msParagraph.add(msText);
                    msParagraph.add(farmerNameText);
                    msParagraph.setMarginTop(-24);
                    msParagraph.setMarginLeft(15);

                    document.add(dateParagraph);
                    document.add(msParagraph);
                    Paragraph particulars = new Paragraph();
                    Text msTextP = new Text("Particulars : ").setFont(PdfFontFactory.createFont(StandardFonts.TIMES_BOLD));
                    Text farmerNameTextP = new Text(itemsText.toString());
                    farmerNameTextP.setFontSize(10);
                    particulars.setMarginTop(-2);
                    particulars.setMarginLeft(5);
                    particulars.setMarginBottom(6);
                    particulars.add(msTextP);
                    particulars.add(farmerNameTextP);
                    document.add(particulars);

                    Color whiteColor = new DeviceRgb(255, 255, 255);
                    Color blackColor = new DeviceRgb(0, 0, 0);
//                    int AutoserialNumber = 0;
////                    if (!finalAllSame) {
//                        AutoserialNumber = 1;
////                    }


                    Table dataTable = new Table(new float[]{100f, 350f, 70f, 80f});
                    dataTable.setBorder(new SolidBorder(blackColor, 1f));
                    SolidBorder whiteSolidBorder = new SolidBorder(1f);
                    whiteSolidBorder.setColor(whiteColor);
                    dataTable.setBorderBottom(whiteSolidBorder);

                    Cell slNoHeaderCell = new Cell().add(new Paragraph("ITEMS").setFontSize(11).setBold()).setTextAlignment(TextAlignment.CENTER);
                    Cell briefHeaderCell = new Cell().add(new Paragraph("Brief").setFontSize(11).setBold()).setTextAlignment(TextAlignment.CENTER);
                    Cell rateHeaderCell = new Cell().add(new Paragraph("Rate").setFontSize(11).setBold()).setTextAlignment(TextAlignment.CENTER);
                    Cell amountHeaderCell = new Cell().add(new Paragraph("Amount").setFontSize(11).setBold()).setTextAlignment(TextAlignment.CENTER);

                    //Header are added.
                    dataTable.addCell(slNoHeaderCell);
                    dataTable.addCell(briefHeaderCell);
                    dataTable.addCell(rateHeaderCell);
                    dataTable.addCell(amountHeaderCell);

                    Collections.sort(bagsumDetailsList, new Comparator<Map<String, String>>() {
                        @Override
                        public int compare(Map<String, String> detail1, Map<String, String> detail2) {
                            String rate1 = detail1.get("Rate");
                            String rate2 = detail2.get("Rate");
                            double rateValue1 = Double.parseDouble(rate1);
                            double rateValue2 = Double.parseDouble(rate2);
                            return Double.compare(rateValue2, rateValue1);
                        }
                    });

                    for (Map<String, String> bagsumDetails : bagsumDetailsList) {
                        String brief = bagsumDetails.get("Brief");
                        String rate = bagsumDetails.get("Rate");
                        String amount = bagsumDetails.get("Amount");
                        String rateString = bagsumDetails.get("RateString");


//                        JsonNode serialNumberNode = serialNumbersIterator.next();
//                        String serialNumber = serialNumberNode.asText();

//                        Cell slNoCell = new Cell();
////                        slNoCell = new Cell().add(new Paragraph(serialNumber)).setTextAlignment(TextAlignment.CENTER);
////                        slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
//
////                        if (!finalAllSame) {
//                            slNoCell = new Cell().add(new Paragraph(String.format("%-2d", AutoserialNumber)));
//                        slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
//                        }
                        JsonNode serialNumberNode = serialNumbersIterator.next();

                        // Convert the serialNumberNode to a string
                        String serialNumber = serialNumberNode.asText();


                        Cell slNoCell = new Cell().add(new Paragraph(serialNumber)).setTextAlignment(TextAlignment.CENTER);
//                        Cell slNoCell = new Cell().add(new Paragraph(String.format("%-2d", serialNumber)));
                        slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));


                        Cell briefCell = new Cell().add(new Paragraph(brief));
                        briefCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                        Cell rateCell = new Cell().add(new Paragraph("0".equals(rate) ? rateString : rate));
                        rateCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                        rateCell.setTextAlignment(TextAlignment.RIGHT);

                        String formattedAmount = df.format(new BigDecimal(amount));
                        Cell amountCell = new Cell().add(new Paragraph(formattedAmount));
                        amountCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                        amountCell.setFontColor(DeviceRgb.BLUE);
                        amountCell.setTextAlignment(TextAlignment.RIGHT);


                        dataTable.addCell(slNoCell);
                        dataTable.addCell(briefCell);
                        dataTable.addCell(rateCell);
                        dataTable.addCell(amountCell);
//                        if (!finalAllSame) {
//                            AutoserialNumber++;
//                        }

                    }

                    List<Map.Entry<String, JsonNode>> sortedEntries = new ArrayList<>();

                    Iterator<Map.Entry<String, JsonNode>> fieldIterator = kgsumNode.fields();
                    while (fieldIterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fieldIterator.next();
                        String rateKey = entry.getKey();
                        JsonNode arrayToCalculate = entry.getValue();
                        if (arrayToCalculate != null && arrayToCalculate.isArray()) {
                            sortedEntries.add(entry);
                        }
                    }
                    Collections.sort(sortedEntries, (entry1, entry2) -> {
                        String rateKey1 = entry1.getKey();
                        String rateKey2 = entry2.getKey();
                        return Double.compare(Double.parseDouble(rateKey2), Double.parseDouble(rateKey1));
                    });

                    for (Map.Entry<String, JsonNode> entry : sortedEntries) {
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
//                                JsonNode serialNumberNode = serialNumbersIterator.next();
//                                String serialNumber = serialNumberNode.asText();
//                                Cell slNoCell = new Cell();
////                                slNoCell = new Cell().add(new Paragraph(serialNumber)).setTextAlignment(TextAlignment.CENTER);
////                                slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
//
////                                if (!finalAllSame) {
//
//                                    slNoCell.add(new Paragraph(String.format("%-2d", AutoserialNumber)));
//                                    slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
////                                }
                                JsonNode serialNumberNode = serialNumbersIterator.next();

                                // Convert the serialNumberNode to a string
                                String serialNumber = serialNumberNode.asText();
//                                Cell slNoCell = new Cell();
//                                slNoCell.add(new Paragraph(String.format("%-2d", serialNumber)));
                                Cell slNoCell = new Cell().add(new Paragraph(serialNumber)).setTextAlignment(TextAlignment.CENTER);
                                slNoCell.setBorderBottom(new SolidBorder(whiteColor, 1f));


                                Cell briefCell = new Cell();
                                String str = "";
                                for (int i = row * 4; i < (row + 1) * 4 && i < briefValues.size(); i++) {
                                    str += briefValues.get(i) + " " + " " + " " + " " + " " + " " + " " + " " + " " + " " + " ";
                                }
                                briefCell.add(new Paragraph(str));
                                briefCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                                Cell rateCell = new Cell();
                                String rateValue = "0".equals(rateKey) ? String.join(" ", briefValues) : rateKey;
                                rateCell.add(new Paragraph(rateValue).setTextAlignment(TextAlignment.RIGHT));
                                rateCell.setBorderBottom(new SolidBorder(whiteColor, 1f));
                                Cell amountCell = new Cell();
                                int totalAmountByRate = 0; // Initialize as an integer
                                if (!"0".equals(rateKey)) {
                                    totalAmountByRate = (int) Math.round(briefValues.subList(row * 4, Math.min((row + 1) * 4, briefValues.size())).stream().mapToDouble(Double::parseDouble).sum());
                                }
                                String formattedTotalAmount = df.format((int) Math.ceil(totalAmountByRate * Double.parseDouble(rateKey)));
                                amountCell.add(new Paragraph(formattedTotalAmount).setTextAlignment(TextAlignment.RIGHT)).setFontColor(DeviceRgb.BLUE);

                                amountCell.setBorderBottom(new SolidBorder(whiteColor, 1f));

                                dataTable.addCell(slNoCell);
                                dataTable.addCell(briefCell);
                                dataTable.addCell(rateCell);
                                dataTable.addCell(amountCell);
//                                if (!finalAllSame) {
//                                    AutoserialNumber++;
//                                }
                            }
                        }
                    }

                    // Add empty rows to dataTable
                    for (int i = 0; i < emptyRowsNeeded; i++) {
                        for (int j = 0; j < 4; j++) { // Add 4 empty cells per row
                            Cell emptyCell = new Cell().add(new Paragraph("")).setPadding(8) // Set padding to 5
                                    .setBorderBottom(new SolidBorder(whiteColor, 1f)); // Set white bottom border
                            dataTable.addCell(emptyCell);
                        }
                    }
                    document.add(dataTable);
                    LineSeparator line = new LineSeparator(new SolidLine());
                    line.setMarginTop(-2f);
                    document.add(line);

                    LayoutResult result = dataTable.createRendererSubTree().setParent(document.getRenderer()).layout(new LayoutContext(new LayoutArea(1, a5PageSize))); // Layout the table
                    Rectangle tableBoundingBox = result.getOccupiedArea().getBBox();
                    float heightFromStartToLastDataRow = a5PageSize.getHeight() - tableBoundingBox.getBottom();
//                    System.out.println(heightFromStartToLastDataRow);

                    if (heightFromStartToLastDataRow > 240.82153f) {
                        int currentPageNumber = pdfDocument.getPageNumber(document.getPdfDocument().getLastPage());
                        if (currentPageNumber == 1) {
                            document.add(new AreaBreak());
                        }
                    }

                    Paragraph paragraphTotla = new Paragraph(strTotalSumKgBAg + "  (Kg/Bags/Pkt/Box/Crate)").setMarginTop(5).setMarginLeft(130);
                    document.add(paragraphTotla);

                    Table expTable = new Table(new float[]{70, 50});
                    expTable.setFontSize(9);
                    expTable.setMarginTop(0);

                    PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD);

                    expTable.setFont(boldFont);

                    Cell headerCell = new Cell().add(new Paragraph("    EXP").setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE));
                    headerCell.setBorderRight(new SolidBorder(whiteColor, 1f));
                    expTable.addCell(headerCell);

                    headerCell = new Cell().add(new Paragraph().setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE));
                    expTable.addCell(headerCell);

                    expTable.addCell(new Cell().add(new Paragraph("Adv./cash").setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(AdvancesumAsString).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));

                    expTable.addCell(new Cell().add(new Paragraph("Coolie").setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(coolieSumAsString).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));

                    expTable.addCell(new Cell().add(new Paragraph("Luggage").setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(LuggagesumAsString).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph("S.CASH").setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    expTable.addCell(new Cell().add(new Paragraph(SCsumAsString).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)));
                    Paragraph tosta = new Paragraph().add(new Text(expToalAsString)).add("\n").setMarginLeft(85).setFontSize(9);
                    document.add(expTable);
                    document.add(tosta);

                    Paragraph amountParagraph = new Paragraph().setFontSize(13).setMarginTop(-100).setMarginRight(5).setPaddingBottom(2).add(new Text(AmountsumAsString)).setFont(PdfFontFactory.createFont(StandardFonts.TIMES_BOLD)).add("\n").setTextAlignment(TextAlignment.RIGHT);

                    Paragraph expTotalParagraph = new Paragraph().setFontSize(12).setMarginTop(-8).setMarginRight(5).add(new Text(expToalAsString)).setFont(PdfFontFactory.createFont(StandardFonts.TIMES_BOLD)).add("\n").setTextAlignment(TextAlignment.RIGHT);
                    Color brownColor = new DeviceRgb(139, 69, 19);
//                    String currencySymbol = "\u20B9";
                    Paragraph totalParagraph = new Paragraph().setFontSize(16).add("Total:     ").setMarginRight(5).add(new Text(currencySymbol)).add(new Text(totalAsString)).setFont(font).setFontColor(brownColor).add("\n").setTextAlignment(TextAlignment.RIGHT);

                    amountParagraph = amountParagraph.setUnderline();
                    document.add(amountParagraph);
                    document.add(expTotalParagraph);
                    document.add(totalParagraph);


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

    public static int calculateSumFromJsonBAG(String jsonString) {
        int sum = 0;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // Get the "BAGSUM" object
            JsonNode bagsumNode = jsonNode.get("BAGSUM");

            // Iterate through the keys in the "BAGSUM" object
            for (JsonNode keyNode : bagsumNode) {
                // Check if the key is "0" and skip it
                if (keyNode.isTextual() && keyNode.asText().equals("0")) {
                    continue;
                }

                // Iterate through the values (arrays) for each key
                for (JsonNode valueNode : keyNode) {
                    // Try to convert the value to an integer, or treat it as zero if it's not numeric
                    int value = 0; // Default to zero
                    try {
                        value = (int) Math.ceil(Double.parseDouble(valueNode.asText()));
                    } catch (NumberFormatException ex) {
                        // Ignore non-numeric values
                    }
                    sum += value;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sum;
    }

    public static int calculateSumFromJsonKG(String jsonString) {
        int sum = 0;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // Get the "BAGSUM" object
            JsonNode bagsumNode = jsonNode.get("KGSUM");

            // Iterate through the keys in the "BAGSUM" object
            for (JsonNode keyNode : bagsumNode) {
                // Check if the key is "0" and skip it
                if (keyNode.isTextual() && keyNode.asText().equals("0")) {
                    continue;
                }

                // Iterate through the values (arrays) for each key
                for (JsonNode valueNode : keyNode) {
                    // Try to convert the value to an integer, or treat it as zero if it's not numeric
                    int value = 0; // Default to zero
                    try {
                        value = (int) Math.ceil(Double.parseDouble(valueNode.asText()));
                    } catch (NumberFormatException ex) {
                        // Ignore non-numeric values
                    }
                    sum += value;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sum;
    }

    private static boolean isNumeric(String str) {
        try {
            new BigDecimal(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
