package com.niruSoft.niruSoft.utils;

import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;


public class PageNumberEventHandler implements IEventHandler {
    @Override
    public void handleEvent(Event event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
        PdfDocument pdfDoc = docEvent.getDocument();
        PdfPage page = docEvent.getPage();

        int pageNumber = pdfDoc.getPageNumber(page);
        int totalPages = pdfDoc.getNumberOfPages();

        PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);

        float x = page.getPageSize().getWidth() - 40;
        float y = 10;
        float fontSize = 10;

        new Canvas(canvas, page.getPageSize())
                .showTextAligned(new Paragraph().add(new Text("Page " + pageNumber + " of " + totalPages)
                                .setFontSize(fontSize))
                        .setMargin(0)
                        .setTextAlignment(TextAlignment.RIGHT), x, y, TextAlignment.RIGHT);
    }
}
