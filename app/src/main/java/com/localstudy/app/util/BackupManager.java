package com.localstudy.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.localstudy.app.data.BackupData;
import com.localstudy.app.data.StudyDao;

import java.util.ArrayList;
import java.util.Map;

public final class BackupManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupManager() {
    }

    public static String export(Context context, StudyDao dao) {
        BackupData data = new BackupData();
        data.exportedAt = DateUtils.nowText();
        data.subjects = new ArrayList<>(dao.getAllSubjectsSync());
        data.chapters = new ArrayList<>(dao.getAllChaptersSync());
        data.sections = new ArrayList<>(dao.getAllSectionsSync());
        data.todos = new ArrayList<>(dao.getAllTodosSync());
        data.repeatTasks = new ArrayList<>(dao.getAllRepeatTasksSync());
        data.records = new ArrayList<>(dao.getAllRecordsSync());
        data.calendarEvents = new ArrayList<>(dao.getAllCalendarEventsSync());
        data.semesters = new ArrayList<>(dao.getAllSemestersSync());
        data.courses = new ArrayList<>(dao.getAllCoursesSync());
        data.courseAdjustments = new ArrayList<>(dao.getAllCourseAdjustmentsSync());
        data.calendarMarks = new ArrayList<>(dao.getAllCalendarMarksSync());
        data.repeatCompletions = new ArrayList<>(dao.getAllRepeatCompletionsSync());
        SharedPreferences prefs = SettingsStore.prefs(context);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                data.preferences.put(entry.getKey(), String.valueOf(value));
            }
        }
        return GSON.toJson(data);
    }

    public static BackupData parse(String json) {
        BackupData data = GSON.fromJson(json, BackupData.class);
        if (data == null || data.schemaVersion < 1) {
            throw new IllegalArgumentException("不是有效的拾光学习备份文件");
        }
        if (data.subjects == null) data.subjects = new ArrayList<>();
        if (data.chapters == null) data.chapters = new ArrayList<>();
        if (data.sections == null) data.sections = new ArrayList<>();
        if (data.todos == null) data.todos = new ArrayList<>();
        if (data.repeatTasks == null) data.repeatTasks = new ArrayList<>();
        if (data.records == null) data.records = new ArrayList<>();
        if (data.calendarEvents == null) data.calendarEvents = new ArrayList<>();
        if (data.semesters == null) data.semesters = new ArrayList<>();
        if (data.courses == null) data.courses = new ArrayList<>();
        if (data.courseAdjustments == null) data.courseAdjustments = new ArrayList<>();
        if (data.calendarMarks == null) data.calendarMarks = new ArrayList<>();
        if (data.repeatCompletions == null) data.repeatCompletions = new ArrayList<>();
        return data;
    }

    public static void restore(Context context, StudyDao dao, BackupData data) {
        clearAll(dao);
        dao.restoreSubjects(data.subjects);
        dao.restoreChapters(data.chapters);
        dao.restoreSections(data.sections);
        dao.restoreTodos(data.todos);
        dao.restoreRepeatTasks(data.repeatTasks);
        dao.insertRecords(data.records);
        dao.restoreCalendarEvents(data.calendarEvents);
        dao.restoreSemesters(data.semesters);
        dao.restoreCourses(data.courses);
        dao.restoreCourseAdjustments(data.courseAdjustments);
        dao.restoreCalendarMarks(data.calendarMarks);
        dao.restoreRepeatCompletions(data.repeatCompletions);

        SharedPreferences.Editor editor = SettingsStore.prefs(context).edit();
        editor.clear();
        for (Map.Entry<String, String> entry : data.preferences.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (SettingsStore.KEY_FONT_SCALE.equals(key)) {
                try {
                    editor.putFloat(key, Float.parseFloat(value));
                } catch (Exception ignored) {
                }
            } else if (SettingsStore.KEY_REMINDER.equals(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value));
            } else if (SettingsStore.KEY_COURSE_SUMMARY.equals(key)
                    || SettingsStore.KEY_COURSE_REMINDER.equals(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value));
            } else if (SettingsStore.KEY_REMINDER_HOUR.equals(key)) {
                try {
                    editor.putInt(key, Integer.parseInt(value));
                } catch (Exception ignored) {
                }
            } else if (SettingsStore.KEY_COURSE_REMINDER_MINUTES.equals(key)) {
                try {
                    editor.putInt(key, Integer.parseInt(value));
                } catch (Exception ignored) {
                }
            } else {
                editor.putString(key, value);
            }
        }
        editor.apply();
    }

    public static void clearAll(StudyDao dao) {
        dao.clearAllRecords();
        dao.clearSections();
        dao.clearChapters();
        dao.clearSubjects();
        dao.clearTodos();
        dao.clearRepeatTasks();
        dao.clearCalendarEvents();
        dao.clearCourseAdjustments();
        dao.clearCourses();
        dao.clearSemesters();
        dao.clearCalendarMarks();
        dao.clearRepeatCompletions();
    }

    public static void clearRecords(StudyDao dao) {
        dao.clearRecords();
    }

    public static void clearCatalog(StudyDao dao) {
        dao.clearSections();
        dao.clearChapters();
        dao.clearSubjects();
    }
}
