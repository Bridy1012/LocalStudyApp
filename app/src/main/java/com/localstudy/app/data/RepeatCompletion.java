package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(
        tableName = "repeat_completions",
        primaryKeys = {"repeatTaskId", "date"}
)
public class RepeatCompletion {
    public long repeatTaskId;

    @NonNull
    public String date = "";

    @NonNull
    public String completedAt = "";

    public RepeatCompletion() {
    }
}
