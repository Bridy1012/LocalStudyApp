package com.localstudy.app.service;

import android.content.Context;
import android.content.SharedPreferences;

public final class TimerState {
    static final String PREFS = "active_timer";
    static final String KEY_ACTIVE = "active";
    static final String KEY_RUNNING = "running";
    static final String KEY_MODE = "mode";
    static final String KEY_DURATION = "duration";
    static final String KEY_ELAPSED = "elapsed";
    static final String KEY_RESUMED_AT = "resumed_at";
    static final String KEY_STARTED_AT = "started_at";
    static final String KEY_TITLE = "title";
    static final String KEY_SUBJECT = "subject_name";
    static final String KEY_SUBJECT_ID = "subject_id";
    static final String KEY_SECTION_ID = "section_id";
    static final String KEY_TODO_ID = "todo_id";
    static final String KEY_REPEAT_ID = "repeat_id";

    private TimerState() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Snapshot read(Context context) {
        SharedPreferences p = prefs(context);
        Snapshot snapshot = new Snapshot();
        snapshot.active = p.getBoolean(KEY_ACTIVE, false);
        snapshot.running = p.getBoolean(KEY_RUNNING, false);
        snapshot.mode = p.getString(KEY_MODE, "positive");
        snapshot.durationSec = p.getLong(KEY_DURATION, 0);
        snapshot.elapsedSec = p.getLong(KEY_ELAPSED, 0);
        snapshot.resumedAt = p.getLong(KEY_RESUMED_AT, 0);
        snapshot.startedAt = p.getLong(KEY_STARTED_AT, 0);
        snapshot.title = p.getString(KEY_TITLE, "");
        snapshot.subjectName = p.getString(KEY_SUBJECT, "其他");
        snapshot.subjectId = nullableLong(p.getLong(KEY_SUBJECT_ID, -1));
        snapshot.sectionId = nullableLong(p.getLong(KEY_SECTION_ID, -1));
        snapshot.todoId = nullableLong(p.getLong(KEY_TODO_ID, -1));
        snapshot.repeatId = nullableLong(p.getLong(KEY_REPEAT_ID, -1));
        if (snapshot.active && snapshot.running && snapshot.resumedAt > 0) {
            snapshot.elapsedSec += Math.max(0, (System.currentTimeMillis() - snapshot.resumedAt) / 1000);
        }
        return snapshot;
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    private static Long nullableLong(long value) {
        return value < 0 ? null : value;
    }

    public static class Snapshot {
        public boolean active;
        public boolean running;
        public String mode = "positive";
        public long durationSec;
        public long elapsedSec;
        public long resumedAt;
        public long startedAt;
        public String title = "";
        public String subjectName = "其他";
        public Long subjectId;
        public Long sectionId;
        public Long todoId;
        public Long repeatId;

        public long remainingSec() {
            return Math.max(0, durationSec - elapsedSec);
        }
    }
}
