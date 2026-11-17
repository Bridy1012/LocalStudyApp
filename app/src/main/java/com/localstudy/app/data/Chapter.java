package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chapters")
public class Chapter {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long subjectId;

    @NonNull
    public String title = "";
    public int sortOrder;

    public Chapter() {
    }
}
