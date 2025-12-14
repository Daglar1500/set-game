package com.example.gameset1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SetCardView extends View {
    private Card card;
    private boolean isSelected = false;
    private final Paint paint = new Paint();
    private final Paint stripedPaint = new Paint();
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    private final int COLOR_RED = Color.rgb(230, 0, 0);
    private final int COLOR_GREEN = Color.rgb(0, 150, 0);
    private final int COLOR_PURPLE = Color.rgb(140, 0, 200);

    public SetCardView(Context context) { super(context); init(); }
    public SetCardView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        stripedPaint.setStyle(Paint.Style.STROKE);
        stripedPaint.setStrokeWidth(3);
        stripedPaint.setAntiAlias(true);
        paint.setAntiAlias(true);
    }

    public void setCard(Card card) {
        this.card = card;
        invalidate();
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        invalidate();
    }

    public Card getCard() {
        return card;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawColor(isSelected ? Color.LTGRAY : Color.WHITE);

        // Draw border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        if (card == null) return;

        // Set Color
        int drawColor;
        if (card.color == 0) drawColor = COLOR_RED;
        else if (card.color == 1) drawColor = COLOR_GREEN;
        else drawColor = COLOR_PURPLE;

        paint.setColor(drawColor);
        stripedPaint.setColor(drawColor);

        // --- FIXED DIMENSIONS TO PREVENT OVERFLOW ---
        float w = getWidth();
        float h = getHeight();

        // Previous logic (h/4) made total height = h, causing clipping.
        // New logic: h/5. Total height = 3*(h/5) + 2*(h/10) = 0.8h.
        // This leaves 10% padding on top and 10% on bottom.
        float shapeH = h / 5;
        float gap = shapeH / 2;

        // Use proportional width instead of fixed -20 pixels
        // CHANGED: Reduced from 0.4f (80% total width) to 0.3f (60% total width)
        float shapeW = w * 0.3f;

        // Logic to center 1, 2, or 3 shapes
        float totalH = (card.number + 1) * shapeH + (card.number) * gap;

        // This centers the block of shapes vertically within the card
        float startY = (h - totalH) / 2 + shapeH/2;

        for (int i = 0; i <= card.number; i++) {
            // Calculate center Y for this specific shape
            float cy = startY + i * (shapeH + gap);
            drawShape(canvas, w / 2, cy, shapeW, shapeH / 2, drawColor);
        }
    }

    private void drawShape(Canvas canvas, float cx, float cy, float rw, float rh, int color) {
        path.reset();
        if (card.shape == 0) { // Oval
            rectF.set(cx - rw, cy - rh, cx + rw, cy + rh);
            path.addOval(rectF, Path.Direction.CW);
        } else if (card.shape == 1) { // Diamond
            path.moveTo(cx, cy - rh);
            path.lineTo(cx + rw, cy);
            path.lineTo(cx, cy + rh);
            path.lineTo(cx - rw, cy);
            path.close();
        } else { // Rect
            rectF.set(cx - rw, cy - rh, cx + rw, cy + rh);
            path.addRect(rectF, Path.Direction.CW);
        }

        if (card.shading == 0) { // SOLID
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
        } else if (card.shading == 1) { // STRIPED
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            canvas.drawPath(path, paint);

            canvas.save();
            canvas.clipPath(path);

            float step = 15;
            // Ensure lines cover the entire rotated bounding box
            for (float i = cx - rw - rh; i < cx + rw + rh; i += step) {
                canvas.drawLine(i, cy + rh, i + 2 * rh, cy - rh, stripedPaint);
            }
            canvas.restore();

        } else { // OPEN
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            canvas.drawPath(path, paint);
        }
    }
}