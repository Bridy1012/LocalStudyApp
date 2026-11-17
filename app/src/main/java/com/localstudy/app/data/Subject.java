package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "subjects")
public class Subject {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";
    public String remark = "";
    public String createAt = "";
    public String timerMode = "positive";
    public int timerDuration = 25;

    public Subject() {
    }
}
