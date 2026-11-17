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

public class CourseReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "course_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String type = intent.getStringExtra(CourseReminderScheduler.EXTRA_TYPE);
        String title = intent.getStringExtra(CourseReminderScheduler.EXTRA_TITLE);
        String text = intent.getStringExtra(CourseReminderScheduler.EXTRA_TEXT);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "课程表提醒", NotificationManager.IMPORTANCE_HIGH
            ));
        }
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context, 6200, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle(title == null ? "课程提醒" : title)
                .setContentText(text == null ? "" : text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text == null ? "" : text))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);
        int id = CourseReminderScheduler.TYPE_SUMMARY.equals(type) ? 6201
                : 6300 + Math.abs((String.valueOf(title) + text).hashCode() % 5000);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }
}
