package com.niruSoft.niruSoft.utils;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.renderer.AbstractRenderer;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.IRenderer;

public class CustomCellRenderer extends CellRenderer {
    private Color customBorderColor;

    public CustomCellRenderer(Cell modelElement, Color customBorderColor) {
        super(modelElement);
        this.customBorderColor = customBorderColor;
    }
    @Override
    public IRenderer getNextRenderer() {
        return new CustomCellRenderer((Cell) modelElement, customBorderColor);
    }

    @Override
    protected CellRenderer createOverflowRenderer(int layoutResult){
        return new CustomCellRenderer((Cell) modelElement, customBorderColor);
    }


    @Override
    public void draw(DrawContext drawContext) {
        super.draw(drawContext);

        float x1 = getOccupiedAreaBBox().getLeft();
        float x2 = getOccupiedAreaBBox().getRight();
        float y = getOccupiedAreaBBox().getBottom();

        PdfCanvas canvas = drawContext.getCanvas();
        canvas.setStrokeColor(customBorderColor);
        canvas.setLineWidth(1);
        canvas.moveTo(x1, y).lineTo(x2, y).stroke();
    }
}