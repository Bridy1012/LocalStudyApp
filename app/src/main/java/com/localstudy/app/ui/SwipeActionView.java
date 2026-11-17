package com.localstudy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.localstudy.app.util.Ui;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

public class SwipeActionView extends FrameLayout {
    public static class Action {
        public final String label;
        public final int color;
        public final Runnable callback;

        public Action(String label, int color, Runnable callback) {
            this.label = label;
            this.color = color;
            this.callback = callback;
        }
    }

    private static WeakReference<SwipeActionView> currentOpen = new WeakReference<>(null);

    private final LinearLayout actions;
    private final int touchSlop;
    private final int minFlingVelocity;
    private View content;
    private VelocityTracker velocityTracker;
    private float downX;
    private float downY;
    private float startTranslation;
    private boolean dragging;
    private boolean open;
    private boolean interceptForOpenTap;
    private int actionWidth;
    private ScrollWatcher scrollWatcher;

    public SwipeActionView(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setClipChildren(true);
        actions.setAlpha(0f);
        actions.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    public void setContent(View view) {
        removeAllViews();
        content = view;
        addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        // Actions must be added after content so the card paints above them.
        addView(actions, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
        ));
    }

    public void setActions(Action... values) {
        setActions(Arrays.asList(values));
    }

    public void setActions(List<Action> values) {
        actions.removeAllViews();
        actionWidth = values.size() * Ui.dp(getContext(), 68);
        for (Action action : values) {
            TextView button = Ui.text(getContext(), action.label, 10, action.color, true);
            button.setGravity(Gravity.CENTER);
            button.setSingleLine(true);
            button.setBackgroundColor(Ui.withAlpha(action.color, 34));
            button.setOnClickListener(view -> {
                close(false);
                action.callback.run();
            });
            actions.addView(button, new LinearLayout.LayoutParams(
                    Ui.dp(getContext(), 68), ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
        ViewGroup.LayoutParams params = actions.getLayoutParams();
        if (params != null) {
            params.width = actionWidth;
            actions.setLayoutParams(params);
        }
        setActionsInteractive(false);
    }

    private void setActionsInteractive(boolean interactive) {
        actions.setAlpha(interactive ? 1f : 0f);
        actions.setImportantForAccessibility(interactive
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            child.setEnabled(interactive);
            child.setClickable(interactive);
            child.setFocusable(interactive);
        }
    }

    private void reveal(float amount) {
        float progress = Math.max(0f, Math.min(1f, Math.abs(amount) / Math.max(1, actionWidth)));
        if (progress > 0f) {
            actions.setAlpha(Math.min(1f, progress * 2f));
            actions.setImportantForAccessibility(progress > 0.15f
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (int i = 0; i < actions.getChildCount(); i++) {
                View child = actions.getChildAt(i);
                child.setClickable(progress > 0.85f);
                child.setFocusable(progress > 0.85f);
            }
        }
    }

    private void openBySwipe() {
        if (content == null) return;
        SwipeActionView previous = currentOpen.get();
        if (previous != null && previous != this) previous.close(false);
        currentOpen = new WeakReference<>(this);
        open = true;
        content.animate()
                .translationX(-actionWidth)
                .setDuration(180)
                .withEndAction(() -> setActionsInteractive(true))
                .start();
    }

    private void close(boolean animate) {
        if (content == null) return;
        open = false;
        setActionsInteractive(false);
        SwipeActionView previous = currentOpen.get();
        if (previous == this) currentOpen = new WeakReference<>(null);
        if (animate) {
            content.animate().translationX(0f).setDuration(170).start();
        } else {
            content.setTranslationX(0f);
        }
    }

    private void closeOtherRows() {
        SwipeActionView previous = currentOpen.get();
        if (previous != null && previous != this) previous.close(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            closeOtherRows();
            downX = event.getX();
            downY = event.getY();
            startTranslation = content == null ? 0f : content.getTranslationX();
            dragging = false;
            interceptForOpenTap = open
                    && event.getX() < getWidth() - actionWidth;
            if (interceptForOpenTap) return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                dragging = true;
                ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (content == null) return false;
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                startTranslation = content.getTranslationX();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    float translation = Math.max(-actionWidth,
                            Math.min(0f, startTranslation + dx));
                    content.setTranslationX(translation);
                    reveal(translation);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (interceptForOpenTap) {
                    interceptForOpenTap = false;
                    close(true);
                    recycleVelocity();
                    return true;
                }
                float translation = content.getTranslationX();
                float velocityX = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityX = velocityTracker.getXVelocity();
                }
                boolean shouldOpen = velocityX < -minFlingVelocity
                        || (velocityX <= minFlingVelocity
                        && translation < -actionWidth * 0.32f);
                if (shouldOpen) openBySwipe();
                else close(true);
                recycleVelocity();
                ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void recycleVelocity() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent parent = getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        if (parent instanceof View) {
            scrollWatcher = new ScrollWatcher((View) parent);
            ((View) parent).getViewTreeObserver()
                    .addOnScrollChangedListener(scrollWatcher);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (scrollWatcher != null && scrollWatcher.view.getViewTreeObserver().isAlive()) {
            scrollWatcher.view.getViewTreeObserver()
                    .removeOnScrollChangedListener(scrollWatcher);
        }
        recycleVelocity();
        close(false);
        super.onDetachedFromWindow();
    }

    private class ScrollWatcher implements ViewTreeObserver.OnScrollChangedListener {
        final View view;

        ScrollWatcher(View view) {
            this.view = view;
        }

        @Override
        public void onScrollChanged() {
            SwipeActionView current = currentOpen.get();
            if (current != null) current.close(true);
        }
    }

    @Override
    public boolean performClick() {
        if (open) {
            close(true);
            return true;
        }
        return super.performClick();
    }
}
