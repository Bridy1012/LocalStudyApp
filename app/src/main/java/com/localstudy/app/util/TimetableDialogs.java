package com.localstudy.app.util;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.localstudy.app.data.CalendarMark;
import com.localstudy.app.data.Course;
import com.localstudy.app.data.CourseAdjustment;
import com.localstudy.app.data.Semester;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TimetableDialogs {
    public interface SemesterCallback {
        void onSemester(Semester semester);
    }

    public interface CourseCallback {
        void onCourse(Course course);
    }

    public interface CoursesCallback {
        void onCourses(List<Course> courses);
    }

    public interface AdjustmentCallback {
        void onAdjustment(CourseAdjustment adjustment);
    }

    public interface MarkCallback {
        void onMark(CalendarMark mark);
    }

    private TimetableDialogs() {
    }

    public static void semesterForm(Context context, Semester existing,
                                    SemesterCallback callback) {
        Semester semester = existing == null ? new Semester() : existing;
        LinearLayout body = Ui.dialogBody(context);
        EditText name = Ui.input(context, "学期名称，例如 2026 秋季学期");
        name.setText(semester.name);
        if (semester.name == null || semester.name.isEmpty()) {
            name.setText(LocalDate.now().getYear() + " 学期");
        }
        TextView startDate = dateField(context,
                semester.startDate == null || semester.startDate.isEmpty()
                        ? TimetableUtils.weekStart(LocalDate.now()).format(DateUtils.ISO_DATE)
                        : semester.startDate);
        EditText totalWeeks = Ui.input(context, "总周数");
        totalWeeks.setInputType(InputType.TYPE_CLASS_NUMBER);
        totalWeeks.setText(String.valueOf(semester.totalWeeks > 0 ? semester.totalWeeks : 18));
        EditText duration = numberInput(context, "每节课时长（分钟）",
                semester.classDurationMinutes > 0 ? semester.classDurationMinutes : 45);
        EditText rest = numberInput(context, "课间休息（分钟）",
                semester.breakMinutes >= 0 ? semester.breakMinutes : 10);
        EditText morningStart = Ui.input(context, "上午开始时间 HH:mm");
        morningStart.setText(semester.morningStart == null || semester.morningStart.isEmpty()
                ? "08:00" : semester.morningStart);
        EditText morningCount = numberInput(context, "上午节数",
                semester.morningPeriods > 0 ? semester.morningPeriods : 2);
        EditText afternoonStart = Ui.input(context, "下午开始时间 HH:mm");
        afternoonStart.setText(semester.afternoonStart == null || semester.afternoonStart.isEmpty()
                ? "14:00" : semester.afternoonStart);
        EditText afternoonCount = numberInput(context, "下午节数",
                semester.afternoonPeriods > 0 ? semester.afternoonPeriods : 2);
        EditText eveningStart = Ui.input(context, "晚上开始时间 HH:mm");
        eveningStart.setText(semester.eveningStart == null || semester.eveningStart.isEmpty()
                ? "19:00" : semester.eveningStart);
        EditText eveningCount = numberInput(context, "晚上节数",
                semester.eveningPeriods > 0 ? semester.eveningPeriods : 1);
        Semester previewSemester = copyScheduleSettings(semester, duration, rest,
                morningStart, morningCount, afternoonStart, afternoonCount,
                eveningStart, eveningCount);
        TextView preview = Ui.caption(context,
                "自动节次：" + TimetableUtils.generatePeriodTimes(previewSemester).replace(";", "  |  "));
        preview.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 4));
        Button template = Ui.button(context, "恢复常见模板（5 节 × 45 分钟）", false);
        template.setOnClickListener(view -> {
            duration.setText("45");
            rest.setText("10");
            morningStart.setText("08:00");
            morningCount.setText("2");
            afternoonStart.setText("14:00");
            afternoonCount.setText("2");
            eveningStart.setText("19:00");
            eveningCount.setText("1");
            Semester value = copyScheduleSettings(semester, duration, rest,
                    morningStart, morningCount, afternoonStart, afternoonCount,
                    eveningStart, eveningCount);
            preview.setText("自动节次："
                    + TimetableUtils.generatePeriodTimes(value).replace(";", "  |  "));
        });

        body.addView(name);
        body.addView(label(context, "第一周周一"));
        body.addView(startDate);
        body.addView(label(context, "学期设置"));
        body.addView(totalWeeks);
        body.addView(label(context, "课堂时间规则"));
        body.addView(duration);
        body.addView(rest);
        body.addView(label(context, "上午"));
        body.addView(morningStart);
        body.addView(morningCount);
        body.addView(label(context, "下午"));
        body.addView(afternoonStart);
        body.addView(afternoonCount);
        body.addView(label(context, "晚上"));
        body.addView(eveningStart);
        body.addView(eveningCount);
        body.addView(preview);
        body.addView(template);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新建学期课表" : "学期设置")
                .setView(wrapScroll(context, body))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError("请输入学期名称");
                        return;
                    }
                    int weekCount = parseInt(totalWeeks.getText().toString(), 0);
                    if (weekCount < 1 || weekCount > 40) {
                        totalWeeks.setError("请输入 1-40 周");
                        return;
                    }
                    int durationValue = parseInt(duration.getText().toString(), 0);
                    int breakValue = parseInt(rest.getText().toString(), -1);
                    int morningValue = parseInt(morningCount.getText().toString(), 0);
                    int afternoonValue = parseInt(afternoonCount.getText().toString(), 0);
                    int eveningValue = parseInt(eveningCount.getText().toString(), 0);
                    int periodCount = morningValue + afternoonValue + eveningValue;
                    if (durationValue < 20 || durationValue > 120) {
                        duration.setError("请输入 20-120 分钟");
                        return;
                    }
                    if (breakValue < 0 || breakValue > 60) {
                        rest.setError("请输入 0-60 分钟");
                        return;
                    }
                    if (periodCount < 1 || periodCount > 12) {
                        morningCount.setError("上午、下午、晚上合计需为 1-12 节");
                        return;
                    }
                    if (!isTime(morningStart.getText().toString())
                            || !isTime(afternoonStart.getText().toString())
                            || !isTime(eveningStart.getText().toString())) {
                        morningStart.setError("请输入 HH:mm 格式");
                        return;
                    }
                    semester.name = value;
                    semester.startDate = String.valueOf(startDate.getTag());
                    semester.totalWeeks = weekCount;
                    semester.periodsPerDay = periodCount;
                    semester.classDurationMinutes = durationValue;
                    semester.breakMinutes = breakValue;
                    semester.morningPeriods = morningValue;
                    semester.afternoonPeriods = afternoonValue;
                    semester.eveningPeriods = eveningValue;
                    semester.morningStart = morningStart.getText().toString().trim();
                    semester.afternoonStart = afternoonStart.getText().toString().trim();
                    semester.eveningStart = eveningStart.getText().toString().trim();
                    semester.periodTimes = TimetableUtils.generatePeriodTimes(semester);
                    if (semester.createdAt == null || semester.createdAt.isEmpty()) {
                        semester.createdAt = DateUtils.nowText();
                    }
                    callback.onSemester(semester);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void courseForm(Context context, Semester semester, Course existing,
                                  CourseCallback callback) {
        Course course = existing == null ? new Course() : existing;
        int totalWeeks = Math.max(1, semester.totalWeeks);
        int periods = Math.max(1, semester.periodsPerDay);
        LinearLayout body = Ui.dialogBody(context);
        EditText name = Ui.input(context, "课程名称");
        name.setText(course.name);
        EditText classroom = Ui.input(context, "上课教室");
        classroom.setText(course.classroom);
        EditText teacher = Ui.input(context, "授课老师");
        teacher.setText(course.teacher);
        EditText note = Ui.input(context, "课程备注，例如点名、带电脑");
        note.setText(course.note);

        Spinner day = new Spinner(context);
        day.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"}));
        day.setSelection(Math.max(0, Math.min(6, course.dayOfWeek - 1)));
        LinearLayout periodRow = Ui.horizontal(context);
        Spinner start = periodSpinner(context, periods, course.startPeriod);
        Spinner end = periodSpinner(context, periods, course.endPeriod);
        Ui.setWeight(start, 1);
        Ui.setWeight(end, 1);
        periodRow.addView(start);
        periodRow.addView(Ui.space(context, 8));
        periodRow.addView(end);

        Set<Integer> selectedWeeks = TimetableUtils.parseWeeks(course.weeks);
        if (selectedWeeks.isEmpty()) {
            for (int week = 1; week <= totalWeeks; week++) selectedWeeks.add(week);
        }
        final Set<Integer> weeks = new HashSet<>(selectedWeeks);
        LinearLayout weekGrid = Ui.vertical(context);
        List<TextView> weekCells = new ArrayList<>();
        LinearLayout weekRow = null;
        for (int week = 1; week <= totalWeeks; week++) {
            if ((week - 1) % 6 == 0) {
                weekRow = Ui.horizontal(context);
                weekGrid.addView(weekRow);
            }
            final int weekNumber = week;
            TextView cell = Ui.text(context, String.valueOf(week), 12,
                    Ui.textColor(context), true);
            cell.setGravity(Gravity.CENTER);
            cell.setOnClickListener(view -> {
                if (!weeks.remove(weekNumber)) weeks.add(weekNumber);
                refreshWeekCells(context, weekCells, weeks);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(context, 34), 1
            );
            params.setMargins(Ui.dp(context, 2), Ui.dp(context, 2),
                    Ui.dp(context, 2), Ui.dp(context, 2));
            weekRow.addView(cell, params);
            weekCells.add(cell);
        }
        refreshWeekCells(context, weekCells, weeks);
        LinearLayout quickWeeks = Ui.horizontal(context);
        quickWeeks.addView(weekQuickButton(context, "全选", () -> {
            weeks.clear();
            for (int week = 1; week <= totalWeeks; week++) weeks.add(week);
            refreshWeekCells(context, weekCells, weeks);
        }));
        quickWeeks.addView(weekQuickButton(context, "单周", () -> {
            weeks.clear();
            for (int week = 1; week <= totalWeeks; week += 2) weeks.add(week);
            refreshWeekCells(context, weekCells, weeks);
        }));
        quickWeeks.addView(weekQuickButton(context, "双周", () -> {
            weeks.clear();
            for (int week = 2; week <= totalWeeks; week += 2) weeks.add(week);
            refreshWeekCells(context, weekCells, weeks);
        }));
        quickWeeks.addView(weekQuickButton(context, "清空", () -> {
            weeks.clear();
            refreshWeekCells(context, weekCells, weeks);
        }));

        final int[] color = {course.color == 0 ? Ui.chartColor(0) : course.color};
        LinearLayout colorRow = Ui.horizontal(context);
        List<View> swatches = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int value = Ui.chartColor(i);
            View swatch = new View(context);
            swatch.setBackground(Ui.rounded(context, value, 20));
            swatch.setOnClickListener(view -> {
                color[0] = value;
                for (int j = 0; j < swatches.size(); j++) {
                    swatches.get(j).setAlpha(color[0] == Ui.chartColor(j) ? 1f : 0.45f);
                }
            });
            swatch.setAlpha(color[0] == value ? 1f : 0.45f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(context, 26), Ui.dp(context, 26)
            );
            params.setMargins(Ui.dp(context, 5), 0, Ui.dp(context, 5), 0);
            colorRow.addView(swatch, params);
            swatches.add(swatch);
        }

        body.addView(name);
        body.addView(classroom);
        body.addView(teacher);
        body.addView(note);
        body.addView(label(context, "上课星期"));
        body.addView(day);
        body.addView(label(context, "节次"));
        body.addView(periodRow);
        body.addView(label(context, "上课周次（点选）"));
        body.addView(weekGrid);
        body.addView(quickWeeks);
        body.addView(label(context, "课程颜色"));
        body.addView(colorRow);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增课程" : "编辑课程")
                .setView(wrapScroll(context, body))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError("请输入课程名称");
                        return;
                    }
                    if (weeks.isEmpty()) {
                        ((TextView) weekCells.get(0)).setError("至少选择一周");
                        return;
                    }
                    int startValue = start.getSelectedItemPosition() + 1;
                    int endValue = end.getSelectedItemPosition() + 1;
                    if (endValue < startValue) {
                        new AlertDialog.Builder(context)
                                .setMessage("结束节次不能早于开始节次")
                                .setPositiveButton("知道了", null)
                                .show();
                        return;
                    }
                    course.semesterId = semester.id;
                    course.name = value;
                    course.classroom = classroom.getText().toString().trim();
                    course.teacher = teacher.getText().toString().trim();
                    course.note = note.getText().toString().trim();
                    course.dayOfWeek = day.getSelectedItemPosition() + 1;
                    course.startPeriod = startValue;
                    course.endPeriod = endValue;
                    course.weeks = TimetableUtils.formatWeeks(weeks);
                    course.color = color[0];
                    if (course.createdAt == null || course.createdAt.isEmpty()) {
                        course.createdAt = DateUtils.nowText();
                    }
                    callback.onCourse(course);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void importForm(Context context, Semester semester, CoursesCallback callback) {
        LinearLayout body = Ui.dialogBody(context);
        TextView hint = Ui.caption(context,
                "每行示例：\n高等数学 周一 1-2节 1-16周 教学楼A101 张老师\n"
                        + "也支持用竖线或逗号分隔教室和老师。");
        hint.setPadding(0, 0, 0, Ui.dp(context, 8));
        EditText raw = Ui.multilineInput(context, "粘贴教务系统课表文字");
        raw.setMinLines(8);
        body.addView(hint);
        body.addView(raw);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("批量导入课表")
                .setView(wrapScroll(context, body))
                .setNegativeButton("取消", null)
                .setPositiveButton("预览", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    List<Course> courses = TimetableTextParser.parse(raw.getText().toString());
                    if (courses.isEmpty()) {
                        raw.setError("没有识别到课程，请检查每行是否包含周几、节次和周次");
                        return;
                    }
                    StringBuilder preview = new StringBuilder();
                    int shown = 0;
                    for (Course course : courses) {
                        if (shown++ >= 8) break;
                        preview.append(course.name).append(" · 周")
                                .append(course.dayOfWeek).append(" · ")
                                .append(course.startPeriod).append("-")
                                .append(course.endPeriod).append("节\n");
                    }
                    if (courses.size() > shown) {
                        preview.append("……共 ").append(courses.size()).append(" 门");
                    }
                    new AlertDialog.Builder(context)
                            .setTitle("识别到 " + courses.size() + " 门课程")
                            .setMessage(preview.toString())
                            .setNegativeButton("返回修改", null)
                            .setPositiveButton("确认导入", (d, w) -> {
                                for (Course course : courses) {
                                    course.semesterId = semester.id;
                                }
                                callback.onCourses(courses);
                                dialog.dismiss();
                            })
                            .show();
                }));
        dialog.show();
    }

    public static void adjustmentForm(Context context, Semester semester,
                                      Course course, LocalDate originalDate,
                                      AdjustmentCallback callback) {
        CourseAdjustment adjustment = new CourseAdjustment();
        adjustment.courseId = course.id;
        adjustment.originalDate = originalDate.format(DateUtils.ISO_DATE);
        adjustment.type = "reschedule";
        LinearLayout body = Ui.dialogBody(context);
        TextView original = Ui.caption(context,
                "原课程：" + course.name + " · 周" + course.dayOfWeek
                        + " · " + course.startPeriod + "-" + course.endPeriod + "节");
        original.setPadding(0, 0, 0, Ui.dp(context, 8));
        TextView date = dateField(context, originalDate.format(DateUtils.ISO_DATE));
        int periodCount = Math.max(1, semester.periodsPerDay);
        Spinner start = periodSpinner(context, periodCount, course.startPeriod);
        Spinner end = periodSpinner(context, periodCount, course.endPeriod);
        List<TimetableUtils.PeriodTime> times = TimetableUtils.parsePeriodTimes(semester);
        EditText note = Ui.input(context, "调课备注（可选）");
        body.addView(original);
        body.addView(label(context, "调整到日期"));
        body.addView(date);
        body.addView(label(context, "调整到节次"));
        body.addView(start);
        body.addView(end);
        body.addView(note);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("单节调课")
                .setView(wrapScroll(context, body))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int startPeriod = start.getSelectedItemPosition() + 1;
                    int endPeriod = end.getSelectedItemPosition() + 1;
                    if (endPeriod < startPeriod) {
                        new AlertDialog.Builder(context)
                                .setMessage("结束节次不能早于开始节次")
                                .setPositiveButton("知道了", null)
                                .show();
                        return;
                    }
                    adjustment.overrideDate = String.valueOf(date.getTag());
                    adjustment.overrideStartPeriod = startPeriod;
                    adjustment.overrideEndPeriod = endPeriod;
                    if (!times.isEmpty()) {
                        int safeStart = Math.min(times.size(), startPeriod) - 1;
                        int safeEnd = Math.min(times.size(), endPeriod) - 1;
                        adjustment.overrideStart = times.get(safeStart).start.toString();
                        adjustment.overrideEnd = times.get(safeEnd).end.toString();
                    }
                    adjustment.note = note.getText().toString().trim();
                    adjustment.createdAt = DateUtils.nowText();
                    callback.onAdjustment(adjustment);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static void markForm(Context context, LocalDate defaultDate, MarkCallback callback) {
        LinearLayout body = Ui.dialogBody(context);
        Spinner type = new Spinner(context);
        type.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"当天停课", "调休补课"}));
        TextView date = dateField(context, defaultDate.format(DateUtils.ISO_DATE));
        Spinner weekday = new Spinner(context);
        weekday.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"按周一", "按周二", "按周三", "按周四", "按周五", "按周六", "按周日"}));
        weekday.setEnabled(false);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       View view, int position, long id) {
                weekday.setEnabled(position == 1);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        EditText label = Ui.input(context, "说明，例如运动会、考试周、补周二课");
        body.addView(label(context, "调整类型"));
        body.addView(type);
        body.addView(label(context, "日期"));
        body.addView(date);
        body.addView(label(context, "补课按哪一天"));
        body.addView(weekday);
        body.addView(label(context, "说明"));
        body.addView(label);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("调休 / 停课")
                .setView(wrapScroll(context, body))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    CalendarMark mark = new CalendarMark();
                    mark.date = String.valueOf(date.getTag());
                    mark.type = type.getSelectedItemPosition() == 1 ? "makeup_day" : "rest_day";
                    mark.weekday = type.getSelectedItemPosition() == 1
                            ? weekday.getSelectedItemPosition() + 1 : 0;
                    mark.label = label.getText().toString().trim();
                    mark.createdAt = DateUtils.nowText();
                    callback.onMark(mark);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    public static CalendarMark stopWeekMark(LocalDate weekStart, String label) {
        CalendarMark mark = new CalendarMark();
        mark.date = weekStart.format(DateUtils.ISO_DATE);
        mark.type = "stop_week";
        mark.label = label;
        mark.createdAt = DateUtils.nowText();
        return mark;
    }

    private static void refreshWeekCells(Context context, List<TextView> cells,
                                         Set<Integer> selected) {
        for (int i = 0; i < cells.size(); i++) {
            boolean active = selected.contains(i + 1);
            TextView cell = cells.get(i);
            cell.setTextColor(active ? Color.WHITE : Ui.textColor(context));
            cell.setBackground(Ui.rounded(context,
                    active ? Ui.accent(context) : Ui.surfaceAlt(context), 7));
        }
    }

    private static Button weekQuickButton(Context context, String label, Runnable action) {
        Button button = Ui.button(context, label, false);
        button.setTextSize(11);
        button.setMinHeight(Ui.dp(context, 34));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(context, 36), 1
        );
        params.setMargins(Ui.dp(context, 2), Ui.dp(context, 5),
                Ui.dp(context, 2), Ui.dp(context, 2));
        button.setLayoutParams(params);
        return button;
    }

    private static Spinner periodSpinner(Context context, int max, int selected) {
        Spinner spinner = new Spinner(context);
        String[] values = new String[max];
        for (int i = 0; i < max; i++) values[i] = "第 " + (i + 1) + " 节";
        spinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(Math.max(0, Math.min(max - 1, selected - 1)));
        return spinner;
    }

    private static TextView dateField(Context context, String initial) {
        TextView view = Ui.text(context, DateUtils.friendlyDate(initial) + "  " + initial,
                13, Ui.textColor(context), false);
        view.setTag(initial);
        view.setPadding(Ui.dp(context, 12), Ui.dp(context, 12),
                Ui.dp(context, 12), Ui.dp(context, 12));
        view.setBackground(Ui.outlined(context, Ui.surfaceColor(context), Ui.border(context), 8));
        view.setOnClickListener(ignored -> Dialogs.pickDate(context,
                String.valueOf(view.getTag()), value -> {
                    view.setTag(value);
                    view.setText(DateUtils.friendlyDate(value) + "  " + value);
                }));
        return view;
    }

    private static TextView label(Context context, String text) {
        TextView view = Ui.caption(context, text);
        view.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6));
        return view;
    }

    private static View wrapScroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private static EditText numberInput(Context context, String hint, int value) {
        EditText input = Ui.input(context, hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        return input;
    }

    private static Semester copyScheduleSettings(Semester source,
                                                  EditText duration, EditText rest,
                                                  EditText morningStart, EditText morningCount,
                                                  EditText afternoonStart, EditText afternoonCount,
                                                  EditText eveningStart, EditText eveningCount) {
        Semester value = new Semester();
        value.totalWeeks = source.totalWeeks;
        value.classDurationMinutes = parseInt(duration.getText().toString(), 45);
        value.breakMinutes = parseInt(rest.getText().toString(), 10);
        value.morningStart = morningStart.getText().toString().trim();
        value.morningPeriods = parseInt(morningCount.getText().toString(), 2);
        value.afternoonStart = afternoonStart.getText().toString().trim();
        value.afternoonPeriods = parseInt(afternoonCount.getText().toString(), 2);
        value.eveningStart = eveningStart.getText().toString().trim();
        value.eveningPeriods = parseInt(eveningCount.getText().toString(), 1);
        value.periodsPerDay = value.morningPeriods + value.afternoonPeriods + value.eveningPeriods;
        return value;
    }

    private static boolean isTime(String value) {
        if (value == null || !value.trim().matches("\\d{2}:\\d{2}")) return false;
        try {
            LocalTime.parse(value.trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
