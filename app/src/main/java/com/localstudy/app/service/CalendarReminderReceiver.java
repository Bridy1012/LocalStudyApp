package com.localstudy.app.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.localstudy.app.MainActivity;
import com.localstudy.app.R;
import com.localstudy.app.data.CalendarEvent;
import com.localstudy.app.data.StudyDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "calendar_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        long eventId = intent.getLongExtra(CalendarReminderScheduler.EXTRA_EVENT_ID, -1);
        if (eventId <= 0) return;

        PendingResult result = goAsync();
        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            try {
                CalendarEvent event = StudyDatabase.get(context).dao()
                        .getCalendarEventSync(eventId);
                if (event != null) show(context, event);
            } finally {
                io.shutdown();
                result.finish();
            }
        });
    }

    private void show(Context context, CalendarEvent event) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "日历日程提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("按设定时间提醒日历中的日程");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context,
                5000,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String text = event.startAt;
        if (event.note != null && !event.note.isEmpty()) {
            text += " · " + event.note;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle(event.title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);
        NotificationManagerCompat.from(context)
                .notify(5000 + (int) (event.id % 100000), builder.build());
    }
}
