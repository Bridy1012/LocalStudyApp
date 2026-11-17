package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "semesters")
public class Semester {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    @NonNull
    public String startDate = "";

    public int totalWeeks = 18;
    public int periodsPerDay = 5;
    public int classDurationMinutes = 45;
    public int breakMinutes = 10;
    public int morningPeriods = 2;
    public int afternoonPeriods = 2;
    public int eveningPeriods = 1;

    @NonNull
    public String morningStart = "08:00";

    @NonNull
    public String afternoonStart = "14:00";

    @NonNull
    public String eveningStart = "19:00";

    @NonNull
    public String periodTimes = "";

    public boolean isActive;

    @NonNull
    public String createdAt = "";

    public Semester() {
    }
}
