package com.localstudy.app.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.localstudy.app.data.CalendarEvent;
import com.localstudy.app.data.StudyDatabase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CalendarReminderScheduler {
    public static final String ACTION_REMIND = "com.localstudy.app.calendar.REMIND";
    public static final String EXTRA_EVENT_ID = "calendar_event_id";
    public static final String EXTRA_TITLE = "calendar_event_title";
    public static final String EXTRA_START = "calendar_event_start";

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CalendarReminderScheduler() {
    }

    public static void schedule(Context context, CalendarEvent event) {
        if (event == null || event.id <= 0 || event.reminderMinutes < 0) return;
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(event.startAt, FORMAT);
        } catch (Exception ignored) {
            return;
        }
        long triggerAt = start.minusMinutes(event.reminderMinutes)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (triggerAt <= System.currentTimeMillis()) return;

        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent pending = pendingIntent(context, event);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    public static void cancel(Context context, long eventId) {
        if (eventId <= 0) return;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        Intent intent = new Intent(context, CalendarReminderReceiver.class)
                .setAction(ACTION_REMIND + "." + eventId);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode(eventId),
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    public static void rescheduleAll(Context context) {
        rescheduleAll(context, null);
    }

    public static void rescheduleAll(Context context, Runnable done) {
        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            try {
                for (CalendarEvent event : StudyDatabase.get(context).dao()
                        .getAllCalendarEventsSync()) {
                    schedule(context, event);
                }
            } finally {
                io.shutdown();
                if (done != null) done.run();
            }
        });
    }

    private static PendingIntent pendingIntent(Context context, CalendarEvent event) {
        Intent intent = new Intent(context, CalendarReminderReceiver.class)
                .setAction(ACTION_REMIND + "." + event.id)
                .putExtra(EXTRA_EVENT_ID, event.id)
                .putExtra(EXTRA_TITLE, event.title)
                .putExtra(EXTRA_START, event.startAt);
        return PendingIntent.getBroadcast(
                context,
                requestCode(event.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int requestCode(long eventId) {
        return 4000 + (int) (eventId % 100000);
    }
}
