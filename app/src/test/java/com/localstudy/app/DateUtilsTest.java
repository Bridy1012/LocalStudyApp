package com.localstudy.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.util.DateUtils;

import org.junit.Test;

import java.time.LocalDate;

public class DateUtilsTest {
    @Test
    public void weeklyRepeatUsesIsoWeekday() {
        RepeatTask task = new RepeatTask();
        task.repeatType = "weekly";
        task.repeatValue = "1,3,5";

        assertTrue(DateUtils.repeatOccursOn(task, LocalDate.of(2026, 10, 26)));
        assertFalse(DateUtils.repeatOccursOn(task, LocalDate.of(2026, 10, 27)));
    }

    @Test
    public void monthlyRepeatFallsBackToLastDay() {
        RepeatTask task = new RepeatTask();
        task.repeatType = "monthly";
        task.repeatValue = "31";

        assertTrue(DateUtils.repeatOccursOn(task, LocalDate.of(2026, 2, 28)));
    }

    @Test
    public void formatsDuration() {
        assertEquals("01:05", DateUtils.duration(65));
        assertEquals("1:02:03", DateUtils.duration(3723));
        assertEquals("1小时5分", DateUtils.durationHuman(3900));
    }
}
