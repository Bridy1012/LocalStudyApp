package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "courses")
public class Course {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long semesterId;

    @NonNull
    public String name = "";

    @NonNull
    public String classroom = "";

    @NonNull
    public String teacher = "";

    @NonNull
    public String note = "";

    public int color = 0xFF356AA8;

    @NonNull
    public String weeks = "";

    public int dayOfWeek = 1;
    public int startPeriod = 1;
    public int endPeriod = 1;

    @NonNull
    public String createdAt = "";

    public Course() {
    }
}
