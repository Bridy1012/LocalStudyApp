package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendar_marks")
public class CalendarMark {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String date = "";

    @NonNull
    public String type = "rest_day";

    public int weekday;

    @NonNull
    public String label = "";

    @NonNull
    public String createdAt = "";

    public CalendarMark() {
    }
}
