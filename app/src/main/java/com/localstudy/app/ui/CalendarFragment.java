package com.localstudy.app.ui;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.localstudy.app.R;
import com.localstudy.app.data.CalendarEvent;
import com.localstudy.app.data.CalendarMark;
import com.localstudy.app.data.Course;
import com.localstudy.app.data.CourseAdjustment;
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.RepeatCompletion;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.Semester;
import com.localstudy.app.data.StudyRepository;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.data.Subject;
import com.localstudy.app.data.Todo;
import com.localstudy.app.service.CalendarReminderScheduler;
import com.localstudy.app.service.CourseReminderScheduler;
import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.Dialogs;
import com.localstudy.app.util.TimetableDialogs;
import com.localstudy.app.util.TimetableUtils;
import com.localstudy.app.util.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalendarFragment extends Fragment implements Refreshable {
    private static final int COLOR_EVENT = 0xFF3E73D6;
    private static final int COLOR_TASK = 0xFF2F8F68;
    private static final int COLOR_TODO = 0xFFD07A30;
    private static final DateTimeFormatter EVENT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private StudyRepository repository;
    private LinearLayout content;
    private YearMonth month = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private String mode = "month";
    private int timetableWeek = 1;
    private boolean timetableInitialized;
    private Semester selectedSemester;
    private Loaded loaded;
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        repository = StudyRepository.get(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.bg(requireContext()));
        content = Ui.vertical(requireContext());
        content.setPadding(
                Ui.dp(requireContext(), 18), Ui.dp(requireContext(), 16),
                Ui.dp(requireContext(), 18), Ui.dp(requireContext(), 28)
        );
        scroll.addView(content);
        refresh();
        return scroll;
    }

    @Override
    public void refresh() {
        if (!isAdded() || loading || repository == null) return;
        loading = true;
        repository.io(() -> {
            Loaded data = new Loaded();
            data.events = repository.dao().getAllCalendarEventsSync();
            data.sections = repository.dao().getAllSectionsSync();
            data.repeats = repository.dao().getAllRepeatTasksSync();
            data.todos = repository.dao().getAllTodosSync();
            data.semesters = repository.dao().getAllSemestersSync();
            data.courses = repository.dao().getAllCoursesSync();
            data.adjustments = repository.dao().getAllCourseAdjustmentsSync();
            data.marks = repository.dao().getAllCalendarMarksSync();
            data.subjects = repository.dao().getAllSubjectsSync();
            data.repeatCompletions = repository.dao().getAllRepeatCompletionsSync();
            data.records = repository.dao().getAllRecordsSync();
            return data;
        }, (value, error) -> {
            loading = false;
            if (!isAdded()) return;
            if (error != null) {
                Toast.makeText(requireContext(), "读取日历失败", Toast.LENGTH_SHORT).show();
                return;
            }
            loaded = value;
            resolveSelectedSemester();
            render();
        });
    }

    private void resolveSelectedSemester() {
        if (loaded == null || loaded.semesters.isEmpty()) {
            selectedSemester = null;
            return;
        }
        if (selectedSemester != null) {
            for (Semester semester : loaded.semesters) {
                if (semester.id == selectedSemester.id) {
                    selectedSemester = semester;
                    break;
                }
            }
        }
        if (selectedSemester == null) {
            selectedSemester = CourseReminderScheduler.activeSemester(loaded.semesters);
        }
        if (!timetableInitialized && selectedSemester != null) {
            int current = TimetableUtils.weekNumber(selectedSemester, LocalDate.now());
            timetableWeek = current > 0 ? current : 1;
            timetableInitialized = true;
        }
    }

    private void render() {
        if (content == null || loaded == null) return;
        content.removeAllViews();
        LinearLayout header = Ui.horizontal(requireContext());
        LinearLayout headerText = Ui.vertical(requireContext());
        headerText.addView(Ui.title(requireContext(), "日历"));
        headerText.addView(Ui.caption(requireContext(),
                "日程、每日任务与课程表放在同一处"));
        Ui.setWeight(headerText, 1);
        ImageButton add = Ui.iconButton(requireContext(), R.drawable.ic_add,
                "month".equals(mode) ? "新增日程" : "新增课程");
        add.setOnClickListener(view -> {
            if ("month".equals(mode)) addEvent();
            else addCourse();
        });
        header.addView(headerText);
        header.addView(add);
        content.addView(header);
        content.addView(Ui.space(requireContext(), 12));
        content.addView(modeSelector());
        content.addView(Ui.space(requireContext(), 12));
        if ("month".equals(mode)) {
            content.addView(monthCard());
            content.addView(Ui.space(requireContext(), 12));
            content.addView(dayDetailCard());
        } else {
            renderTimetable();
        }
    }

    private View modeSelector() {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3),
                Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3));
        row.setBackground(Ui.rounded(requireContext(), Ui.surfaceAlt(requireContext()), 8));
        addModeTab(row, "月历", "month");
        addModeTab(row, "课程表", "timetable");
        return row;
    }

    private void addModeTab(LinearLayout row, String label, String value) {
        boolean active = mode.equals(value);
        TextView tab = Ui.text(requireContext(), label, 12,
                active ? android.graphics.Color.WHITE : Ui.muted(requireContext()), true);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(0, Ui.dp(requireContext(), 9), 0, Ui.dp(requireContext(), 9));
        tab.setBackground(Ui.rounded(requireContext(),
                active ? Ui.accent(requireContext()) : android.graphics.Color.TRANSPARENT, 7));
        tab.setOnClickListener(view -> {
            mode = value;
            render();
        });
        row.addView(tab, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
        ));
    }

    private View monthCard() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout monthHeader = Ui.horizontal(requireContext());
        TextView previous = monthAction("‹");
        previous.setContentDescription("上个月");
        previous.setOnClickListener(view -> {
            month = month.minusMonths(1);
            selectedDate = month.atDay(1);
            render();
        });
        TextView title = Ui.text(requireContext(),
                month.getYear() + "年 " + month.getMonthValue() + "月",
                17, Ui.textColor(requireContext()), true);
        title.setGravity(Gravity.CENTER);
        Ui.setWeight(title, 1);
        TextView next = monthAction("›");
        next.setContentDescription("下个月");
        next.setOnClickListener(view -> {
            month = month.plusMonths(1);
            selectedDate = month.atDay(1);
            render();
        });
        monthHeader.addView(previous);
        monthHeader.addView(title);
        monthHeader.addView(next);
        card.addView(monthHeader);

        LinearLayout legend = Ui.horizontal(requireContext());
        legend.setGravity(Gravity.CENTER);
        legend.addView(legendItem("日程", COLOR_EVENT));
        legend.addView(legendItem("每日任务", COLOR_TASK));
        legend.addView(legendItem("截止待办", COLOR_TODO));
        card.addView(legend);

        LinearLayout weekdays = Ui.horizontal(requireContext());
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (String name : names) {
            TextView day = Ui.caption(requireContext(), name);
            day.setGravity(Gravity.CENTER);
            day.setPadding(0, Ui.dp(requireContext(), 9), 0, Ui.dp(requireContext(), 5));
            weekdays.addView(day, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
            ));
        }
        card.addView(weekdays);
        LocalDate first = month.atDay(1);
        LocalDate cursor = first.minusDays(first.getDayOfWeek().getValue() - 1);
        for (int week = 0; week < 6; week++) {
            LinearLayout row = Ui.horizontal(requireContext());
            for (int day = 0; day < 7; day++) {
                row.addView(dayCell(cursor), new LinearLayout.LayoutParams(
                        0, Ui.dp(requireContext(), 50), 1
                ));
                cursor = cursor.plusDays(1);
            }
            card.addView(row);
        }
        return card;
    }

    private View legendItem(String label, int color) {
        LinearLayout holder = Ui.horizontal(requireContext());
        View dot = new View(requireContext());
        dot.setBackground(Ui.rounded(requireContext(), color, 3));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 6), Ui.dp(requireContext(), 6)
        );
        dotParams.rightMargin = Ui.dp(requireContext(), 4);
        holder.addView(dot, dotParams);
        holder.addView(Ui.caption(requireContext(), label));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = Ui.dp(requireContext(), 12);
        holder.setLayoutParams(params);
        return holder;
    }

    private View dayCell(LocalDate date) {
        LinearLayout cell = Ui.vertical(requireContext());
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(Ui.dp(requireContext(), 2), Ui.dp(requireContext(), 3),
                Ui.dp(requireContext(), 2), Ui.dp(requireContext(), 3));
        boolean inMonth = YearMonth.from(date).equals(month);
        boolean selected = date.equals(selectedDate);
        boolean today = date.equals(LocalDate.now());
        int textColor = selected ? Ui.accent(requireContext())
                : inMonth ? Ui.textColor(requireContext()) : Ui.muted(requireContext());
        TextView number = Ui.text(requireContext(), String.valueOf(date.getDayOfMonth()),
                13, textColor, selected || today);
        number.setGravity(Gravity.CENTER);
        cell.addView(number);
        LinearLayout markers = Ui.horizontal(requireContext());
        markers.setGravity(Gravity.CENTER);
        if (!eventsOn(date).isEmpty()) markers.addView(marker(COLOR_EVENT));
        if (!dailyTasksOn(date).isEmpty()) markers.addView(marker(COLOR_TASK));
        if (!todosOn(date).isEmpty()) markers.addView(marker(COLOR_TODO));
        cell.addView(markers);
        if (selected) {
            cell.setBackground(Ui.rounded(requireContext(),
                    Ui.withAlpha(Ui.accent(requireContext()), 24), 8));
        } else if (today) {
            cell.setBackground(Ui.outlined(requireContext(),
                    Ui.surfaceColor(requireContext()), Ui.accent(requireContext()), 8));
        }
        cell.setOnClickListener(view -> {
            selectedDate = date;
            month = YearMonth.from(date);
            render();
        });
        return cell;
    }

    private View marker(int color) {
        View view = new View(requireContext());
        view.setBackground(Ui.rounded(requireContext(), color, 3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 5), Ui.dp(requireContext(), 5)
        );
        params.setMargins(Ui.dp(requireContext(), 1), 0, Ui.dp(requireContext(), 1), 0);
        view.setLayoutParams(params);
        return view;
    }

    private View dayDetailCard() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout header = Ui.horizontal(requireContext());
        LinearLayout titleHolder = Ui.vertical(requireContext());
        titleHolder.addView(Ui.sectionTitle(requireContext(),
                selectedDate.getMonthValue() + "月" + selectedDate.getDayOfMonth()
                        + "日 · " + weekdayCn(selectedDate)));
        List<StudyRecord> records = recordsOn(selectedDate);
        int total = eventsOn(selectedDate).size() + dailyTasksOn(selectedDate).size()
                + todosOn(selectedDate).size() + records.size();
        titleHolder.addView(Ui.caption(requireContext(),
                total == 0 ? "当天暂无安排" : "共 " + total + " 项安排"));
        Ui.setWeight(titleHolder, 1);
        Button record = Ui.button(requireContext(), "补记", false);
        record.setOnClickListener(view -> editRecord(null));
        Button add = Ui.button(requireContext(), "新增日程", true);
        add.setOnClickListener(view -> addEvent());
        header.addView(titleHolder);
        header.addView(record);
        header.addView(add);
        card.addView(header);
        card.addView(Ui.space(requireContext(), 10));

        List<CalendarEvent> events = eventsOn(selectedDate);
        List<DayTask> tasks = dailyTasksOn(selectedDate);
        List<Todo> todos = todosOn(selectedDate);
        if (!events.isEmpty()) {
            addGroupTitle(card, "日程提醒", COLOR_EVENT, events.size());
            for (CalendarEvent event : events) card.addView(eventRow(event));
        }
        if (!tasks.isEmpty()) {
            addGroupTitle(card, "每日任务", COLOR_TASK, tasks.size());
            for (DayTask task : tasks) card.addView(taskRow(task));
        }
        if (!todos.isEmpty()) {
            addGroupTitle(card, "当天截止待办", COLOR_TODO, todos.size());
            for (Todo todo : todos) card.addView(todoRow(todo));
        }
        if (!records.isEmpty()) {
            addGroupTitle(card, "学习记录", Ui.accent(requireContext()), records.size());
            for (StudyRecord studyRecord : records) card.addView(recordRow(studyRecord));
        }
        if (events.isEmpty() && tasks.isEmpty() && todos.isEmpty() && records.isEmpty()) {
            TextView empty = Ui.caption(requireContext(),
                    "这一天还没有安排，可以添加会议、提醒或完成当天任务。");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(requireContext(), 16), 0, Ui.dp(requireContext(), 10));
            card.addView(empty);
        }
        return card;
    }

    private void addGroupTitle(LinearLayout card, String title, int color, int count) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 13), 0, Ui.dp(requireContext(), 5));
        TextView text = Ui.text(requireContext(), title, 13, color, true);
        Ui.setWeight(text, 1);
        row.addView(text);
        row.addView(Ui.pill(requireContext(), String.valueOf(count), color));
        card.addView(row);
    }

    private View eventRow(CalendarEvent event) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 5), 0, Ui.dp(requireContext(), 5));
        TextView time = Ui.pill(requireContext(), timeText(event), COLOR_EVENT);
        row.addView(time);
        LinearLayout text = Ui.vertical(requireContext());
        TextView title = Ui.text(requireContext(), event.title, 14,
                Ui.textColor(requireContext()), true);
        text.addView(title);
        String meta = reminderText(event.reminderMinutes);
        if (event.note != null && !event.note.isEmpty()) meta += " · " + event.note;
        text.addView(Ui.caption(requireContext(), meta));
        Ui.setWeight(text, 1);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 9), 1
        );
        row.addView(Ui.space(requireContext(), 1), spacerParams);
        row.addView(text);
        TextView more = actionText("更多", Ui.accent(requireContext()));
        more.setOnClickListener(view -> showEventMenu(more, event));
        row.addView(more);
        row.setOnLongClickListener(view -> {
            editEvent(event);
            return true;
        });
        return row;
    }

    private View taskRow(DayTask task) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 4));
        AnimatedCheckView check = new AnimatedCheckView(requireContext());
        check.setChecked(task.done);
        LinearLayout text = Ui.vertical(requireContext());
        TextView title = Ui.text(requireContext(), task.title, 13,
                task.done ? Ui.muted(requireContext()) : Ui.textColor(requireContext()), true);
        if (task.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        check.setListener(done -> setTaskDone(task, done, title));
        text.addView(title);
        String meta = "每日任务";
        if (task.repeat != null) {
            meta += " · " + repeatText(task.repeat);
        } else if (task.section != null) {
            meta += " · 单次任务";
        }
        text.addView(Ui.caption(requireContext(), meta));
        Ui.setWeight(text, 1);
        row.addView(check);
        row.addView(text);
        View swatch = new View(requireContext());
        swatch.setBackground(Ui.rounded(requireContext(), COLOR_TASK, 3));
        row.addView(swatch, new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 6), Ui.dp(requireContext(), 32)
        ));
        row.setOnClickListener(view -> editDayTask(task));
        row.setOnLongClickListener(view -> {
            editDayTask(task);
            return true;
        });
        return row;
    }

    private void editDayTask(DayTask task) {
        if (task.section != null) {
            long chapterId = task.section.chapterId == null ? -1 : task.section.chapterId;
            Dialogs.sectionForm(requireContext(), task.section, loaded.subjects,
                    task.section.subjectId, value -> {
                        value.chapterId = chapterId < 0 ? null : chapterId;
                        repository.io(() -> repository.dao().updateSection(value), this::refresh);
                    });
        } else if (task.repeat != null) {
            Dialogs.repeatForm(requireContext(), task.repeat, loaded.subjects, value ->
                    repository.io(() -> repository.dao().updateRepeatTask(value), this::refresh));
        }
    }

    private View todoRow(Todo todo) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 4));
        AnimatedCheckView check = new AnimatedCheckView(requireContext());
        check.setChecked(todo.isFinished);
        LinearLayout text = Ui.vertical(requireContext());
        boolean overdue = !todo.isFinished && DateUtils.isValidDate(todo.ddl)
                && todo.ddl.compareTo(DateUtils.today()) < 0;
        int color = todo.isFinished ? Ui.muted(requireContext())
                : overdue ? Ui.danger(requireContext()) : COLOR_TODO;
        TextView title = Ui.text(requireContext(), todo.title, 13, color, true);
        if (todo.isFinished) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        check.setListener(done -> {
            todo.isFinished = done;
            applyCalendarDoneStyle(title, done, color);
            repository.io(() -> repository.dao().updateTodo(todo), this::refreshAfterAnimation);
        });
        text.addView(title);
        text.addView(Ui.caption(requireContext(), overdue
                ? "截止日已过 · 尚未完成" : "当天截止"));
        Ui.setWeight(text, 1);
        row.addView(check);
        row.addView(text);
        View swatch = new View(requireContext());
        swatch.setBackground(Ui.rounded(requireContext(), color, 3));
        row.addView(swatch, new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 6), Ui.dp(requireContext(), 32)
        ));
        return row;
    }

    private View recordRow(StudyRecord record) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 5), 0, Ui.dp(requireContext(), 5));
        row.addView(Ui.pill(requireContext(), recordTimeRange(record),
                Ui.accent(requireContext())));
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(),
                record.subjectName == null ? "其他" : record.subjectName,
                13, Ui.textColor(requireContext()), true));
        text.addView(Ui.caption(requireContext(),
                DateUtils.durationHuman(record.durationSec)));
        Ui.setWeight(text, 1);
        row.addView(text);
        TextView edit = actionText("编辑", Ui.accent(requireContext()));
        edit.setOnClickListener(view -> editRecord(record));
        TextView delete = actionText("删除", Ui.danger(requireContext()));
        delete.setOnClickListener(view -> new AlertDialog.Builder(requireContext())
                .setTitle("删除学习记录？")
                .setMessage("对应学习时长会从统计中移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                        repository.io(() -> repository.dao().deleteRecord(record), this::refresh))
                .show());
        row.addView(edit);
        row.addView(delete);
        return row;
    }

    private void editRecord(StudyRecord existing) {
        Dialogs.recordForm(requireContext(), existing, loaded.subjects, record ->
                repository.io(() -> {
                    if (existing == null) repository.dao().insertRecord(record);
                    else repository.dao().updateRecord(record);
                }, this::refresh));
    }

    private String recordTimeRange(StudyRecord record) {
        String start = record.startTime == null ? "" : record.startTime;
        String end = record.endTime == null ? "" : record.endTime;
        if (start.length() >= 16) start = start.substring(11, 16);
        if (end.length() >= 16) end = end.substring(11, 16);
        return start + "–" + end;
    }

    private void renderTimetable() {
        if (selectedSemester == null) {
            LinearLayout empty = Ui.surface(requireContext());
            empty.addView(Ui.sectionTitle(requireContext(), "建立你的第一张课程表"));
            empty.addView(Ui.caption(requireContext(),
                    "设置学期、每天节数和节次时间后，就可以添加课程或从教务系统粘贴导入。"));
            Button create = Ui.button(requireContext(), "新建学期课表", true);
            create.setOnClickListener(view -> createSemester());
            empty.addView(create);
            content.addView(empty);
            return;
        }
        content.addView(semesterCard());
        content.addView(Ui.space(requireContext(), 10));
        content.addView(weekControls());
        content.addView(Ui.space(requireContext(), 8));
        content.addView(weekGrid());
        content.addView(Ui.space(requireContext(), 10));
        content.addView(weekCourseList());
    }

    private View semesterCard() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(), selectedSemester.name, 15,
                Ui.textColor(requireContext()), true));
        text.addView(Ui.caption(requireContext(),
                "共 " + selectedSemester.totalWeeks + " 周 · 每天 "
                        + selectedSemester.periodsPerDay + " 节"));
        Ui.setWeight(text, 1);
        TextView switchSemester = actionText("切换学期", Ui.accent(requireContext()));
        switchSemester.setOnClickListener(view -> showSemesterPicker());
        TextView settings = actionText("设置", Ui.accent(requireContext()));
        settings.setOnClickListener(view -> editSemester(selectedSemester));
        row.addView(text);
        row.addView(switchSemester);
        row.addView(settings);
        card.addView(row);
        LinearLayout actions = Ui.horizontal(requireContext());
        actions.setPadding(0, Ui.dp(requireContext(), 10), 0, 0);
        Button add = Ui.button(requireContext(), "新增课程", true);
        add.setOnClickListener(view -> addCourse());
        Button importButton = Ui.button(requireContext(), "粘贴导入", false);
        importButton.setOnClickListener(view -> importCourses());
        Button mark = Ui.button(requireContext(), "调休/停课", false);
        mark.setOnClickListener(view -> editDayMark());
        actions.addView(add);
        actions.addView(importButton);
        actions.addView(mark);
        card.addView(actions);
        return card;
    }

    private View weekControls() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        TextView previous = monthAction("‹");
        previous.setOnClickListener(view -> changeWeek(-1));
        LocalDate start = TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1);
        LocalDate end = start.plusDays(6);
        LinearLayout titleHolder = Ui.vertical(requireContext());
        TextView title = Ui.text(requireContext(), "第 " + timetableWeek + " 周",
                16, Ui.textColor(requireContext()), true);
        title.setGravity(Gravity.CENTER);
        TextView dates = Ui.caption(requireContext(),
                start.getMonthValue() + "/" + start.getDayOfMonth() + " - "
                        + end.getMonthValue() + "/" + end.getDayOfMonth());
        dates.setGravity(Gravity.CENTER);
        titleHolder.addView(title);
        titleHolder.addView(dates);
        Ui.setWeight(titleHolder, 1);
        TextView next = monthAction("›");
        next.setOnClickListener(view -> changeWeek(1));
        row.addView(previous);
        row.addView(titleHolder);
        row.addView(next);
        card.addView(row);
        LinearLayout actions = Ui.horizontal(requireContext());
        TextView current = actionText("回到本周", Ui.accent(requireContext()));
        current.setOnClickListener(view -> {
            int week = TimetableUtils.weekNumber(selectedSemester, LocalDate.now());
            timetableWeek = week > 0 ? week : 1;
            render();
        });
        TextView stopWeek = actionText(isStopWeek() ? "取消停课周" : "标记停课周",
                isStopWeek() ? Ui.success(requireContext()) : Ui.danger(requireContext()));
        stopWeek.setOnClickListener(view -> toggleStopWeek());
        Ui.setWeight(current, 1);
        actions.addView(current);
        actions.addView(stopWeek);
        card.addView(actions);
        CalendarMark stop = findStopWeek();
        if (stop != null) {
            TextView banner = Ui.text(requireContext(),
                    "本周停课" + (stop.label.isEmpty() ? "" : " · " + stop.label),
                    12, Ui.danger(requireContext()), true);
            banner.setPadding(0, Ui.dp(requireContext(), 8), 0, 0);
            card.addView(banner);
        }
        return card;
    }

    private View weekGrid() {
        LinearLayout card = Ui.surface(requireContext());
        LocalDate start = TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1);
        Map<LocalDate, List<TimetableUtils.Occurrence>> occurrences = new HashMap<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            occurrences.put(date, TimetableUtils.resolveDay(
                    selectedSemester, loaded.courses, loaded.adjustments,
                    loaded.marks, date
            ));
        }
        LinearLayout grid = Ui.vertical(requireContext());
        LinearLayout header = Ui.horizontal(requireContext());
        TextView timeHead = Ui.caption(requireContext(), "节次 / 时间");
        timeHead.setGravity(Gravity.CENTER);
        header.addView(timeHead, new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 56), ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            boolean today = date.equals(LocalDate.now());
            LinearLayout dayHeader = Ui.vertical(requireContext());
            dayHeader.setGravity(Gravity.CENTER);
            dayHeader.setPadding(0, Ui.dp(requireContext(), 7), 0, Ui.dp(requireContext(), 5));
            TextView weekDay = Ui.text(requireContext(), weekdayShort(date), 11,
                    today ? Ui.accent(requireContext()) : Ui.muted(requireContext()), today);
            TextView dateText = Ui.text(requireContext(), String.valueOf(date.getDayOfMonth()),
                    12, today ? Ui.accent(requireContext()) : Ui.textColor(requireContext()), today);
            dayHeader.addView(weekDay);
            dayHeader.addView(dateText);
            if (today) {
                dayHeader.setBackground(Ui.rounded(requireContext(),
                        Ui.withAlpha(Ui.accent(requireContext()), 18), 6));
            }
            header.addView(dayHeader, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
            ));
        }
        grid.addView(header);

        List<TimetableUtils.PeriodTime> periods =
                TimetableUtils.parsePeriodTimes(selectedSemester);
        for (TimetableUtils.PeriodTime period : periods) {
            LinearLayout row = Ui.horizontal(requireContext());
            row.setPadding(0, Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 2));
            row.setBackgroundColor(Ui.withAlpha(Ui.surfaceAlt(requireContext()), 46));
            LinearLayout time = Ui.vertical(requireContext());
            time.setGravity(Gravity.CENTER);
            TextView number = Ui.text(requireContext(), "第 " + period.period + " 节", 10,
                    Ui.textColor(requireContext()), true);
            TextView startText = Ui.text(requireContext(),
                    period.start.format(TIME_FORMAT) + "\n" + period.end.format(TIME_FORMAT),
                    9, Ui.muted(requireContext()), false);
            number.setGravity(Gravity.CENTER);
            startText.setGravity(Gravity.CENTER);
            time.addView(number);
            time.addView(startText);
            row.addView(time, new LinearLayout.LayoutParams(
                    Ui.dp(requireContext(), 56), Ui.dp(requireContext(), 78)
            ));
            for (int day = 0; day < 7; day++) {
                LocalDate date = start.plusDays(day);
                TimetableUtils.Occurrence occurrence = findOccurrence(
                        occurrences.get(date), period.period
                );
                Course canceled = occurrence == null
                        ? findCanceledCourse(date, period.period) : null;
                View cell = courseCell(date, period, occurrence, canceled, occurrences.get(date));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, Ui.dp(requireContext(), 78), 1
                );
                params.setMargins(Ui.dp(requireContext(), 1), 0,
                        Ui.dp(requireContext(), 1), 0);
                row.addView(cell, params);
            }
            grid.addView(row);
        }
        SwipeLayout swipe = new SwipeLayout(requireContext());
        swipe.addView(grid);
        swipe.setOnSwipe(direction -> changeWeek(direction));
        card.addView(swipe);
        return card;
    }

    private View courseCell(LocalDate date, TimetableUtils.PeriodTime period,
                            TimetableUtils.Occurrence occurrence,
                            Course canceled,
                            List<TimetableUtils.Occurrence> dayOccurrences) {
        LinearLayout cell = Ui.vertical(requireContext());
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3),
                Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3));
        if (occurrence != null) {
            boolean firstPeriod = period.period == occurrence.startPeriod;
            int color = occurrence.course.color;
            cell.setBackground(Ui.outlined(requireContext(),
                    Ui.withAlpha(color, 52), Ui.withAlpha(color, 145), 7));
            if (firstPeriod) {
                TextView name = Ui.text(requireContext(), occurrence.course.name, 10,
                        Ui.textColor(requireContext()), true);
                name.setMaxLines(2);
                name.setGravity(Gravity.CENTER);
                cell.addView(name);
                if (!occurrence.course.classroom.isEmpty()) {
                    TextView room = Ui.text(requireContext(), occurrence.course.classroom,
                            9, Ui.muted(requireContext()), false);
                    room.setMaxLines(1);
                    room.setGravity(Gravity.CENTER);
                    cell.addView(room);
                }
                String status = courseStatus(occurrence, dayOccurrences);
                if (!status.isEmpty()) {
                    TextView badge = Ui.text(requireContext(), status, 8,
                            color, true);
                    badge.setGravity(Gravity.CENTER);
                    cell.addView(badge);
                }
                if (occurrence.adjusted) {
                    TextView adjusted = Ui.text(requireContext(), "调课", 8,
                            COLOR_TODO, true);
                    adjusted.setGravity(Gravity.CENTER);
                    cell.addView(adjusted);
                }
            } else {
                TextView continuation = Ui.text(requireContext(), "↳ 连堂", 9,
                        Ui.muted(requireContext()), false);
                continuation.setGravity(Gravity.CENTER);
                cell.addView(continuation);
            }
            cell.setOnClickListener(view ->
                    showCourseOccurrenceMenu(view, occurrence, date));
        } else if (canceled != null) {
            cell.setBackground(Ui.rounded(requireContext(),
                    Ui.withAlpha(Ui.muted(requireContext()), 16), 6));
            TextView text = Ui.text(requireContext(), "停课", 8,
                    Ui.muted(requireContext()), true);
            text.setGravity(Gravity.CENTER);
            text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            cell.addView(text);
            cell.setOnClickListener(view ->
                    showCanceledMenu(view, canceled, date));
        }
        return cell;
    }

    private View weekCourseList() {
        LinearLayout card = Ui.surface(requireContext());
        card.addView(Ui.sectionTitle(requireContext(), "本周课程"));
        LocalDate start = TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1);
        int count = 0;
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            List<TimetableUtils.Occurrence> occurrences = TimetableUtils.resolveDay(
                    selectedSemester, loaded.courses, loaded.adjustments, loaded.marks, date
            );
            for (TimetableUtils.Occurrence occurrence : occurrences) {
                count++;
                LinearLayout row = Ui.horizontal(requireContext());
                row.setPadding(0, Ui.dp(requireContext(), 7), 0, Ui.dp(requireContext(), 7));
                TextView dayPill = Ui.pill(requireContext(), weekdayShort(date),
                        occurrence.course.color);
                row.addView(dayPill);
                LinearLayout text = Ui.vertical(requireContext());
                text.addView(Ui.text(requireContext(), occurrence.course.name, 13,
                        Ui.textColor(requireContext()), true));
                String meta = occurrence.start.format(TIME_FORMAT) + "-"
                        + occurrence.end.format(TIME_FORMAT);
                if (!occurrence.course.classroom.isEmpty()) meta += " · " + occurrence.course.classroom;
                if (!occurrence.course.teacher.isEmpty()) meta += " · " + occurrence.course.teacher;
                text.addView(Ui.caption(requireContext(), meta));
                Ui.setWeight(text, 1);
                LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(
                        Ui.dp(requireContext(), 8), 1
                );
                row.addView(Ui.space(requireContext(), 1), spacer);
                row.addView(text);
                row.setOnClickListener(view ->
                        showCourseOccurrenceMenu(view, occurrence, date));
                card.addView(row);
            }
        }
        if (count == 0) {
            TextView empty = Ui.caption(requireContext(), "本周没有课程");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(requireContext(), 16), 0, Ui.dp(requireContext(), 8));
            card.addView(empty);
        }
        return card;
    }

    private TextView actionText(String label, int color) {
        TextView view = Ui.text(requireContext(), label, 11, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 7),
                Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 7));
        view.setBackground(Ui.rounded(requireContext(), Ui.withAlpha(color, 17), 7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = Ui.dp(requireContext(), 5);
        view.setLayoutParams(params);
        return view;
    }

    private void showSemesterPicker() {
        String[] names = new String[loaded.semesters.size() + 1];
        for (int i = 0; i < loaded.semesters.size(); i++) {
            Semester semester = loaded.semesters.get(i);
            names[i] = semester.name + (semester.isActive ? " · 当前" : "");
        }
        names[names.length - 1] = "＋ 新建下学期课表";
        new AlertDialog.Builder(requireContext())
                .setTitle("选择学期")
                .setItems(names, (dialog, which) -> {
                    if (which == loaded.semesters.size()) {
                        createNextSemester();
                    } else {
                        selectedSemester = loaded.semesters.get(which);
                        int current = TimetableUtils.weekNumber(selectedSemester, LocalDate.now());
                        timetableWeek = current > 0 ? current : 1;
                        timetableInitialized = true;
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createSemester() {
        TimetableDialogs.semesterForm(requireContext(), null, semester -> {
            semester.isActive = true;
            repository.io(() -> {
                repository.dao().clearActiveSemesters();
                semester.id = repository.dao().insertSemester(semester);
            }, () -> {
                selectedSemester = semester;
                timetableWeek = 1;
                timetableInitialized = true;
                CourseReminderScheduler.rescheduleAll(requireContext());
                refresh();
            });
        });
    }

    private void createNextSemester() {
        Semester preset = new Semester();
        LocalDate start = TimetableUtils.weekStart(LocalDate.now());
        if (selectedSemester != null) {
            try {
                start = TimetableUtils.weekStart(LocalDate.parse(selectedSemester.startDate))
                        .plusWeeks(selectedSemester.totalWeeks);
            } catch (Exception ignored) {
            }
        }
        preset.startDate = start.format(DateUtils.ISO_DATE);
        preset.totalWeeks = 18;
        preset.periodsPerDay = selectedSemester == null ? 5 : selectedSemester.periodsPerDay;
        preset.periodTimes = TimetableUtils.defaultPeriodTimes(preset.periodsPerDay);
        preset.name = start.getYear() + " 学期";
        TimetableDialogs.semesterForm(requireContext(), preset, semester -> {
            semester.id = 0;
            semester.isActive = true;
            repository.io(() -> {
                repository.dao().clearActiveSemesters();
                semester.id = repository.dao().insertSemester(semester);
            }, () -> {
                selectedSemester = semester;
                timetableWeek = 1;
                CourseReminderScheduler.rescheduleAll(requireContext());
                refresh();
            });
        });
    }

    private void editSemester(Semester semester) {
        TimetableDialogs.semesterForm(requireContext(), semester, value ->
                repository.io(() -> repository.dao().updateSemester(value), () -> {
                    CourseReminderScheduler.rescheduleAll(requireContext());
                    refresh();
                }));
    }

    private void addCourse() {
        if (selectedSemester == null) {
            createSemester();
            return;
        }
        TimetableDialogs.courseForm(requireContext(), selectedSemester, null, course ->
                repository.io(() -> {
                    repository.dao().insertCourse(course);
                    CourseReminderScheduler.rescheduleAll(requireContext());
                }, this::refresh));
    }

    private void importCourses() {
        if (selectedSemester == null) return;
        TimetableDialogs.importForm(requireContext(), selectedSemester, courses ->
                repository.io(() -> {
                    for (Course course : courses) repository.dao().insertCourse(course);
                    CourseReminderScheduler.rescheduleAll(requireContext());
                }, this::refresh));
    }

    private void showCourseOccurrenceMenu(View anchor,
                                          TimetableUtils.Occurrence occurrence,
                                          LocalDate date) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑课程");
        menu.getMenu().add("本节停课");
        menu.getMenu().add("单节调课");
        if (TimetableUtils.findAdjustment(loaded.adjustments, occurrence.course.id, date) != null) {
            menu.getMenu().add("清除本节调整");
        }
        menu.getMenu().add("删除课程");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (title.equals("编辑课程")) editCourse(occurrence.course);
            else if (title.equals("本节停课")) cancelOccurrence(occurrence.course, date);
            else if (title.equals("单节调课")) rescheduleOccurrence(occurrence.course, date);
            else if (title.equals("清除本节调整")) clearAdjustment(occurrence.course, date);
            else deleteCourse(occurrence.course);
            return true;
        });
        menu.show();
    }

    private void showCanceledMenu(View anchor, Course course, LocalDate date) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("取消本节停课");
        menu.getMenu().add("单节调课");
        menu.setOnMenuItemClickListener(item -> {
            if (String.valueOf(item.getTitle()).startsWith("取消")) {
                clearAdjustment(course, date);
            } else {
                rescheduleOccurrence(course, date);
            }
            return true;
        });
        menu.show();
    }

    private void editCourse(Course course) {
        TimetableDialogs.courseForm(requireContext(), selectedSemester, course, value ->
                repository.io(() -> {
                    repository.dao().updateCourse(value);
                    CourseReminderScheduler.rescheduleAll(requireContext());
                }, this::refresh));
    }

    private void deleteCourse(Course course) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除这门课程？")
                .setMessage("整学期安排会删除，单节调整也会一并清理。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                        repository.io(() -> {
                            for (CourseAdjustment adjustment : loaded.adjustments) {
                                if (adjustment.courseId == course.id) {
                                    repository.dao().deleteCourseAdjustment(adjustment);
                                }
                            }
                            repository.dao().deleteCourse(course);
                            CourseReminderScheduler.rescheduleAll(requireContext());
                        }, this::refresh))
                .show();
    }

    private void cancelOccurrence(Course course, LocalDate date) {
        CourseAdjustment adjustment = new CourseAdjustment();
        adjustment.courseId = course.id;
        adjustment.originalDate = date.format(DateUtils.ISO_DATE);
        adjustment.type = "cancel";
        adjustment.createdAt = DateUtils.nowText();
        repository.io(() -> {
            CourseAdjustment old = TimetableUtils.findAdjustment(
                    loaded.adjustments, course.id, date);
            if (old != null) repository.dao().deleteCourseAdjustment(old);
            repository.dao().insertCourseAdjustment(adjustment);
            CourseReminderScheduler.rescheduleAll(requireContext());
        }, this::refresh);
    }

    private void rescheduleOccurrence(Course course, LocalDate date) {
        TimetableDialogs.adjustmentForm(requireContext(), selectedSemester, course, date,
                adjustment -> repository.io(() -> {
                    CourseAdjustment old = TimetableUtils.findAdjustment(
                            loaded.adjustments, course.id, date);
                    if (old != null) repository.dao().deleteCourseAdjustment(old);
                    repository.dao().insertCourseAdjustment(adjustment);
                    CourseReminderScheduler.rescheduleAll(requireContext());
                }, this::refresh));
    }

    private void clearAdjustment(Course course, LocalDate date) {
        repository.io(() -> {
            CourseAdjustment old = TimetableUtils.findAdjustment(
                    loaded.adjustments, course.id, date);
            if (old != null) repository.dao().deleteCourseAdjustment(old);
            CourseReminderScheduler.rescheduleAll(requireContext());
        }, this::refresh);
    }

    private void editDayMark() {
        LocalDate defaultDate = LocalDate.now();
        LocalDate start = TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1);
        if (defaultDate.isBefore(start) || defaultDate.isAfter(start.plusDays(6))) {
            defaultDate = start;
        }
        TimetableDialogs.markForm(requireContext(), defaultDate, mark ->
                repository.io(() -> {
                    for (CalendarMark existing : new ArrayList<>(loaded.marks)) {
                        if (existing.date.equals(mark.date) && existing.type.equals(mark.type)) {
                            repository.dao().deleteCalendarMark(existing);
                        }
                    }
                    repository.dao().insertCalendarMark(mark);
                    CourseReminderScheduler.rescheduleAll(requireContext());
                }, this::refresh));
    }

    private void toggleStopWeek() {
        CalendarMark existing = findStopWeek();
        if (existing != null) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("取消本周停课？")
                    .setMessage("课程会恢复显示，课程提醒也会重新开启。")
                    .setNegativeButton("保留", null)
                    .setPositiveButton("取消停课", (dialog, which) ->
                            repository.io(() -> {
                                repository.dao().deleteCalendarMark(existing);
                                CourseReminderScheduler.rescheduleAll(requireContext());
                            }, this::refresh))
                    .show();
            return;
        }
        EditText label = Ui.input(requireContext(), "停课原因，例如考试周、运动会");
        LinearLayout body = Ui.dialogBody(requireContext());
        body.addView(label);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("标记第 " + timetableWeek + " 周停课")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    CalendarMark mark = TimetableDialogs.stopWeekMark(
                            TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1),
                            label.getText().toString().trim()
                    );
                    repository.io(() -> {
                        repository.dao().insertCalendarMark(mark);
                        CourseReminderScheduler.rescheduleAll(requireContext());
                    }, this::refresh);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private CalendarMark findStopWeek() {
        if (selectedSemester == null) return null;
        LocalDate start = TimetableUtils.dateForWeek(selectedSemester, timetableWeek, 1);
        return TimetableUtils.findMark(loaded.marks, start, "stop_week");
    }

    private boolean isStopWeek() {
        return findStopWeek() != null;
    }

    private void changeWeek(int delta) {
        if (selectedSemester == null) return;
        int value = timetableWeek + delta;
        timetableWeek = Math.max(1, Math.min(selectedSemester.totalWeeks, value));
        render();
    }

    private TimetableUtils.Occurrence findOccurrence(
            List<TimetableUtils.Occurrence> occurrences, int period) {
        if (occurrences == null) return null;
        for (TimetableUtils.Occurrence occurrence : occurrences) {
            if (occurrence.containsPeriod(period)) return occurrence;
        }
        return null;
    }

    private Course findCanceledCourse(LocalDate date, int period) {
        for (CourseAdjustment adjustment : loaded.adjustments) {
            if (!"cancel".equals(adjustment.type)
                    || !date.format(DateUtils.ISO_DATE).equals(adjustment.originalDate)) continue;
            for (Course course : loaded.courses) {
                if (course.id == adjustment.courseId
                        && period >= course.startPeriod && period <= course.endPeriod) {
                    return course;
                }
            }
        }
        return null;
    }

    private String courseStatus(TimetableUtils.Occurrence occurrence,
                                List<TimetableUtils.Occurrence> dayOccurrences) {
        if (!occurrence.date.equals(LocalDate.now())) return occurrence.adjusted ? "调课" : "";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.of(occurrence.date, occurrence.start);
        LocalDateTime end = LocalDateTime.of(occurrence.date, occurrence.end);
        if (!now.isBefore(start) && !now.isAfter(end)) return "进行中";
        LocalDateTime nextStart = null;
        for (TimetableUtils.Occurrence item : dayOccurrences) {
            LocalDateTime itemStart = LocalDateTime.of(item.date, item.start);
            if (itemStart.isAfter(now)
                    && (nextStart == null || itemStart.isBefore(nextStart))) {
                nextStart = itemStart;
            }
        }
        if (nextStart != null && nextStart.equals(start)) return "即将开始";
        return "";
    }

    private void addEvent() {
        Dialogs.calendarEventForm(requireContext(), null,
                selectedDate.format(DateUtils.ISO_DATE), event ->
                        repository.io(() -> {
                            event.id = repository.dao().insertCalendarEvent(event);
                            CalendarReminderScheduler.schedule(requireContext(), event);
                        }, this::refresh));
    }

    private void editEvent(CalendarEvent event) {
        Dialogs.calendarEventForm(requireContext(), event,
                selectedDate.format(DateUtils.ISO_DATE), value ->
                        repository.io(() -> {
                            repository.dao().updateCalendarEvent(value);
                            CalendarReminderScheduler.cancel(requireContext(), value.id);
                            CalendarReminderScheduler.schedule(requireContext(), value);
                        }, this::refresh));
    }

    private void showEventMenu(View anchor, CalendarEvent event) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑日程");
        menu.getMenu().add("删除日程");
        menu.setOnMenuItemClickListener(item -> {
            if (String.valueOf(item.getTitle()).startsWith("编辑")) {
                editEvent(event);
            } else {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除这个日程？")
                        .setMessage("对应的本地提醒也会取消。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (dialog, which) ->
                                repository.io(() -> {
                                    CalendarReminderScheduler.cancel(requireContext(), event.id);
                                    repository.dao().deleteCalendarEvent(event);
                                }, this::refresh))
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private List<CalendarEvent> eventsOn(LocalDate date) {
        String prefix = date.format(DateUtils.ISO_DATE);
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent event : loaded.events) {
            if (event.startAt != null && event.startAt.startsWith(prefix)) result.add(event);
        }
        result.sort(Comparator.comparing(event -> event.startAt));
        return result;
    }

    private List<DayTask> dailyTasksOn(LocalDate date) {
        List<DayTask> result = new ArrayList<>();
        String value = date.format(DateUtils.ISO_DATE);
        for (Section section : loaded.sections) {
            if (!section.isSkipped && value.equals(section.planDate)) {
                result.add(DayTask.section(section));
            }
        }
        for (RepeatTask repeat : loaded.repeats) {
            if (DateUtils.repeatOccursOn(repeat, date)) {
                result.add(DayTask.repeat(repeat, repeatDone(repeat, value)));
            }
        }
        return result;
    }

    private boolean repeatDone(RepeatTask repeat, String date) {
        if (date.equals(repeat.lastCompletedDate)) return true;
        for (RepeatCompletion completion : loaded.repeatCompletions) {
            if (completion.repeatTaskId == repeat.id && date.equals(completion.date)) {
                return true;
            }
        }
        return false;
    }

    private List<Todo> todosOn(LocalDate date) {
        String value = date.format(DateUtils.ISO_DATE);
        List<Todo> result = new ArrayList<>();
        for (Todo todo : loaded.todos) {
            if (value.equals(todo.ddl)) result.add(todo);
        }
        result.sort(Comparator.comparing(todo -> todo.title));
        return result;
    }

    private List<StudyRecord> recordsOn(LocalDate date) {
        List<StudyRecord> result = new ArrayList<>();
        for (StudyRecord record : loaded.records) {
            if (date.equals(DateUtils.parseRecordDate(record))) result.add(record);
        }
        result.sort(Comparator.comparing(record -> record.startTime));
        return result;
    }

    private void setTaskDone(DayTask task, boolean done, TextView title) {
        applyCalendarDoneStyle(title, done, Ui.textColor(requireContext()));
        if (task.section != null) {
            task.section.isFinished = done;
            task.section.finishDate = done ? DateUtils.today() : "";
            repository.io(() -> repository.dao().updateSection(task.section),
                    this::refreshAfterAnimation);
        } else if (task.repeat != null) {
            String date = selectedDate.format(DateUtils.ISO_DATE);
            task.repeat.lastCompletedDate = done ? date : "";
            repository.io(() -> {
                repository.dao().updateRepeatTask(task.repeat);
                if (done) {
                    RepeatCompletion completion = new RepeatCompletion();
                    completion.repeatTaskId = task.repeat.id;
                    completion.date = date;
                    completion.completedAt = DateUtils.nowText();
                    repository.dao().insertRepeatCompletion(completion);
                } else {
                    repository.dao().deleteRepeatCompletion(task.repeat.id, date);
                }
            }, this::refreshAfterAnimation);
        }
    }

    private void refreshAfterAnimation() {
        if (content != null) content.postDelayed(this::refresh, 460);
    }

    private void applyCalendarDoneStyle(TextView title, boolean done, int activeColor) {
        if (done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(Ui.muted(requireContext()));
        } else {
            title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(activeColor);
        }
    }

    private String timeText(CalendarEvent event) {
        try {
            LocalDateTime start = LocalDateTime.parse(event.startAt, EVENT_FORMAT);
            LocalDateTime end = LocalDateTime.parse(event.endAt, EVENT_FORMAT);
            return start.toLocalTime().format(TIME_FORMAT) + "–"
                    + end.toLocalTime().format(TIME_FORMAT);
        } catch (Exception ignored) {
            return event.startAt;
        }
    }

    private String reminderText(int minutes) {
        if (minutes < 0) return "不提醒";
        if (minutes == 0) return "日程开始时";
        if (minutes < 60) return "提前 " + minutes + " 分钟";
        if (minutes == 60) return "提前 1 小时";
        if (minutes == 1440) return "提前 1 天";
        return "提前 " + minutes + " 分钟";
    }

    private String repeatText(RepeatTask task) {
        switch (task.repeatType) {
            case "weekly": return "每周 " + task.repeatValue;
            case "monthly": return "每月 " + task.repeatValue + " 日";
            default: return "每天";
        }
    }

    private String weekdayCn(LocalDate date) {
        return "周" + new String[]{"一", "二", "三", "四", "五", "六", "日"}
                [date.getDayOfWeek().getValue() - 1];
    }

    private String weekdayShort(LocalDate date) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}
                [date.getDayOfWeek().getValue() - 1];
    }

    private TextView monthAction(String label) {
        TextView view = Ui.text(requireContext(), label, 25,
                Ui.accent(requireContext()), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(requireContext(), 12), 0,
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 3));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        content = null;
        super.onDestroyView();
    }

    private static class Loaded {
        List<CalendarEvent> events = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        List<RepeatTask> repeats = new ArrayList<>();
        List<Todo> todos = new ArrayList<>();
        List<Semester> semesters = new ArrayList<>();
        List<Course> courses = new ArrayList<>();
        List<CourseAdjustment> adjustments = new ArrayList<>();
        List<CalendarMark> marks = new ArrayList<>();
        List<Subject> subjects = new ArrayList<>();
        List<RepeatCompletion> repeatCompletions = new ArrayList<>();
        List<StudyRecord> records = new ArrayList<>();
    }

    private static class DayTask {
        Section section;
        RepeatTask repeat;
        String title;
        boolean done;

        static DayTask section(Section section) {
            DayTask task = new DayTask();
            task.section = section;
            task.title = section.title;
            task.done = section.isFinished;
            return task;
        }

        static DayTask repeat(RepeatTask repeat, boolean done) {
            DayTask task = new DayTask();
            task.repeat = repeat;
            task.title = repeat.title;
            task.done = done;
            return task;
        }
    }

    private class SwipeLayout extends FrameLayout {
        private float downX;
        private float downY;
        private boolean horizontal;
        private SwipeDirection listener;

        SwipeLayout(android.content.Context context) {
            super(context);
        }

        void setOnSwipe(SwipeDirection listener) {
            this.listener = listener;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                horizontal = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                horizontal = dx > Ui.dp(requireContext(), 18) && dx > dy * 1.4f;
            }
            return horizontal;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX;
                if (Math.abs(dx) > Ui.dp(requireContext(), 45) && listener != null) {
                    listener.onSwipe(dx < 0 ? 1 : -1);
                }
            }
            return true;
        }
    }

    private interface SwipeDirection {
        void onSwipe(int direction);
    }
}
