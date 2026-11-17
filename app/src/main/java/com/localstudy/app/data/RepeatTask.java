package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "repeat_tasks")
public class RepeatTask {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";
    public Long subjectId;
    public String repeatType = "daily";
    public String repeatValue = "";
    public String note = "";
    public String createdAt = "";
    public String lastCompletedDate = "";

    public RepeatTask() {
    }
}
