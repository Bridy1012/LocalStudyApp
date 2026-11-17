package com.localstudy.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.localstudy.app.data.Course;
import com.localstudy.app.util.TimetableTextParser;

import org.junit.Test;

import java.util.List;

public class TimetableTextParserTest {
    @Test
    public void parsesCommonPastTimetableLine() {
        List<Course> courses = TimetableTextParser.parse(
                "高等数学 周一 1-2节 1-16周 A101 张老师\n"
                        + "大学英语|周三|3-4|2-16周|B203|李老师"
        );

        assertEquals(2, courses.size());
        assertEquals("高等数学", courses.get(0).name);
        assertEquals(1, courses.get(0).dayOfWeek);
        assertEquals(1, courses.get(0).startPeriod);
        assertEquals(2, courses.get(0).endPeriod);
        assertTrue(courses.get(0).weeks.contains("16"));
        assertEquals(3, courses.get(1).dayOfWeek);
    }
}
