package com.niruSoft.niruSoft.utils;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.element.ILeafElement;
import com.itextpdf.layout.renderer.IRenderer;

public class DottedLine implements ILeafElement {
    private final Color color;

    public DottedLine(Color color) {
        this.color = color;
    }


    public void draw(PdfCanvas canvas, float x, float y, float maxWidth, float maxHeight) {
        canvas.setStrokeColor(color);
        canvas.setLineWidth(1f); // Adjust the thickness as needed
        float gap = 2f; // Adjust the gap length as needed
        canvas.setLineDash(0, gap);
        canvas.moveTo(x, y);
        canvas.lineTo(x + maxWidth, y);
        canvas.stroke();
        canvas.setLineDash(0);
    }

    @Override
    public void setNextRenderer(IRenderer iRenderer) {

    }

    @Override
    public IRenderer getRenderer() {
        return null;
    }

    @Override
    public IRenderer createRendererSubTree() {
        return null;
    }

    @Override
    public boolean hasProperty(int i) {
        return false;
    }

    @Override
    public boolean hasOwnProperty(int i) {
        return false;
    }

    @Override
    public <T1> T1 getProperty(int i) {
        return null;
    }

    @Override
    public <T1> T1 getOwnProperty(int i) {
        return null;
    }

    @Override
    public <T1> T1 getDefaultProperty(int i) {
        return null;
    }

    @Override
    public void setProperty(int i, Object o) {

    }

    @Override
    public void deleteOwnProperty(int i) {

    }
}
