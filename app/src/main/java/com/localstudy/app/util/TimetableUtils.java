package com.localstudy.app.util;

import com.localstudy.app.data.CalendarMark;
import com.localstudy.app.data.Course;
import com.localstudy.app.data.CourseAdjustment;
import com.localstudy.app.data.Semester;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TimetableUtils {
    private TimetableUtils() {
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    public static int weekNumber(Semester semester, LocalDate date) {
        if (semester == null || !DateUtils.isValidDate(semester.startDate)) return 0;
        LocalDate start = weekStart(LocalDate.parse(semester.startDate, DateUtils.ISO_DATE));
        LocalDate target = weekStart(date);
        if (target.isBefore(start)) return 0;
        long week = ChronoUnit.WEEKS.between(start, target) + 1;
        return week >= 1 && week <= semester.totalWeeks ? (int) week : 0;
    }

    public static LocalDate dateForWeek(Semester semester, int week, int dayOfWeek) {
        LocalDate start = weekStart(LocalDate.parse(semester.startDate, DateUtils.ISO_DATE));
        return start.plusWeeks(Math.max(0, week - 1)).plusDays(Math.max(0, dayOfWeek - 1));
    }

    public static Set<Integer> parseWeeks(String value) {
        Set<Integer> result = new HashSet<>();
        if (value == null) return result;
        for (String token : value.split("[,，\\s]+")) {
            try {
                int week = Integer.parseInt(token.trim());
                if (week > 0) result.add(week);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static String formatWeeks(Set<Integer> weeks) {
        List<Integer> sorted = new ArrayList<>(weeks);
        sorted.sort(Integer::compareTo);
        StringBuilder value = new StringBuilder();
        for (int week : sorted) {
            if (value.length() > 0) value.append(',');
            value.append(week);
        }
        return value.toString();
    }

    public static List<PeriodTime> parsePeriodTimes(Semester semester) {
        List<PeriodTime> result = new ArrayList<>();
        if (semester == null) return result;
        if (semester.periodTimes == null || semester.periodTimes.trim().isEmpty()
                || semester.classDurationMinutes > 0) {
            String generated = generatePeriodTimes(semester);
            if (!generated.isEmpty()) semester.periodTimes = generated;
        }
        String[] values = semester.periodTimes == null ? new String[0] : semester.periodTimes.split(";");
        for (int i = 0; i < semester.periodsPerDay; i++) {
            LocalTime start = LocalTime.of(8, 0).plusMinutes(i * 55L);
            LocalTime end = start.plusMinutes(45);
            if (i < values.length) {
                String[] parts = values[i].trim().split("-");
                if (parts.length == 2) {
                    try {
                        start = LocalTime.parse(parts[0].trim());
                        end = LocalTime.parse(parts[1].trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            result.add(new PeriodTime(i + 1, start, end));
        }
        return result;
    }

    public static String generatePeriodTimes(Semester semester) {
        if (semester == null) return "";
        int duration = Math.max(1, semester.classDurationMinutes);
        int breakTime = Math.max(0, semester.breakMinutes);
        StringBuilder result = new StringBuilder();
        appendBlock(result, semester.morningStart, semester.morningPeriods, duration, breakTime);
        appendBlock(result, semester.afternoonStart, semester.afternoonPeriods, duration, breakTime);
        appendBlock(result, semester.eveningStart, semester.eveningPeriods, duration, breakTime);
        return result.toString();
    }

    private static void appendBlock(StringBuilder result, String startText,
                                    int count, int duration, int breakTime) {
        if (count <= 0) return;
        LocalTime start;
        try {
            start = LocalTime.parse(startText);
        } catch (Exception ignored) {
            start = LocalTime.of(8, 0);
        }
        for (int i = 0; i < count; i++) {
            if (result.length() > 0) result.append(';');
            LocalTime end = start.plusMinutes(duration);
            result.append(start).append('-').append(end);
            start = end.plusMinutes(breakTime);
        }
    }

    public static String defaultPeriodTimes(int periods) {
        String[] morning = {
                "08:00-08:45", "08:55-09:40", "10:00-10:45",
                "10:55-11:40", "14:00-14:45", "14:55-15:40",
                "16:00-16:45", "16:55-17:40", "19:00-19:45",
                "19:55-20:40", "20:50-21:35", "21:40-22:25"
        };
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(periods, morning.length); i++) {
            if (result.length() > 0) result.append(';');
            result.append(morning[i]);
        }
        return result.toString();
    }

    public static List<Occurrence> resolveDay(Semester semester, List<Course> allCourses,
                                              List<CourseAdjustment> adjustments,
                                              List<CalendarMark> marks,
                                              LocalDate date) {
        List<Occurrence> result = new ArrayList<>();
        if (semester == null || date == null) return result;
        int week = weekNumber(semester, date);
        if (week <= 0) return result;
        LocalDate firstOfWeek = weekStart(date);
        for (CalendarMark mark : marks) {
            if ("stop_week".equals(mark.type) && firstOfWeek.format(DateUtils.ISO_DATE)
                    .equals(mark.date)) {
                return result;
            }
            if ("rest_day".equals(mark.type) && date.format(DateUtils.ISO_DATE).equals(mark.date)) {
                return result;
            }
        }

        int weekday = date.getDayOfWeek().getValue();
        for (CalendarMark mark : marks) {
            if ("makeup_day".equals(mark.type)
                    && date.format(DateUtils.ISO_DATE).equals(mark.date)
                    && mark.weekday >= 1 && mark.weekday <= 7) {
                weekday = mark.weekday;
                break;
            }
        }

        List<PeriodTime> times = parsePeriodTimes(semester);
        for (Course course : allCourses) {
            if (course.semesterId != semester.id || course.dayOfWeek != weekday
                    || !parseWeeks(course.weeks).contains(week)) continue;
            CourseAdjustment adjustment = findAdjustment(adjustments, course.id, date);
            if (adjustment != null && "cancel".equals(adjustment.type)) continue;
            if (adjustment != null && "reschedule".equals(adjustment.type)) continue;
            result.add(defaultOccurrence(course, date, times, false, ""));
        }

        for (CourseAdjustment adjustment : adjustments) {
            if (!"reschedule".equals(adjustment.type)
                    || !date.format(DateUtils.ISO_DATE).equals(adjustment.overrideDate)) continue;
            Course course = findCourse(allCourses, adjustment.courseId);
            if (course == null || course.semesterId != semester.id) continue;
            int startPeriod = adjustment.overrideStartPeriod > 0
                    ? adjustment.overrideStartPeriod : course.startPeriod;
            int endPeriod = adjustment.overrideEndPeriod > 0
                    ? adjustment.overrideEndPeriod : course.endPeriod;
            startPeriod = Math.max(1, Math.min(startPeriod, times.size()));
            endPeriod = Math.max(startPeriod, Math.min(endPeriod, times.size()));
            LocalTime start = times.get(startPeriod - 1).start;
            LocalTime end = times.get(endPeriod - 1).end;
            if (!adjustment.overrideStart.isEmpty()) {
                start = parseTime(adjustment.overrideStart, start);
            }
            if (!adjustment.overrideEnd.isEmpty()) {
                end = parseTime(adjustment.overrideEnd, end);
            }
            result.add(new Occurrence(course, date, start, end, startPeriod, endPeriod,
                    true, adjustment.originalDate, adjustment.note));
        }
        result.sort(Comparator.comparing(item -> item.start));
        return result;
    }

    public static CourseAdjustment findAdjustment(List<CourseAdjustment> adjustments,
                                                   long courseId, LocalDate date) {
        String value = date.format(DateUtils.ISO_DATE);
        for (CourseAdjustment adjustment : adjustments) {
            if (adjustment.courseId == courseId && value.equals(adjustment.originalDate)) {
                return adjustment;
            }
        }
        return null;
    }

    public static CalendarMark findMark(List<CalendarMark> marks, LocalDate date, String type) {
        String value = date.format(DateUtils.ISO_DATE);
        for (CalendarMark mark : marks) {
            if (type.equals(mark.type) && value.equals(mark.date)) return mark;
        }
        return null;
    }

    private static Occurrence defaultOccurrence(Course course, LocalDate date,
                                                List<PeriodTime> times,
                                                boolean adjusted, String originalDate) {
        int startIndex = Math.max(0, course.startPeriod - 1);
        int endIndex = Math.max(startIndex, course.endPeriod - 1);
        LocalTime start = startIndex < times.size()
                ? times.get(startIndex).start : LocalTime.of(8, 0);
        LocalTime end = endIndex < times.size()
                ? times.get(endIndex).end : start.plusMinutes(45);
        return new Occurrence(course, date, start, end,
                course.startPeriod, course.endPeriod, adjusted, originalDate, "");
    }

    private static Course findCourse(List<Course> courses, long id) {
        for (Course course : courses) if (course.id == id) return course;
        return null;
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        try {
            return LocalTime.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static class PeriodTime {
        public final int period;
        public final LocalTime start;
        public final LocalTime end;

        PeriodTime(int period, LocalTime start, LocalTime end) {
            this.period = period;
            this.start = start;
            this.end = end;
        }
    }

    public static class Occurrence {
        public final Course course;
        public final LocalDate date;
        public final LocalTime start;
        public final LocalTime end;
        public final int startPeriod;
        public final int endPeriod;
        public final boolean adjusted;
        public final String originalDate;
        public final String adjustmentNote;

        Occurrence(Course course, LocalDate date, LocalTime start, LocalTime end,
                   int startPeriod, int endPeriod, boolean adjusted,
                   String originalDate, String adjustmentNote) {
            this.course = course;
            this.date = date;
            this.start = start;
            this.end = end;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.adjusted = adjusted;
            this.originalDate = originalDate;
            this.adjustmentNote = adjustmentNote;
        }

        public boolean containsPeriod(int period) {
            return period >= startPeriod && period <= endPeriod;
        }
    }
}
