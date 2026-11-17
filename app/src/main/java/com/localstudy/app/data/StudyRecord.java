package com.localstudy.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "study_records")
public class StudyRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public Long subjectId;

    @NonNull
    public String subjectName = "其他";
    public Long sectionId;
    public Long todoId;
    public Long repeatTaskId;
    public String startTime = "";
    public String endTime = "";
    public long durationSec;

    public StudyRecord() {
    }
}
