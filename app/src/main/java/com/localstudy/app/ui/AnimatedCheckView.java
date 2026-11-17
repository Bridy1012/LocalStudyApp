package com.localstudy.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.localstudy.app.util.Ui;

public class AnimatedCheckView extends View {
    public interface Listener {
        void onCheckedChanged(boolean checked);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path checkPath = new Path();
    private boolean checked;
    private float animation;
    private Listener listener;

    public AnimatedCheckView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        fill.setStyle(Paint.Style.FILL);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(context, 1.8f));
        border.setColor(Ui.muted(context));
        check.setStyle(Paint.Style.STROKE);
        check.setStrokeCap(Paint.Cap.ROUND);
        check.setStrokeJoin(Paint.Join.ROUND);
        check.setStrokeWidth(Ui.dp(context, 2.4f));
        check.setColor(android.graphics.Color.WHITE);
        setOnClickListener(view -> {
            checked = !checked;
            animateTo(checked, true);
            if (listener != null) listener.onCheckedChanged(checked);
        });
    }

    public void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    public void setChecked(boolean checked, boolean animate) {
        this.checked = checked;
        if (animate) {
            animateTo(checked, true);
        } else {
            animation = checked ? 1f : 0f;
            invalidate();
        }
        setContentDescription(checked ? "已完成" : "未完成");
    }

    public boolean isChecked() {
        return checked;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void animateTo(boolean target, boolean animate) {
        float from = animation;
        float to = target ? 1f : 0f;
        if (!animate) {
            animation = to;
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            animation = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
        setContentDescription(target ? "已完成" : "未完成");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Ui.dp(getContext(), 34);
        setMeasuredDimension(
                resolveSize(size, widthMeasureSpec),
                resolveSize(size, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        float scale = 0.8f + 0.2f * animation;
        if (checked && animation > 0.7f) {
            scale = 1f + (float) Math.sin((animation - 0.7f) / 0.3f * Math.PI) * 0.08f;
        }
        float size = side * 0.72f * scale;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        box.set(left, top, left + size, top + size);
        float radius = Ui.dp(getContext(), 5);

        if (animation > 0) {
            fill.setColor(Ui.accent(getContext()));
            fill.setAlpha((int) (255 * Math.min(1f, animation * 1.8f)));
            canvas.drawRoundRect(box, radius, radius, fill);
        }
        border.setColor(checked ? Ui.accent(getContext()) : Ui.muted(getContext()));
        canvas.drawRoundRect(box, radius, radius, border);

        if (checked && animation > 0.2f) {
            float progress = Math.min(1f, Math.max(0f, (animation - 0.2f) / 0.8f));
            float startX = box.left + size * 0.24f;
            float startY = box.top + size * 0.52f;
            float midX = box.left + size * 0.43f;
            float midY = box.top + size * 0.70f;
            float endX = box.left + size * 0.76f;
            float endY = box.top + size * 0.34f;
            checkPath.reset();
            checkPath.moveTo(startX, startY);
            float firstPart = Math.min(1f, progress / 0.45f);
            checkPath.lineTo(startX + (midX - startX) * firstPart,
                    startY + (midY - startY) * firstPart);
            if (progress > 0.45f) {
                float second = (progress - 0.45f) / 0.55f;
                checkPath.lineTo(midX + (endX - midX) * second,
                        midY + (endY - midY) * second);
            }
            canvas.drawPath(checkPath, check);
        }
    }
}
