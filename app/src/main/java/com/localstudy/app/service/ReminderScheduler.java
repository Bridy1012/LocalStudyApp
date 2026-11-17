package com.localstudy.app.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.SettingsStore;

public final class ReminderScheduler {
    private ReminderScheduler() {
    }

    public static void schedule(Context context) {
        if (!SettingsStore.reminder(context)) {
            cancel(context);
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        long triggerAt = System.currentTimeMillis()
                + DateUtils.secondsUntilNextReminder(SettingsStore.reminderHour(context)) * 1000L;
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(context));
    }

    public static void cancel(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(pending(context));
    }

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, DailyReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                2201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
