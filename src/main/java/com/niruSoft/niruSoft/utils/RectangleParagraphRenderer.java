package com.niruSoft.niruSoft.utils;

import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.ParagraphRenderer;

import java.awt.*;

class RectangleParagraphRenderer extends ParagraphRenderer {
    private Color borderColor;
    private float borderWidth;

    public RectangleParagraphRenderer(Paragraph modelElement, Color borderColor, float borderWidth) {
        super(modelElement);
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
    }

    @Override
    public void drawBackground(DrawContext drawContext) {
        super.drawBackground(drawContext);
        float x = getOccupiedAreaBBox().getX() + borderWidth / 2;
        float y = getOccupiedAreaBBox().getY() + borderWidth / 2;
        float width = getOccupiedAreaBBox().getWidth() - borderWidth;
        float height = getOccupiedAreaBBox().getHeight() - borderWidth;
        PdfCanvas canvas = drawContext.getCanvas();
        canvas.saveState()
                .setLineWidth(borderWidth)
                .rectangle(x, y, width, height)
                .stroke()
                .restoreState();
    }
}