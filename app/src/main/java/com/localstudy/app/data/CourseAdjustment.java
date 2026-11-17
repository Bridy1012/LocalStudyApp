package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "course_adjustments")
public class CourseAdjustment {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long courseId;

    @NonNull
    public String originalDate = "";

    @NonNull
    public String type = "cancel";

    @NonNull
    public String overrideDate = "";

    @NonNull
    public String overrideStart = "";

    @NonNull
    public String overrideEnd = "";

    public int overrideStartPeriod;
    public int overrideEndPeriod;

    @NonNull
    public String note = "";

    @NonNull
    public String createdAt = "";

    public CourseAdjustment() {
    }
}
