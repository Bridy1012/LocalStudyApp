package com.localstudy.app.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BackupData {
    public int schemaVersion = 5;
    public String exportedAt = "";
    public List<Subject> subjects = new ArrayList<>();
    public List<Chapter> chapters = new ArrayList<>();
    public List<Section> sections = new ArrayList<>();
    public List<Todo> todos = new ArrayList<>();
    public List<RepeatTask> repeatTasks = new ArrayList<>();
    public List<StudyRecord> records = new ArrayList<>();
    public List<CalendarEvent> calendarEvents = new ArrayList<>();
    public List<Semester> semesters = new ArrayList<>();
    public List<Course> courses = new ArrayList<>();
    public List<CourseAdjustment> courseAdjustments = new ArrayList<>();
    public List<CalendarMark> calendarMarks = new ArrayList<>();
    public List<RepeatCompletion> repeatCompletions = new ArrayList<>();
    public Map<String, String> preferences = new LinkedHashMap<>();
}
