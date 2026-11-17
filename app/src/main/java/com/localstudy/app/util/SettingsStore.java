package com.localstudy.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class SettingsStore {
    private static final String PREFS = "study_settings";
    public static final String KEY_THEME = "theme";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_FONT_SCALE = "font_scale";
    public static final String KEY_REMINDER = "daily_reminder";
    public static final String KEY_REMINDER_HOUR = "reminder_hour";
    public static final String KEY_COURSE_SUMMARY = "course_summary_reminder";
    public static final String KEY_COURSE_REMINDER = "course_class_reminder";
    public static final String KEY_COURSE_REMINDER_MINUTES = "course_reminder_minutes";

    private SettingsStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String theme(Context context) {
        return prefs(context).getString(KEY_THEME, "system");
    }

    public static String accent(Context context) {
        return prefs(context).getString(KEY_ACCENT, "teal");
    }

    public static float fontScale(Context context) {
        return prefs(context).getFloat(KEY_FONT_SCALE, 1f);
    }

    public static boolean reminder(Context context) {
        return prefs(context).getBoolean(KEY_REMINDER, false);
    }

    public static int reminderHour(Context context) {
        return prefs(context).getInt(KEY_REMINDER_HOUR, 21);
    }

    public static boolean courseSummary(Context context) {
        return prefs(context).getBoolean(KEY_COURSE_SUMMARY, true);
    }

    public static boolean courseReminder(Context context) {
        return prefs(context).getBoolean(KEY_COURSE_REMINDER, true);
    }

    public static int courseReminderMinutes(Context context) {
        return prefs(context).getInt(KEY_COURSE_REMINDER_MINUTES, 10);
    }

    public static void applyNightMode(Context context) {
        String theme = theme(context);
        int mode;
        if ("light".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
