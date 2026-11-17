package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "todos")
public class Todo {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";
    public String ddl = "";
    public boolean isFinished;
    public Long subjectId;
    public String note = "";
    public String createdAt = "";

    public Todo() {
    }
}
