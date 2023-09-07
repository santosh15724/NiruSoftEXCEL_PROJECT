//package com.niruSoft.niruSoft.utils;
//
//import com.itextpdf.kernel.colors.Color;
//import com.itextpdf.kernel.colors.DeviceRgb;
//import com.itextpdf.kernel.geom.PageSize;
//import com.itextpdf.kernel.pdf.PdfDocument;
//import com.itextpdf.kernel.pdf.PdfWriter;
//import com.itextpdf.layout.Document;
//import com.itextpdf.layout.borders.Border;
//import com.itextpdf.layout.borders.SolidBorder;
//import com.itextpdf.layout.element.Cell;
//import com.itextpdf.layout.element.Table;
//import com.itextpdf.layout.property.TextAlignment;
//import com.itextpdf.layout.property.UnitValue;
//import com.itextpdf.layout.renderer.CellRenderer;
//import com.itextpdf.layout.renderer.DrawContext;
//
//import java.io.IOException;
//import java.util.Map;
//import java.math.BigDecimal;
//
//class CustomCenteredCell extends Cell {
//        public CustomCenteredCell() {
//            super();
//            setBorder(Border.NO_BORDER);
//            setTextAlignment(TextAlignment.CENTER);
//        }
//
//
//        public void draw(DrawContext drawContext) {
//            super.draw(drawContext);
//            drawContext.getCanvas().setStrokeColor(new DeviceRgb(0, 0, 0)); // Set stroke color to black
//            drawContext.getCanvas().setLineWidth(1f); // Set line width
//            drawContext.getCanvas().moveTo(getX(), getY() + getHeight());
//            drawContext.getCanvas().lineTo(getX() + getWidth(), getY() + getHeight());
//            drawContext.getCanvas().stroke();
//        }
//    }