package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sections")
public class Section {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public Long chapterId;
    public Long subjectId;

    @NonNull
    public String title = "";
    public String note = "";
    public boolean isFinished;
    public boolean isSkipped;
    public String planDate = "";
    public String finishDate = "";
    public String source = "manual";
    public int sortOrder;

    public Section() {
    }
}
