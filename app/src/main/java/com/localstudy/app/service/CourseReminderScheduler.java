package com.localstudy.app.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.localstudy.app.data.CalendarMark;
import com.localstudy.app.data.Course;
import com.localstudy.app.data.CourseAdjustment;
import com.localstudy.app.data.Semester;
import com.localstudy.app.data.StudyDao;
import com.localstudy.app.data.StudyDatabase;
import com.localstudy.app.util.SettingsStore;
import com.localstudy.app.util.TimetableUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CourseReminderScheduler {
    public static final String EXTRA_TYPE = "course_notice_type";
    public static final String EXTRA_TITLE = "course_notice_title";
    public static final String EXTRA_TEXT = "course_notice_text";
    public static final String TYPE_SUMMARY = "summary";
    public static final String TYPE_CLASS = "class";

    private static final int MAX_DAYS_AHEAD = 140;
    private static final String PREFS = "course_reminder_registry";
    private static final String KEY_ALARMS = "registered_alarms";

    private CourseReminderScheduler() {
    }

    public static void rescheduleAll(Context context) {
        rescheduleAll(context, null);
    }

    public static void rescheduleAll(Context context, Runnable done) {
        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            try {
                cancelRegistered(context);
                StudyDao dao = StudyDatabase.get(context).dao();
                List<Semester> semesters = dao.getAllSemestersSync();
                Semester active = activeSemester(semesters);
                if (active == null) return;
                List<Course> courses = dao.getAllCoursesSync();
                List<CourseAdjustment> adjustments = dao.getAllCourseAdjustmentsSync();
                List<CalendarMark> marks = dao.getAllCalendarMarksSync();

                LocalDate today = LocalDate.now();
                int scheduleDays = MAX_DAYS_AHEAD;
                try {
                    LocalDate semesterStart = LocalDate.parse(active.startDate);
                    LocalDate semesterEnd = semesterStart.plusWeeks(active.totalWeeks);
                    scheduleDays = (int) Math.max(1, Math.min(
                            MAX_DAYS_AHEAD,
                            java.time.temporal.ChronoUnit.DAYS.between(today, semesterEnd) + 1
                    ));
                } catch (Exception ignored) {
                }
                for (int day = 0; day < scheduleDays; day++) {
                    LocalDate date = today.plusDays(day);
                    for (Course course : courses) {
                        cancel(context, date, course.id, false);
                    }
                    cancel(context, date, 0, true);
                }

                for (int day = 0; day < scheduleDays; day++) {
                    LocalDate date = today.plusDays(day);
                    List<TimetableUtils.Occurrence> occurrences = TimetableUtils.resolveDay(
                            active, courses, adjustments, marks, date
                    );
                    if (occurrences.isEmpty()) continue;

                    if (SettingsStore.courseSummary(context)
                            && occurrences.get(0).start.isBefore(java.time.LocalTime.NOON)) {
                        scheduleSummary(context, date, occurrences);
                    }
                    if (SettingsStore.courseReminder(context)) {
                        int minutes = SettingsStore.courseReminderMinutes(context);
                        for (TimetableUtils.Occurrence occurrence : occurrences) {
                            scheduleClass(context, occurrence, minutes);
                        }
                    }
                }
            } finally {
                io.shutdown();
                if (done != null) done.run();
            }
        });
    }

    private static void scheduleSummary(Context context, LocalDate date,
                                        List<TimetableUtils.Occurrence> occurrences) {
        LocalDateTime trigger = LocalDateTime.of(date, occurrences.get(0).start).minusMinutes(30);
        if (!trigger.isAfter(LocalDateTime.now())) return;
        StringBuilder text = new StringBuilder();
        for (TimetableUtils.Occurrence occurrence : occurrences) {
            if (text.length() > 0) text.append('\n');
            text.append(occurrence.start).append('-').append(occurrence.end).append(' ')
                    .append(occurrence.course.name);
            if (!occurrence.course.classroom.isEmpty()) {
                text.append(" · ").append(occurrence.course.classroom);
            }
            if (!occurrence.course.teacher.isEmpty()) {
                text.append(" · ").append(occurrence.course.teacher);
            }
        }
        String title = occurrences.get(0).start + " 第一节 · 今日 " + occurrences.size() + " 门课";
        schedule(context, trigger, TYPE_SUMMARY, title, text.toString(), 0, date);
    }

    private static void scheduleClass(Context context,
                                      TimetableUtils.Occurrence occurrence,
                                      int minutesBefore) {
        LocalDateTime trigger = LocalDateTime.of(occurrence.date, occurrence.start)
                .minusMinutes(minutesBefore);
        if (!trigger.isAfter(LocalDateTime.now())) return;
        String text = occurrence.start + "-" + occurrence.end;
        if (!occurrence.course.classroom.isEmpty()) {
            text += " · " + occurrence.course.classroom;
        }
        if (!occurrence.course.teacher.isEmpty()) {
            text += " · " + occurrence.course.teacher;
        }
        schedule(context, trigger, TYPE_CLASS, occurrence.course.name, text,
                occurrence.course.id, occurrence.date);
    }

    private static void schedule(Context context, LocalDateTime trigger, String type,
                                 String title, String text, long courseId, LocalDate date) {
        Intent intent = new Intent(context, CourseReminderReceiver.class)
                .setAction("course." + type + "." + date + "." + courseId)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text);
        int requestCode = requestCode(date, courseId, TYPE_SUMMARY.equals(type));
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long millis = trigger.atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_ALARMS, "");
        String value = requestCode + "|" + type + "|" + date + "|" + courseId;
        if (!existing.contains(value)) {
            prefs.edit().putString(KEY_ALARMS,
                    existing.isEmpty() ? value : existing + "\n" + value).apply();
        }
    }

    private static void cancelRegistered(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String registered = prefs.getString(KEY_ALARMS, "");
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager != null && !registered.isEmpty()) {
            for (String item : registered.split("\\n")) {
                String[] parts = item.split("\\|");
                if (parts.length != 4) continue;
                try {
                    int requestCode = Integer.parseInt(parts[0]);
                    Intent intent = new Intent(context, CourseReminderReceiver.class)
                            .setAction("course." + parts[1] + "." + parts[2] + "." + parts[3]);
                    PendingIntent pending = PendingIntent.getBroadcast(
                            context, requestCode, intent,
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                    );
                    if (pending != null) {
                        manager.cancel(pending);
                        pending.cancel();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        prefs.edit().remove(KEY_ALARMS).apply();
    }

    private static void cancel(Context context, LocalDate date, long courseId, boolean summary) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        String type = summary ? TYPE_SUMMARY : TYPE_CLASS;
        Intent intent = new Intent(context, CourseReminderReceiver.class)
                .setAction("course." + type + "." + date + "." + courseId);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode(date, courseId, summary), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    private static int requestCode(LocalDate date, long courseId, boolean summary) {
        long value = date.toEpochDay() * 131L + courseId * 17L + (summary ? 7 : 3);
        return (int) (value & 0x7fffffff);
    }

    public static Semester activeSemester(List<Semester> semesters) {
        for (Semester semester : semesters) if (semester.isActive) return semester;
        return semesters.isEmpty() ? null : semesters.get(0);
    }
}
