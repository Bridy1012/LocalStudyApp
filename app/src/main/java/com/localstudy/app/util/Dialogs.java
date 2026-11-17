package com.localstudy.app.util;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.localstudy.app.data.CalendarEvent;
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.data.Subject;
import com.localstudy.app.data.Todo;
import com.localstudy.app.service.TimerService;
import com.localstudy.app.service.TimerState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Dialogs {
    private Dialogs() {
    }

    public interface SubjectCallback {
        void onSubject(Subject subject);
    }

    public interface TodoCallback {
        void onTodo(Todo todo);
    }

    public interface SectionCallback {
        void onSection(Section section);
    }

    public interface RepeatCallback {
        void onRepeat(RepeatTask task);
    }

    public interface RecordCallback {
        void onRecord(StudyRecord record);
    }

    public interface CalendarEventCallback {
        void onEvent(CalendarEvent event);
    }

    public static void pickDate(Context context, String initial, DateValue callback) {
        LocalDate date = DateUtils.isValidDate(initial) ? LocalDate.parse(initial) : LocalDate.now();
        DatePickerDialog dialog = new DatePickerDialog(
                context,
                (view, year, month, day) -> callback.onDate(
                        LocalDate.of(year, month + 1, day).format(DateUtils.ISO_DATE)
                ),
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth()
        );
        dialog.show();
    }

    public static void subjectForm(Context context, Subject existing, SubjectCallback callback) {
        Subject subject = existing == null ? new Subject() : existing;
        LinearLayout body = Ui.dialogBody(context);
        EditText name = Ui.input(context, "科目名称");
        name.setText(subject.name);
        EditText remark = Ui.input(context, "备注（可选）");
        remark.setText(subject.remark);
        TextView ruleLabel = Ui.caption(context, "默认计时规则");
        ruleLabel.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6));
        Spinner mode = new Spinner(context);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"正计时（最长 3 小时）", "倒计时"}
        );
        mode.setAdapter(modeAdapter);
        mode.setSelection("countdown".equals(subject.timerMode) ? 1 : 0);
        EditText duration = Ui.input(context, "倒计时分钟数");
        duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        duration.setText(String.valueOf(subject.timerDuration > 0 ? subject.timerDuration : 25));
        mode.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            duration.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        }));

        body.addView(name);
        body.addView(remark);
        body.addView(ruleLabel);
        body.addView(mode);
        body.addView(duration);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增科目" : "编辑科目")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) {
                        name.setError("请输入科目名称");
                        return;
                    }
                    int minutes = parseInt(duration.getText().toString(), 25);
                    if (minutes < 1) {
                        duration.setError("至少 1 分钟");
                        return;
                    }
                    subject.name = title;
                    subject.remark = remark.getText().toString().trim();
                    subject.timerMode = mode.getSelectedItemPosition() == 1 ? "countdown" : "positive";
                    subject.timerDuration = minutes;
                    callback.onSubject(subject);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void todoForm(Context context, Todo existing, List<Subject> subjects, TodoCallback callback) {
        Todo todo = existing == null ? new Todo() : existing;
        LinearLayout body = Ui.dialogBody(context);
        EditText title = Ui.input(context, "待办标题");
        title.setText(todo.title);
        EditText note = Ui.input(context, "备注（可选）");
        note.setText(todo.note);
        TextView date = dateField(context, DateUtils.isValidDate(todo.ddl) ? todo.ddl : DateUtils.today());
        Spinner subject = subjectSpinner(context, subjects, todo.subjectId);
        body.addView(title);
        body.addView(note);
        body.addView(label(context, "截止日期"));
        body.addView(date);
        body.addView(label(context, "绑定科目（可选）"));
        body.addView(subject);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增待办" : "编辑待办")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (title.getText().toString().trim().isEmpty()) {
                        title.setError("请输入标题");
                        return;
                    }
                    todo.title = title.getText().toString().trim();
                    todo.note = note.getText().toString().trim();
                    todo.ddl = String.valueOf(date.getTag());
                    todo.subjectId = selectedSubjectId(subject, subjects);
                    if (todo.createdAt == null || todo.createdAt.isEmpty()) {
                        todo.createdAt = DateUtils.nowText();
                    }
                    callback.onTodo(todo);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void sectionForm(Context context, Section existing, List<Subject> subjects,
                                   Long forcedSubjectId, SectionCallback callback) {
        sectionForm(context, existing, subjects, forcedSubjectId, DateUtils.today(), callback);
    }

    public static void sectionForm(Context context, Section existing, List<Subject> subjects,
                                   Long forcedSubjectId, String defaultPlanDate,
                                   SectionCallback callback) {
        Section section = existing == null ? new Section() : existing;
        LinearLayout body = Ui.dialogBody(context);
        EditText title = Ui.input(context, "任务或小节标题");
        title.setText(section.title);
        EditText note = Ui.input(context, "备注（可选）");
        note.setText(section.note);
        TextView date = dateField(context, DateUtils.isValidDate(section.planDate)
                ? section.planDate : defaultPlanDate);
        Spinner subject = subjectSpinner(context, subjects,
                forcedSubjectId != null ? forcedSubjectId : section.subjectId);
        subject.setEnabled(forcedSubjectId == null);
        body.addView(title);
        body.addView(note);
        body.addView(label(context, "计划日期"));
        body.addView(date);
        body.addView(label(context, "所属科目（可选）"));
        body.addView(subject);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增任务" : "编辑任务")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (title.getText().toString().trim().isEmpty()) {
                        title.setError("请输入标题");
                        return;
                    }
                    section.title = title.getText().toString().trim();
                    section.note = note.getText().toString().trim();
                    section.planDate = String.valueOf(date.getTag());
                    section.subjectId = forcedSubjectId != null
                            ? forcedSubjectId : selectedSubjectId(subject, subjects);
                    if (section.source == null || section.source.isEmpty()) {
                        section.source = "manual";
                    }
                    callback.onSection(section);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void repeatForm(Context context, RepeatTask existing, List<Subject> subjects,
                                  RepeatCallback callback) {
        RepeatTask task = existing == null ? new RepeatTask() : existing;
        LinearLayout body = Ui.dialogBody(context);
        EditText title = Ui.input(context, "重复任务标题");
        title.setText(task.title);
        Spinner subject = subjectSpinner(context, subjects, task.subjectId);
        Spinner type = new Spinner(context);
        type.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"每天", "每周", "每月"}
        ));
        type.setSelection("weekly".equals(task.repeatType) ? 1
                : "monthly".equals(task.repeatType) ? 2 : 0);
        TextView valueLabel = label(context, "");
        EditText value = Ui.input(context, "");
        updateRepeatValueFields(task.repeatType, valueLabel, value);
        type.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            String mode = position == 0 ? "daily" : position == 1 ? "weekly" : "monthly";
            updateRepeatValueFields(mode, valueLabel, value);
        }));

        body.addView(title);
        body.addView(label(context, "绑定科目（可选）"));
        body.addView(subject);
        body.addView(label(context, "重复周期"));
        body.addView(type);
        body.addView(valueLabel);
        body.addView(value);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增重复任务" : "编辑重复任务")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (title.getText().toString().trim().isEmpty()) {
                        title.setError("请输入标题");
                        return;
                    }
                    int position = type.getSelectedItemPosition();
                    String mode = position == 0 ? "daily" : position == 1 ? "weekly" : "monthly";
                    String repeatValue = normalizeRepeatValue(mode, value.getText().toString());
                    if (!"daily".equals(mode) && repeatValue.isEmpty()) {
                        value.setError("请输入有效日期");
                        return;
                    }
                    task.title = title.getText().toString().trim();
                    task.subjectId = selectedSubjectId(subject, subjects);
                    task.repeatType = mode;
                    task.repeatValue = repeatValue;
                    if (task.createdAt == null || task.createdAt.isEmpty()) {
                        task.createdAt = DateUtils.nowText();
                    }
                    callback.onRepeat(task);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void timerRule(Context context, String title, String subjectName,
                                 Long subjectId, Long sectionId, Long todoId,
                                 Long repeatId, String presetMode, int presetDuration) {
        if (TimerState.isActive(context)) {
            Toast.makeText(context, "请先结束当前计时", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout body = Ui.dialogBody(context);
        TextView task = Ui.text(context, title, 15, Ui.textColor(context), true);
        TextView subject = Ui.caption(context, "科目：" + subjectName);
        subject.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 12));
        RadioGroup modes = new RadioGroup(context);
        RadioButton positive = new RadioButton(context);
        positive.setText("正计时（最长 3 小时）");
        positive.setId(View.generateViewId());
        RadioButton countdown = new RadioButton(context);
        countdown.setText("倒计时");
        countdown.setId(View.generateViewId());
        modes.addView(positive);
        modes.addView(countdown);
        Spinner presets = new Spinner(context);
        presets.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"25 分钟", "45 分钟", "60 分钟", "自定义"}
        ));
        EditText custom = Ui.input(context, "自定义分钟数");
        custom.setInputType(InputType.TYPE_CLASS_NUMBER);
        custom.setText(String.valueOf(presetDuration > 0 ? presetDuration : 25));
        custom.setVisibility(View.GONE);
        int presetIndex = presetDuration == 25 ? 0 : presetDuration == 45 ? 1
                : presetDuration == 60 ? 2 : 3;
        presets.setSelection(presetIndex);

        boolean startCountdown = "countdown".equals(presetMode);
        if (startCountdown) {
            countdown.setChecked(true);
            presets.setVisibility(View.VISIBLE);
        } else {
            positive.setChecked(true);
            presets.setVisibility(View.GONE);
        }
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCountdown = checkedId == countdown.getId();
            presets.setVisibility(isCountdown ? View.VISIBLE : View.GONE);
            custom.setVisibility(isCountdown && presets.getSelectedItemPosition() == 3
                    ? View.VISIBLE : View.GONE);
        });
        presets.setOnItemSelectedListener(new SimpleItemSelected(position ->
                custom.setVisibility(countdown.isChecked() && position == 3 ? View.VISIBLE : View.GONE)));

        body.addView(task);
        body.addView(subject);
        body.addView(modes);
        body.addView(presets);
        body.addView(custom);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("开始学习")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (countdown.isChecked()) {
                        int minutes;
                        int position = presets.getSelectedItemPosition();
                        if (position == 0) minutes = 25;
                        else if (position == 1) minutes = 45;
                        else if (position == 2) minutes = 60;
                        else minutes = parseInt(custom.getText().toString(), 0);
                        if (minutes < 1 || minutes > 600) {
                            custom.setError("请输入 1-600 分钟");
                            return;
                        }
                        TimerService.start(context, "countdown", minutes * 60L, title, subjectName,
                                subjectId, sectionId, todoId, repeatId);
                    } else {
                        TimerService.start(context, "positive", 0, title, subjectName,
                                subjectId, sectionId, todoId, repeatId);
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void recordForm(Context context, StudyRecord existing, List<Subject> subjects,
                                  RecordCallback callback) {
        StudyRecord record = existing == null ? new StudyRecord() : existing;
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        LinearLayout body = Ui.dialogBody(context);
        Spinner subject = subjectSpinner(context, subjects, record.subjectId);
        String dayValue = record.startTime != null && record.startTime.length() >= 10
                ? record.startTime.substring(0, 10) : DateUtils.today();
        LocalTime defaultStart = LocalTime.now().withSecond(0).withNano(0).minusHours(1);
        LocalTime startTime = parseTime(record.startTime, defaultStart);
        LocalDateTime startDateTime = LocalDateTime.of(LocalDate.parse(dayValue), startTime);
        LocalDateTime endDateTime = parseDateTime(record.endTime);
        if (endDateTime == null || !endDateTime.isAfter(startDateTime)) {
            endDateTime = startDateTime.plusSeconds(Math.max(60, record.durationSec));
        }
        final LocalDateTime initialRecordEnd = endDateTime;

        TextView date = dateField(context, dayValue);
        TextView rangeSummary = Ui.caption(context, "");
        rangeSummary.setPadding(0, Ui.dp(context, 8), 0, 0);
        final LocalDateTime[] currentStart = {startDateTime};
        final LocalDateTime[] currentEnd = {endDateTime};
        Runnable updateSummary = () -> {
            long seconds = Math.max(0,
                    Duration.between(currentStart[0], currentEnd[0]).getSeconds());
            rangeSummary.setText("本次学习 " + DateUtils.durationHuman(seconds));
        };
        final TextView startField = timeField(context, "开始时间",
                startTime.format(timeFormat), null);
        final TextView endField = timeField(context, "结束时间",
                endDateTime.toLocalTime().format(timeFormat), null);
        startField.setOnClickListener(view -> new TimePickerDialog(
                context,
                (picker, hour, minute) -> {
                    String value = String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute);
                    startField.setTag(value);
                    startField.setText("开始时间  " + value);
                    LocalDate dateValue = parseDate(String.valueOf(date.getTag()), LocalDate.now());
                    currentStart[0] = LocalDateTime.of(dateValue, LocalTime.of(hour, minute));
                    if (!currentEnd[0].isAfter(currentStart[0])) {
                        currentEnd[0] = currentStart[0].plusHours(1);
                        endField.setTag(currentEnd[0].toLocalTime().format(timeFormat));
                        endField.setText("结束时间  " + currentEnd[0].toLocalTime().format(timeFormat));
                    }
                    updateSummary.run();
                },
                parseTime(String.valueOf(startField.getTag()), startTime).getHour(),
                parseTime(String.valueOf(startField.getTag()), startTime).getMinute(),
                true
        ).show());
        endField.setOnClickListener(view -> new TimePickerDialog(
                context,
                (picker, hour, minute) -> {
                    String value = String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute);
                    endField.setTag(value);
                    endField.setText("结束时间  " + value);
                    LocalDate dateValue = parseDate(String.valueOf(date.getTag()), LocalDate.now());
                    currentEnd[0] = LocalDateTime.of(dateValue, LocalTime.of(hour, minute));
                    if (!currentEnd[0].isAfter(currentStart[0])) {
                        currentEnd[0] = currentEnd[0].plusDays(1);
                    }
                    updateSummary.run();
                },
                parseTime(String.valueOf(endField.getTag()), initialRecordEnd.toLocalTime()).getHour(),
                parseTime(String.valueOf(endField.getTag()), initialRecordEnd.toLocalTime()).getMinute(),
                true
        ).show());
        updateSummary.run();

        body.addView(label(context, "科目"));
        body.addView(subject);
        body.addView(label(context, "学习日期"));
        body.addView(date);
        LinearLayout timeRow = Ui.horizontal(context);
        Ui.setWeight(startField, 1);
        View spacer = Ui.space(context, 1);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
        Ui.setWeight(endField, 1);
        timeRow.addView(startField);
        timeRow.addView(spacer);
        timeRow.addView(endField);
        body.addView(label(context, "学习时段"));
        body.addView(timeRow);
        body.addView(rangeSummary);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "补记学习" : "编辑学习记录")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    LocalDate selectedDate = parseDate(String.valueOf(date.getTag()), LocalDate.now());
                    LocalTime selectedStart = parseTime(
                            String.valueOf(startField.getTag()), LocalTime.of(9, 0));
                    LocalTime selectedEnd = parseTime(
                            String.valueOf(endField.getTag()), selectedStart.plusHours(1));
                    LocalDateTime start = LocalDateTime.of(selectedDate, selectedStart);
                    LocalDateTime end = LocalDateTime.of(selectedDate, selectedEnd);
                    if (!end.isAfter(start)) end = end.plusDays(1);
                    long total = Duration.between(start, end).getSeconds();
                    if (total <= 0) {
                        rangeSummary.setText("结束时间必须晚于开始时间");
                        rangeSummary.setTextColor(Ui.danger(context));
                        return;
                    }
                    int index = subject.getSelectedItemPosition();
                    if (index == 0) {
                        record.subjectId = null;
                        record.subjectName = "其他";
                    } else {
                        Subject selected = subjects.get(index - 1);
                        record.subjectId = selected.id;
                        record.subjectName = selected.name;
                    }
                    record.startTime = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    record.endTime = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    record.durationSec = total;
                    callback.onRecord(record);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void calendarEventForm(Context context, CalendarEvent existing,
                                         String selectedDate,
                                         CalendarEventCallback callback) {
        CalendarEvent event = existing == null ? new CalendarEvent() : existing;
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        LocalDate initialDate = parseDate(
                existing != null && existing.startAt.length() >= 10
                        ? existing.startAt.substring(0, 10) : selectedDate,
                LocalDate.now()
        );
        LocalTime initialStart = existing != null
                ? parseTime(existing.startAt, LocalTime.of(9, 0))
                : (initialDate.equals(LocalDate.now())
                ? LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
                : LocalTime.of(9, 0));
        LocalDateTime start = LocalDateTime.of(initialDate, initialStart);
        LocalDateTime end = parseDateTime(existing == null ? "" : existing.endAt);
        if (end == null || !end.isAfter(start)) end = start.plusHours(1);
        final LocalDateTime initialCalendarEnd = end;

        LinearLayout body = Ui.dialogBody(context);
        EditText title = Ui.input(context, "日程标题");
        title.setText(event.title);
        EditText note = Ui.input(context, "备注（可选）");
        note.setText(event.note);
        TextView date = dateField(context, initialDate.format(DateUtils.ISO_DATE));
        final TextView startField = timeField(context, "开始时间",
                start.toLocalTime().format(timeFormat), null);
        final TextView endField = timeField(context, "结束时间",
                end.toLocalTime().format(timeFormat), null);
        startField.setOnClickListener(view -> new TimePickerDialog(
                context,
                (picker, hour, minute) -> {
                    String value = String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute);
                    startField.setTag(value);
                    startField.setText("开始时间  " + value);
                },
                parseTime(String.valueOf(startField.getTag()), start.toLocalTime()).getHour(),
                parseTime(String.valueOf(startField.getTag()), start.toLocalTime()).getMinute(),
                true
        ).show());
        endField.setOnClickListener(view -> new TimePickerDialog(
                context,
                (picker, hour, minute) -> {
                    String value = String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute);
                    endField.setTag(value);
                    endField.setText("结束时间  " + value);
                },
                parseTime(String.valueOf(endField.getTag()), initialCalendarEnd.toLocalTime()).getHour(),
                parseTime(String.valueOf(endField.getTag()), initialCalendarEnd.toLocalTime()).getMinute(),
                true
        ).show());

        String[] reminderLabels = {
                "不提醒", "日程开始时", "提前 5 分钟", "提前 10 分钟",
                "提前 30 分钟", "提前 1 小时", "提前 1 天"
        };
        int[] reminderValues = {-1, 0, 5, 10, 30, 60, 1440};
        Spinner reminder = new Spinner(context);
        reminder.setAdapter(new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_dropdown_item, reminderLabels
        ));
        int selectedReminder = 1;
        for (int i = 0; i < reminderValues.length; i++) {
            if (reminderValues[i] == event.reminderMinutes) selectedReminder = i;
        }
        reminder.setSelection(selectedReminder);

        body.addView(title);
        body.addView(note);
        body.addView(label(context, "日程日期"));
        body.addView(date);
        LinearLayout timeRow = Ui.horizontal(context);
        Ui.setWeight(startField, 1);
        View spacer = Ui.space(context, 1);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
        Ui.setWeight(endField, 1);
        timeRow.addView(startField);
        timeRow.addView(spacer);
        timeRow.addView(endField);
        body.addView(label(context, "时间"));
        body.addView(timeRow);
        body.addView(label(context, "提醒"));
        body.addView(reminder);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增日历日程" : "编辑日历日程")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (title.getText().toString().trim().isEmpty()) {
                        title.setError("请输入日程标题");
                        return;
                    }
                    LocalDate day = parseDate(String.valueOf(date.getTag()), LocalDate.now());
                    LocalTime startTime = parseTime(String.valueOf(startField.getTag()), LocalTime.of(9, 0));
                    LocalTime endTime = parseTime(String.valueOf(endField.getTag()), startTime.plusHours(1));
                    LocalDateTime startDateTime = LocalDateTime.of(day, startTime);
                    LocalDateTime endDateTime = LocalDateTime.of(day, endTime);
                    if (!endDateTime.isAfter(startDateTime)) endDateTime = endDateTime.plusDays(1);
                    event.title = title.getText().toString().trim();
                    event.note = note.getText().toString().trim();
                    event.startAt = startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    event.endAt = endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    event.reminderMinutes = reminderValues[reminder.getSelectedItemPosition()];
                    if (event.createdAt == null || event.createdAt.isEmpty()) {
                        event.createdAt = DateUtils.nowText();
                    }
                    callback.onEvent(event);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static TextView dateField(Context context, String initial) {
        TextView view = Ui.text(context, DateUtils.friendlyDate(initial) + "  " + initial,
                14, Ui.textColor(context), false);
        view.setTag(initial);
        view.setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
        view.setBackground(Ui.outlined(context, Ui.surfaceColor(context), Ui.border(context), 8));
        view.setOnClickListener(ignored -> pickDate(context, String.valueOf(view.getTag()), date -> {
            view.setTag(date);
            view.setText(DateUtils.friendlyDate(date) + "  " + date);
        }));
        return view;
    }

    private static TextView timeField(Context context, String label, String initial,
                                      Runnable ignored) {
        TextView view = Ui.text(context, label + "  " + initial,
                13, Ui.textColor(context), false);
        view.setTag(initial);
        view.setPadding(Ui.dp(context, 10), Ui.dp(context, 12),
                Ui.dp(context, 10), Ui.dp(context, 12));
        view.setBackground(Ui.outlined(context, Ui.surfaceColor(context), Ui.border(context), 8));
        return view;
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        if (value != null && value.matches(".*\\d{2}:\\d{2}.*")) {
            try {
                String segment = value.replaceAll(".*?(\\d{2}:\\d{2}).*", "$1");
                return LocalTime.parse(segment);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value, DateUtils.ISO_DATE);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Spinner subjectSpinner(Context context, List<Subject> subjects, Long selectedId) {
        Spinner spinner = new Spinner(context);
        List<String> labels = new ArrayList<>();
        labels.add("不绑定科目");
        int selected = 0;
        for (int i = 0; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            labels.add(subject.name);
            if (selectedId != null && selectedId == subject.id) {
                selected = i + 1;
            }
        }
        spinner.setAdapter(new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_dropdown_item, labels
        ));
        spinner.setSelection(selected);
        return spinner;
    }

    private static Long selectedSubjectId(Spinner spinner, List<Subject> subjects) {
        int position = spinner.getSelectedItemPosition();
        return position <= 0 || position > subjects.size() ? null : subjects.get(position - 1).id;
    }

    private static TextView label(Context context, String label) {
        TextView view = Ui.caption(context, label);
        view.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6));
        return view;
    }

    private static void updateRepeatValueFields(String mode, TextView label, EditText value) {
        if ("weekly".equals(mode)) {
            label.setText("周几（1=周一，多选用逗号，例如 1,3,5）");
            value.setHint("1,3,5");
            value.setVisibility(View.VISIBLE);
            label.setVisibility(View.VISIBLE);
        } else if ("monthly".equals(mode)) {
            label.setText("每月几号（多选逗号分隔，例如 1,15）");
            value.setHint("1,15");
            value.setVisibility(View.VISIBLE);
            label.setVisibility(View.VISIBLE);
        } else {
            label.setVisibility(View.GONE);
            value.setVisibility(View.GONE);
        }
    }

    private static String normalizeRepeatValue(String mode, String input) {
        if ("daily".equals(mode)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int max = "weekly".equals(mode) ? 7 : 31;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (String token : input.split("[,，\\s]+")) {
            try {
                int value = Integer.parseInt(token.trim());
                if (value >= 1 && value <= max && seen.add(value)) {
                    if (result.length() > 0) result.append(',');
                    result.append(value);
                }
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public interface DateValue {
        void onDate(String date);
    }

    private static class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;

        SimpleItemSelected(PositionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            callback.onPosition(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private interface PositionCallback {
        void onPosition(int position);
    }
}
