package com.localstudy.app.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.localstudy.app.R;

public final class Ui {
    private static final int[] CHART_COLORS = {
            0xFF207A66, 0xFF356AA8, 0xFFC66535, 0xFF985D91,
            0xFFAD842B, 0xFF4B7E7B, 0xFFC24F62, 0xFF687E45
    };

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int color(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0
                ? ContextCompat.getColor(context, value.resourceId)
                : value.data;
    }

    public static int bg(Context context) {
        return color(context, R.attr.studyBackground);
    }

    public static int surfaceColor(Context context) {
        return color(context, R.attr.studySurface);
    }

    public static int surfaceAlt(Context context) {
        return color(context, R.attr.studySurfaceAlt);
    }

    public static int textColor(Context context) {
        return color(context, R.attr.studyText);
    }

    public static int muted(Context context) {
        return color(context, R.attr.studyMuted);
    }

    public static int border(Context context) {
        return color(context, R.attr.studyBorder);
    }

    public static int danger(Context context) {
        return color(context, R.attr.studyDanger);
    }

    public static int success(Context context) {
        return color(context, R.attr.studySuccess);
    }

    public static int accent(Context context) {
        return color(context, androidx.appcompat.R.attr.colorAccent);
    }

    public static int withAlpha(int input, int alpha) {
        return Color.argb(alpha, Color.red(input), Color.green(input), Color.blue(input));
    }

    public static int chartColor(int index) {
        return CHART_COLORS[Math.abs(index) % CHART_COLORS.length];
    }

    public static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.12f);
        if (bold) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return text;
    }

    public static TextView title(Context context, String value) {
        return text(context, value, 26, textColor(context), true);
    }

    public static TextView sectionTitle(Context context, String value) {
        TextView view = text(context, value, 15, textColor(context), true);
        view.setLetterSpacing(0);
        return view;
    }

    public static TextView caption(Context context, String value) {
        return text(context, value, 12, muted(context), false);
    }

    public static GradientDrawable rounded(Context context, int fill, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(Context context, int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = rounded(context, fill, radiusDp);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static LinearLayout surface(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        layout.setBackground(outlined(context, surfaceColor(context), border(context), 8));
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static Space space(Context context, int dp) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, dp)));
        return space;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(context, 40));
        button.setMinWidth(0);
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackground(rounded(context, accent(context), 8));
        } else {
            button.setTextColor(textColor(context));
            button.setBackground(outlined(context, surfaceColor(context), border(context), 8));
        }
        return button;
    }

    public static ImageButton iconButton(Context context, int drawable, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        button.setColorFilter(textColor(context));
        button.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        button.setBackground(rounded(context, surfaceAlt(context), 8));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));
        return button;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(textColor(context));
        input.setHintTextColor(muted(context));
        input.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        input.setBackground(outlined(context, surfaceColor(context), border(context), 8));
        input.setMinHeight(dp(context, 46));
        return input;
    }

    public static EditText multilineInput(Context context, String hint) {
        EditText input = input(context, hint);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        input.setMaxLines(10);
        return input;
    }

    public static TextView pill(Context context, String label, int color) {
        TextView view = text(context, label, 11, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        view.setBackground(rounded(context, withAlpha(color, 28), 20));
        return view;
    }

    public static ProgressBar progress(Context context, int max, int value, int tint) {
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, max));
        bar.setProgress(Math.max(0, Math.min(max, value)));
        bar.setProgressTintList(ColorStateList.valueOf(tint));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(border(context)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 5)
        );
        bar.setLayoutParams(params);
        return bar;
    }

    public static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            params.setMargins(
                    dp(view.getContext(), left),
                    dp(view.getContext(), top),
                    dp(view.getContext(), right),
                    dp(view.getContext(), bottom)
            );
            view.setLayoutParams(params);
        }
    }

    public static void setFullWidth(View view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    public static void setWeight(View view, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight
        );
        view.setLayoutParams(params);
    }

    public static AlertDialog dialog(Context context, String title) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .create();
    }

    public static LinearLayout dialogBody(Context context) {
        LinearLayout layout = vertical(context);
        int pad = dp(context, 20);
        layout.setPadding(pad, dp(context, 4), pad, dp(context, 8));
        return layout;
    }

    public static TextView metric(Context context, String value, String label) {
        LinearLayout holder = vertical(context);
        TextView valueView = text(context, value, 20, textColor(context), true);
        TextView labelView = text(context, label, 11, muted(context), false);
        labelView.setPadding(0, dp(context, 2), 0, 0);
        holder.addView(valueView);
        holder.addView(labelView);
        return valueView;
    }

    public static ImageView navIcon(Context context, int resource) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(resource);
        icon.setColorFilter(muted(context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(params);
        return icon;
    }

    public static FrameLayout iconTile(Context context, int drawable, String label) {
        FrameLayout tile = new FrameLayout(context);
        tile.setBackground(rounded(context, accent(context), 8));
        ImageView icon = new ImageView(context);
        icon.setImageResource(drawable);
        icon.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(context, 22), dp(context, 22));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(icon, iconParams);
        tile.setContentDescription(label);
        return tile;
    }
}
