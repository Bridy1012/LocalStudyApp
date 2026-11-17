package com.localstudy.app;

import android.app.Application;

import com.localstudy.app.service.CalendarReminderScheduler;
import com.localstudy.app.service.CourseReminderScheduler;
import com.localstudy.app.util.SettingsStore;

public class StudyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SettingsStore.applyNightMode(this);
        CalendarReminderScheduler.rescheduleAll(this);
        CourseReminderScheduler.rescheduleAll(this);
    }
}
