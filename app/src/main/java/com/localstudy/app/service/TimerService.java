package com.localstudy.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.app.Service;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.localstudy.app.MainActivity;
import com.localstudy.app.R;
import com.localstudy.app.data.StudyDatabase;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.util.DateUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerService extends Service {
    public static final String ACTION_START = "com.localstudy.app.timer.START";
    public static final String ACTION_PAUSE = "com.localstudy.app.timer.PAUSE";
    public static final String ACTION_RESUME = "com.localstudy.app.timer.RESUME";
    public static final String ACTION_STOP = "com.localstudy.app.timer.STOP";
    public static final String ACTION_TIMEOUT = "com.localstudy.app.timer.TIMEOUT";

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBJECT = "subject";
    public static final String EXTRA_SUBJECT_ID = "subject_id";
    public static final String EXTRA_SECTION_ID = "section_id";
    public static final String EXTRA_TODO_ID = "todo_id";
    public static final String EXTRA_REPEAT_ID = "repeat_id";

    private static final String CHANNEL_ID = "study_timer";
    private static final int NOTIFICATION_ID = 1101;
    private static final long MAX_POSITIVE_SECONDS = 3 * 60 * 60;
    private static final int TIMEOUT_REQUEST_CODE = 1991;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private boolean finishing;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!prefs.getBoolean(TimerState.KEY_ACTIVE, false) || finishing) {
                return;
            }
            TimerState.Snapshot snapshot = TimerState.read(TimerService.this);
            if (snapshot.running) {
                boolean reachedCountdown = "countdown".equals(snapshot.mode)
                        && snapshot.durationSec > 0
                        && snapshot.elapsedSec >= snapshot.durationSec;
                boolean reachedPositiveCap = "positive".equals(snapshot.mode)
                        && snapshot.elapsedSec >= MAX_POSITIVE_SECONDS;
                if (reachedCountdown || reachedPositiveCap) {
                    finishAndSave(true);
                    return;
                }
            }
            updateNotification(snapshot);
            handler.postDelayed(this, 1000);
        }
    };

    public static void start(Context context, String mode, long durationSec, String title,
                             String subjectName, Long subjectId, Long sectionId,
                             Long todoId, Long repeatId) {
        Intent intent = new Intent(context, TimerService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_DURATION, durationSec);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBJECT, subjectName);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId == null ? -1 : subjectId);
        intent.putExtra(EXTRA_SECTION_ID, sectionId == null ? -1 : sectionId);
        intent.putExtra(EXTRA_TODO_ID, todoId == null ? -1 : todoId);
        intent.putExtra(EXTRA_REPEAT_ID, repeatId == null ? -1 : repeatId);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void sendAction(Context context, String action) {
        Intent intent = new Intent(context, TimerService.class).setAction(action);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = TimerState.prefs(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            startNewTimer(intent);
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_RESUME.equals(action)) {
            resume();
        } else if (ACTION_STOP.equals(action)) {
            finishAndSave(false);
        } else if (ACTION_TIMEOUT.equals(action)) {
            finishAndSave(true);
        } else if (prefs.getBoolean(TimerState.KEY_ACTIVE, false)) {
            startForeground(NOTIFICATION_ID, buildNotification(TimerState.read(this)));
            scheduleTicker();
            scheduleTimeoutAlarm();
        } else {
            stopSelf();
        }
        return START_STICKY;
    }

    private void startNewTimer(Intent intent) {
        if (prefs.getBoolean(TimerState.KEY_ACTIVE, false)) {
            return;
        }
        String mode = intent.getStringExtra(EXTRA_MODE);
        long duration = intent.getLongExtra(EXTRA_DURATION, 0);
        long now = System.currentTimeMillis();
        prefs.edit()
                .clear()
                .putBoolean(TimerState.KEY_ACTIVE, true)
                .putBoolean(TimerState.KEY_RUNNING, true)
                .putString(TimerState.KEY_MODE, "countdown".equals(mode) ? "countdown" : "positive")
                .putLong(TimerState.KEY_DURATION, Math.max(0, duration))
                .putLong(TimerState.KEY_ELAPSED, 0)
                .putLong(TimerState.KEY_RESUMED_AT, now)
                .putLong(TimerState.KEY_STARTED_AT, now)
                .putString(TimerState.KEY_TITLE, value(intent.getStringExtra(EXTRA_TITLE)))
                .putString(TimerState.KEY_SUBJECT, value(intent.getStringExtra(EXTRA_SUBJECT)))
                .putLong(TimerState.KEY_SUBJECT_ID, intent.getLongExtra(EXTRA_SUBJECT_ID, -1))
                .putLong(TimerState.KEY_SECTION_ID, intent.getLongExtra(EXTRA_SECTION_ID, -1))
                .putLong(TimerState.KEY_TODO_ID, intent.getLongExtra(EXTRA_TODO_ID, -1))
                .putLong(TimerState.KEY_REPEAT_ID, intent.getLongExtra(EXTRA_REPEAT_ID, -1))
                .apply();
        finishing = false;
        startForeground(NOTIFICATION_ID, buildNotification(TimerState.read(this)));
        scheduleTicker();
        scheduleTimeoutAlarm();
    }

    private void pause() {
        if (!prefs.getBoolean(TimerState.KEY_ACTIVE, false)
                || !prefs.getBoolean(TimerState.KEY_RUNNING, false)) {
            return;
        }
        TimerState.Snapshot snapshot = TimerState.read(this);
        prefs.edit()
                .putBoolean(TimerState.KEY_RUNNING, false)
                .putLong(TimerState.KEY_ELAPSED, snapshot.elapsedSec)
                .putLong(TimerState.KEY_RESUMED_AT, 0)
                .apply();
        cancelTimeoutAlarm();
        updateNotification(TimerState.read(this));
    }

    private void resume() {
        if (!prefs.getBoolean(TimerState.KEY_ACTIVE, false)
                || prefs.getBoolean(TimerState.KEY_RUNNING, false)) {
            return;
        }
        prefs.edit()
                .putBoolean(TimerState.KEY_RUNNING, true)
                .putLong(TimerState.KEY_RESUMED_AT, System.currentTimeMillis())
                .apply();
        scheduleTicker();
        scheduleTimeoutAlarm();
    }

    private void finishAndSave(boolean silentAutoStop) {
        if (finishing) {
            return;
        }
        TimerState.Snapshot snapshot = TimerState.read(this);
        if (!snapshot.active) {
            stopSelf();
            return;
        }
        finishing = true;
        long duration = Math.max(0, snapshot.elapsedSec);
        if ("positive".equals(snapshot.mode)) {
            duration = Math.min(duration, MAX_POSITIVE_SECONDS);
        } else if (snapshot.durationSec > 0) {
            duration = Math.min(duration, snapshot.durationSec);
        }

        StudyRecord record = new StudyRecord();
        record.subjectId = snapshot.subjectId;
        record.subjectName = snapshot.subjectName == null || snapshot.subjectName.isEmpty()
                ? "其他" : snapshot.subjectName;
        record.sectionId = snapshot.sectionId;
        record.todoId = snapshot.todoId;
        record.repeatTaskId = snapshot.repeatId;
        record.startTime = DateUtils.clockText(snapshot.startedAt);
        record.endTime = DateUtils.nowText();
        record.durationSec = duration;

        prefs.edit().clear().apply();
        cancelTimeoutAlarm();
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        if (duration > 0) {
            io.execute(() -> StudyDatabase.get(this).dao().insertRecord(record));
        }
        stopForeground(true);
        stopSelf();
    }

    private void scheduleTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void scheduleTimeoutAlarm() {
        TimerState.Snapshot snapshot = TimerState.read(this);
        if (!snapshot.active || !snapshot.running) return;
        long remaining;
        if ("countdown".equals(snapshot.mode)) {
            remaining = Math.max(0, snapshot.durationSec - snapshot.elapsedSec);
        } else {
            remaining = Math.max(0, MAX_POSITIVE_SECONDS - snapshot.elapsedSec);
        }
        if (remaining <= 0) {
            finishAndSave(true);
            return;
        }
        Intent intent = new Intent(this, TimerService.class).setAction(ACTION_TIMEOUT);
        PendingIntent pending = PendingIntent.getService(
                this, TIMEOUT_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager manager = getSystemService(AlarmManager.class);
        if (manager == null) return;
        long triggerAt = System.currentTimeMillis() + remaining * 1000L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    private void cancelTimeoutAlarm() {
        AlarmManager manager = getSystemService(AlarmManager.class);
        if (manager == null) return;
        Intent intent = new Intent(this, TimerService.class).setAction(ACTION_TIMEOUT);
        PendingIntent pending = PendingIntent.getService(
                this, TIMEOUT_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.timer_channel_description));
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(TimerState.Snapshot snapshot) {
        String time = "countdown".equals(snapshot.mode)
                ? DateUtils.duration(snapshot.remainingSec())
                : DateUtils.duration(snapshot.elapsedSec);
        String title = snapshot.title == null || snapshot.title.isEmpty()
                ? snapshot.subjectName : snapshot.title;
        String body = (snapshot.subjectName == null ? "其他" : snapshot.subjectName)
                + " · " + time + (snapshot.running ? "" : " · 已暂停");

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setColor(Color.rgb(32, 122, 102))
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (snapshot.running) {
            builder.addAction(R.drawable.ic_timer, "暂停", serviceAction(ACTION_PAUSE, 1));
        } else {
            builder.addAction(R.drawable.ic_timer, "继续", serviceAction(ACTION_RESUME, 2));
        }
        builder.addAction(R.drawable.ic_chart, "结束", serviceAction(ACTION_STOP, 3));
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int code) {
        Intent intent = new Intent(this, TimerService.class).setAction(action);
        return PendingIntent.getService(
                this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void updateNotification(TimerState.Snapshot snapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(snapshot));
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        io.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
