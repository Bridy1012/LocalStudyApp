package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendar_events")
public class CalendarEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String note = "";

    @NonNull
    public String startAt = "";

    @NonNull
    public String endAt = "";

    public int reminderMinutes = 0;

    @NonNull
    public String createdAt = "";

    public CalendarEvent() {
    }
}
