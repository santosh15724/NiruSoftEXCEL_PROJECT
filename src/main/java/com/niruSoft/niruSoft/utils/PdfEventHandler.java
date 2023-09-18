//package com.niruSoft.niruSoft.utils;
//
//import com.itextpdf.commons.actions.IEvent;
//import com.itextpdf.commons.actions.IEventHandler;
//import com.itextpdf.kernel.events.Event;
//import com.itextpdf.kernel.events.PdfDocumentEvent;
//import com.itextpdf.kernel.pdf.PdfPage;
//
//class PdfEventHandler implements IEventHandler {
//
//        public void handleEvent(Event event) {
//            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
//            PdfPage page = docEvent.getPage();
//            int pageNumber = docEvent.getDocument().getPageNumber(page);
//
//            if (pageNumber == 1) {
//                // Handle the first page separately
//                document.setMargins(2, 20, 2, 20); // Set top margin for the first page
//            }
//        }
//
//    @Override
//    public void onEvent(IEvent iEvent) {
//
//    }
//}