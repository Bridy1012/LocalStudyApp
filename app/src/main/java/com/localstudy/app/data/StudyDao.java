package com.localstudy.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StudyDao {
    @Query("SELECT * FROM subjects ORDER BY createAt, id")
    List<Subject> getAllSubjectsSync();

    @Query("SELECT * FROM chapters ORDER BY subjectId, sortOrder, id")
    List<Chapter> getAllChaptersSync();

    @Query("SELECT * FROM sections ORDER BY planDate, sortOrder, id")
    List<Section> getAllSectionsSync();

    @Query("SELECT * FROM todos ORDER BY isFinished, ddl, createdAt, id")
    List<Todo> getAllTodosSync();

    @Query("SELECT * FROM repeat_tasks ORDER BY createdAt, id")
    List<RepeatTask> getAllRepeatTasksSync();

    @Query("SELECT * FROM study_records ORDER BY startTime DESC, id DESC")
    List<StudyRecord> getAllRecordsSync();

    @Query("SELECT * FROM calendar_events ORDER BY startAt, id")
    List<CalendarEvent> getAllCalendarEventsSync();

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    CalendarEvent getCalendarEventSync(long id);

    @Query("SELECT * FROM repeat_completions ORDER BY date, repeatTaskId")
    List<RepeatCompletion> getAllRepeatCompletionsSync();

    @Query("SELECT * FROM semesters ORDER BY isActive DESC, startDate DESC, id DESC")
    List<Semester> getAllSemestersSync();

    @Query("SELECT * FROM courses ORDER BY semesterId, dayOfWeek, startPeriod, id")
    List<Course> getAllCoursesSync();

    @Query("SELECT * FROM course_adjustments ORDER BY originalDate, id")
    List<CourseAdjustment> getAllCourseAdjustmentsSync();

    @Query("SELECT * FROM calendar_marks ORDER BY date, id")
    List<CalendarMark> getAllCalendarMarksSync();

    @Insert
    long insertSubject(Subject subject);

    @Insert
    long insertChapter(Chapter chapter);

    @Insert
    long insertSection(Section section);

    @Insert
    long insertTodo(Todo todo);

    @Insert
    long insertRepeatTask(RepeatTask task);

    @Insert
    long insertRecord(StudyRecord record);

    @Insert
    long insertCalendarEvent(CalendarEvent event);

    @Insert
    long insertSemester(Semester semester);

    @Insert
    long insertCourse(Course course);

    @Insert
    long insertCourseAdjustment(CourseAdjustment adjustment);

    @Insert
    long insertCalendarMark(CalendarMark mark);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRepeatCompletion(RepeatCompletion completion);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<StudyRecord> records);

    @Update
    void updateSubject(Subject subject);

    @Update
    void updateChapter(Chapter chapter);

    @Update
    void updateSection(Section section);

    @Update
    void updateTodo(Todo todo);

    @Update
    void updateRepeatTask(RepeatTask task);

    @Update
    void updateRecord(StudyRecord record);

    @Update
    void updateCalendarEvent(CalendarEvent event);

    @Update
    void updateSemester(Semester semester);

    @Update
    void updateCourse(Course course);

    @Delete
    void deleteSubject(Subject subject);

    @Delete
    void deleteChapter(Chapter chapter);

    @Delete
    void deleteSection(Section section);

    @Delete
    void deleteTodo(Todo todo);

    @Delete
    void deleteRepeatTask(RepeatTask task);

    @Delete
    void deleteRecord(StudyRecord record);

    @Delete
    void deleteCalendarEvent(CalendarEvent event);

    @Delete
    void deleteSemester(Semester semester);

    @Delete
    void deleteCourse(Course course);

    @Delete
    void deleteCourseAdjustment(CourseAdjustment adjustment);

    @Delete
    void deleteCalendarMark(CalendarMark mark);

    @Query("DELETE FROM repeat_completions WHERE repeatTaskId = :taskId AND date = :date")
    void deleteRepeatCompletion(long taskId, String date);

    @Query("DELETE FROM chapters WHERE subjectId = :subjectId")
    void deleteChaptersBySubject(long subjectId);

    @Query("DELETE FROM sections WHERE subjectId = :subjectId")
    void deleteSectionsBySubject(long subjectId);

    @Query("DELETE FROM sections WHERE chapterId = :chapterId")
    void deleteSectionsByChapter(long chapterId);

    @Query("UPDATE todos SET subjectId = NULL WHERE subjectId = :subjectId")
    void detachTodosFromSubject(long subjectId);

    @Query("UPDATE repeat_tasks SET subjectId = NULL WHERE subjectId = :subjectId")
    void detachRepeatsFromSubject(long subjectId);

    @Query("UPDATE sections SET isSkipped = 1 WHERE planDate < :today AND isFinished = 0 AND isSkipped = 0")
    int skipAllDebts(String today);

    @Query("DELETE FROM study_records")
    void clearRecords();

    @Query("DELETE FROM sections")
    void clearSections();

    @Query("DELETE FROM chapters")
    void clearChapters();

    @Query("DELETE FROM subjects")
    void clearSubjects();

    @Query("DELETE FROM todos")
    void clearTodos();

    @Query("DELETE FROM repeat_tasks")
    void clearRepeatTasks();

    @Query("DELETE FROM calendar_events")
    void clearCalendarEvents();

    @Query("DELETE FROM semesters")
    void clearSemesters();

    @Query("DELETE FROM courses")
    void clearCourses();

    @Query("DELETE FROM course_adjustments")
    void clearCourseAdjustments();

    @Query("DELETE FROM calendar_marks")
    void clearCalendarMarks();

    @Query("DELETE FROM repeat_completions")
    void clearRepeatCompletions();

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    void clearCoursesForSemester(long semesterId);

    @Query("UPDATE semesters SET isActive = 0")
    void clearActiveSemesters();

    @Query("DELETE FROM study_records")
    void clearAllRecords();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreSubjects(List<Subject> subjects);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreChapters(List<Chapter> chapters);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreSections(List<Section> sections);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreTodos(List<Todo> todos);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreRepeatTasks(List<RepeatTask> tasks);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreCalendarEvents(List<CalendarEvent> events);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreSemesters(List<Semester> semesters);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreCourses(List<Course> courses);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreCourseAdjustments(List<CourseAdjustment> adjustments);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreCalendarMarks(List<CalendarMark> marks);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void restoreRepeatCompletions(List<RepeatCompletion> completions);
}
