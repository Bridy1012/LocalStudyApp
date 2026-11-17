package com.localstudy.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.localstudy.app.util.Ui;

public class RingProgressView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int finished;
    private int total;

    public RingProgressView(Context context) {
        this(context, null);
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(Ui.dp(context, 4));
        track.setColor(Ui.border(context));
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeWidth(Ui.dp(context, 4));
        progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(Ui.accent(context));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Ui.dp(context, 11));
        label.setColor(Ui.textColor(context));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public void setProgress(int finished, int total) {
        this.finished = Math.max(0, finished);
        this.total = Math.max(0, total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = Ui.dp(getContext(), 4);
        float inset = stroke / 2f + Ui.dp(getContext(), 1);
        oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(oval, 0, 360, false, track);
        if (total > 0) {
            canvas.drawArc(oval, -90, 360f * finished / total, false, progress);
        }
        String value = total == 0 ? "--" : Math.round(finished * 100f / total) + "%";
        Paint.FontMetrics metrics = label.getFontMetrics();
        float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, getWidth() / 2f, baseline, label);
    }
}
