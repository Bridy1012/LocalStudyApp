package com.localstudy.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.localstudy.app.data.CalendarMark;
import com.localstudy.app.data.Course;
import com.localstudy.app.data.CourseAdjustment;
import com.localstudy.app.data.Semester;
import com.localstudy.app.util.TimetableUtils;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TimetableUtilsTest {
    private Semester semester() {
        Semester semester = new Semester();
        semester.id = 1;
        semester.startDate = "2026-09-07";
        semester.totalWeeks = 18;
        semester.periodsPerDay = 5;
        semester.periodTimes = TimetableUtils.defaultPeriodTimes(5);
        return semester;
    }

    private Course course() {
        Course course = new Course();
        course.id = 10;
        course.semesterId = 1;
        course.name = "高等数学";
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.weeks = "1,2,3";
        return course;
    }

    @Test
    public void calculatesWeekNumbersFromSemesterStart() {
        Semester semester = semester();
        assertEquals(1, TimetableUtils.weekNumber(semester,
                LocalDate.of(2026, 9, 7)));
        assertEquals(2, TimetableUtils.weekNumber(semester,
                LocalDate.of(2026, 9, 14)));
        assertEquals(0, TimetableUtils.weekNumber(semester,
                LocalDate.of(2026, 8, 31)));
    }

    @Test
    public void generatesPeriodTimesFromDurationAndBreaks() {
        Semester semester = semester();
        semester.classDurationMinutes = 45;
        semester.breakMinutes = 10;
        semester.morningStart = "08:00";
        semester.morningPeriods = 2;
        semester.afternoonStart = "14:00";
        semester.afternoonPeriods = 1;
        semester.eveningStart = "19:00";
        semester.eveningPeriods = 1;
        semester.periodsPerDay = 4;

        List<TimetableUtils.PeriodTime> times =
                TimetableUtils.parsePeriodTimes(semester);

        assertEquals(4, times.size());
        assertEquals("08:00", times.get(0).start.toString());
        assertEquals("08:55", times.get(1).start.toString());
        assertEquals("14:00", times.get(2).start.toString());
        assertEquals("19:00", times.get(3).start.toString());
    }

    @Test
    public void resolvesCourseAndStopWeek() {
        Semester semester = semester();
        List<Course> courses = new ArrayList<>();
        courses.add(course());
        List<CalendarMark> marks = new ArrayList<>();
        assertEquals(1, TimetableUtils.resolveDay(semester, courses,
                new ArrayList<>(), marks, LocalDate.of(2026, 9, 7)).size());

        CalendarMark stop = new CalendarMark();
        stop.type = "stop_week";
        stop.date = "2026-09-07";
        marks.add(stop);
        assertTrue(TimetableUtils.resolveDay(semester, courses,
                new ArrayList<>(), marks, LocalDate.of(2026, 9, 7)).isEmpty());
    }

    @Test
    public void makeupDayUsesRequestedWeekday() {
        Semester semester = semester();
        List<Course> courses = new ArrayList<>();
        courses.add(course());
        CalendarMark makeup = new CalendarMark();
        makeup.type = "makeup_day";
        makeup.date = "2026-09-13";
        makeup.weekday = 1;
        List<CalendarMark> marks = new ArrayList<>();
        marks.add(makeup);

        assertEquals(1, TimetableUtils.resolveDay(semester, courses,
                new ArrayList<>(), marks, LocalDate.of(2026, 9, 13)).size());
    }

    @Test
    public void cancelAndRescheduleAdjustmentsApply() {
        Semester semester = semester();
        List<Course> courses = new ArrayList<>();
        courses.add(course());
        List<CourseAdjustment> adjustments = new ArrayList<>();
        CourseAdjustment cancel = new CourseAdjustment();
        cancel.courseId = 10;
        cancel.originalDate = "2026-09-07";
        cancel.type = "cancel";
        adjustments.add(cancel);
        assertTrue(TimetableUtils.resolveDay(semester, courses, adjustments,
                new ArrayList<>(), LocalDate.of(2026, 9, 7)).isEmpty());

        adjustments.clear();
        CourseAdjustment moved = new CourseAdjustment();
        moved.courseId = 10;
        moved.originalDate = "2026-09-07";
        moved.type = "reschedule";
        moved.overrideDate = "2026-09-08";
        moved.overrideStart = "14:00";
        moved.overrideEnd = "15:35";
        moved.overrideStartPeriod = 3;
        moved.overrideEndPeriod = 4;
        adjustments.add(moved);
        List<TimetableUtils.Occurrence> movedDay = TimetableUtils.resolveDay(
                semester, courses, adjustments, new ArrayList<>(),
                LocalDate.of(2026, 9, 8)
        );
        assertEquals(1, movedDay.size());
        assertEquals(3, movedDay.get(0).startPeriod);
        assertEquals(4, movedDay.get(0).endPeriod);
    }
}
