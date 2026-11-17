package com.localstudy.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                Subject.class,
                Chapter.class,
                Section.class,
                Todo.class,
                RepeatTask.class,
                StudyRecord.class,
                CalendarEvent.class,
                Semester.class,
                Course.class,
                CourseAdjustment.class,
                CalendarMark.class,
                RepeatCompletion.class
        },
        version = 5,
        exportSchema = false
)
public abstract class StudyDatabase extends RoomDatabase {
    private static volatile StudyDatabase instance;

    public abstract StudyDao dao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_events` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`title` TEXT NOT NULL, "
                            + "`note` TEXT NOT NULL, "
                            + "`startAt` TEXT NOT NULL, "
                            + "`endAt` TEXT NOT NULL, "
                            + "`reminderMinutes` INTEGER NOT NULL, "
                            + "`createdAt` TEXT NOT NULL)"
            );
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `semesters` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`name` TEXT NOT NULL, "
                            + "`startDate` TEXT NOT NULL, "
                            + "`totalWeeks` INTEGER NOT NULL, "
                            + "`periodsPerDay` INTEGER NOT NULL, "
                            + "`periodTimes` TEXT NOT NULL, "
                            + "`isActive` INTEGER NOT NULL, "
                            + "`createdAt` TEXT NOT NULL)"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `courses` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`semesterId` INTEGER NOT NULL, "
                            + "`name` TEXT NOT NULL, "
                            + "`classroom` TEXT NOT NULL, "
                            + "`teacher` TEXT NOT NULL, "
                            + "`note` TEXT NOT NULL, "
                            + "`color` INTEGER NOT NULL, "
                            + "`weeks` TEXT NOT NULL, "
                            + "`dayOfWeek` INTEGER NOT NULL, "
                            + "`startPeriod` INTEGER NOT NULL, "
                            + "`endPeriod` INTEGER NOT NULL, "
                            + "`createdAt` TEXT NOT NULL)"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `course_adjustments` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`courseId` INTEGER NOT NULL, "
                            + "`originalDate` TEXT NOT NULL, "
                            + "`type` TEXT NOT NULL, "
                            + "`overrideDate` TEXT NOT NULL, "
                            + "`overrideStart` TEXT NOT NULL, "
                            + "`overrideEnd` TEXT NOT NULL, "
                            + "`note` TEXT NOT NULL, "
                            + "`createdAt` TEXT NOT NULL)"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_marks` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`date` TEXT NOT NULL, "
                            + "`type` TEXT NOT NULL, "
                            + "`weekday` INTEGER NOT NULL, "
                            + "`label` TEXT NOT NULL, "
                            + "`createdAt` TEXT NOT NULL)"
            );
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`classDurationMinutes` INTEGER NOT NULL DEFAULT 45");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`breakMinutes` INTEGER NOT NULL DEFAULT 10");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`morningPeriods` INTEGER NOT NULL DEFAULT 2");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`afternoonPeriods` INTEGER NOT NULL DEFAULT 2");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`eveningPeriods` INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`morningStart` TEXT NOT NULL DEFAULT '08:00'");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`afternoonStart` TEXT NOT NULL DEFAULT '14:00'");
            database.execSQL("ALTER TABLE `semesters` ADD COLUMN "
                    + "`eveningStart` TEXT NOT NULL DEFAULT '19:00'");
            database.execSQL("UPDATE `semesters` SET `periodsPerDay` = 5");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `sections` ADD COLUMN "
                    + "`sortOrder` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `course_adjustments` ADD COLUMN "
                    + "`overrideStartPeriod` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `course_adjustments` ADD COLUMN "
                    + "`overrideEndPeriod` INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `repeat_completions` ("
                            + "`repeatTaskId` INTEGER NOT NULL, "
                            + "`date` TEXT NOT NULL, "
                            + "`completedAt` TEXT NOT NULL, "
                            + "PRIMARY KEY(`repeatTaskId`, `date`))"
            );
            database.execSQL(
                    "INSERT OR REPLACE INTO `repeat_completions` "
                            + "(`repeatTaskId`,`date`,`completedAt`) "
                            + "SELECT `id`,`lastCompletedDate`,`createdAt` "
                            + "FROM `repeat_tasks` WHERE `lastCompletedDate` <> ''"
            );
        }
    };

    public static StudyDatabase get(Context context) {
        if (instance == null) {
            synchronized (StudyDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            StudyDatabase.class,
                            "local_study.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3,
                            MIGRATION_3_4, MIGRATION_4_5).build();
                }
            }
        }
        return instance;
    }
}
