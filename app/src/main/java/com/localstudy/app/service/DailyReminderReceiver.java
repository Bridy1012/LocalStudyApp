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
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.StudyDao;
import com.localstudy.app.data.StudyDatabase;
import com.localstudy.app.data.Todo;
import com.localstudy.app.util.DateUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DailyReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "daily_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderScheduler.schedule(context);
        if (!ReminderSchedulerAllowed(context)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            try {
                StudyDao dao = StudyDatabase.get(context).dao();
                String today = DateUtils.today();
                LocalDate day = LocalDate.now();
                int openTasks = 0;
                for (Section section : dao.getAllSectionsSync()) {
                    if (!section.isFinished && !section.isSkipped
                            && section.planDate != null && section.planDate.compareTo(today) <= 0) {
                        openTasks++;
                    }
                }
                for (RepeatTask task : dao.getAllRepeatTasksSync()) {
                    if (DateUtils.repeatOccursOn(task, day)
                            && !today.equals(task.lastCompletedDate)) {
                        openTasks++;
                    }
                }
                int overdue = 0;
                for (Todo todo : dao.getAllTodosSync()) {
                    if (!todo.isFinished && DateUtils.isValidDate(todo.ddl)
                            && todo.ddl.compareTo(today) < 0) {
                        overdue++;
                    }
                }
                show(context, openTasks, overdue);
            } finally {
                io.shutdown();
                pendingResult.finish();
            }
        });
    }

    private boolean ReminderSchedulerAllowed(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void show(Context context, int tasks, int overdue) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String text = "今天还有 " + tasks + " 项任务";
        if (overdue > 0) {
            text += "，其中 " + overdue + " 个待办已逾期";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("给今天收个尾")
                .setContentText(text)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(2202, builder.build());
    }
}
