package com.localstudy.app.ui;

import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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

import com.localstudy.app.MainActivity;
import com.localstudy.app.R;
import com.localstudy.app.data.Chapter;
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.RepeatCompletion;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.data.StudyRepository;
import com.localstudy.app.data.Subject;
import com.localstudy.app.data.Todo;
import com.localstudy.app.service.TimerService;
import com.localstudy.app.service.TimerState;
import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.Dialogs;
import com.localstudy.app.util.RepeatTaskManager;
import com.localstudy.app.util.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TodayFragment extends Fragment implements Refreshable {
    private StudyRepository repository;
    private LinearLayout content;
    private TextView taskMetric;
    private TextView durationMetric;
    private TextView todoMetric;
    private LinearLayout timerBar;
    private TextView timerText;
    private Button pauseResume;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private Loaded loaded;
    private boolean loading;
    private boolean timerWasActive;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            updateTimerBar();
            updateTodayDuration();
            ticker.postDelayed(this, 1000);
        }
    };

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
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 16),
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 28)
        );
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refresh();
        return scroll;
    }

    @Override
    public void refresh() {
        if (!isAdded() || loading || repository == null) return;
        loading = true;
        repository.io(() -> {
            Loaded value = new Loaded();
            value.subjects = repository.dao().getAllSubjectsSync();
            value.chapters = repository.dao().getAllChaptersSync();
            value.sections = repository.dao().getAllSectionsSync();
            value.todos = repository.dao().getAllTodosSync();
            value.repeats = repository.dao().getAllRepeatTasksSync();
            value.records = repository.dao().getAllRecordsSync();
            value.completions = repository.dao().getAllRepeatCompletionsSync();
            return value;
        }, (value, error) -> {
            loading = false;
            if (!isAdded()) return;
            if (error != null) {
                Toast.makeText(requireContext(), "读取数据失败", Toast.LENGTH_SHORT).show();
                return;
            }
            loaded = value;
            render();
        });
    }

    private void render() {
        if (content == null || loaded == null) return;
        content.removeAllViews();

        LinearLayout heading = Ui.horizontal(requireContext());
        LinearLayout headingText = Ui.vertical(requireContext());
        TextView title = Ui.title(requireContext(), "今天");
        TextView date = Ui.caption(requireContext(), DateUtils.fullDateCn());
        date.setPadding(0, Ui.dp(requireContext(), 2), 0, 0);
        headingText.addView(title);
        headingText.addView(date);
        Ui.setWeight(headingText, 1);
        ImageButton add = Ui.iconButton(requireContext(), R.drawable.ic_add, "添加任务");
        add.setOnClickListener(view -> showAddTaskMenu());
        heading.addView(headingText);
        heading.addView(add);
        content.addView(heading);
        content.addView(Ui.space(requireContext(), 14));

        content.addView(buildSummary());
        content.addView(Ui.space(requireContext(), 10));

        timerBar = buildTimerBar();
        content.addView(timerBar);
        updateTimerBar();

        addTodoSection();
        addTasksSection();
        addRepeatFooter();
        updateTodayDuration();
    }

    private View buildSummary() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        LinearLayout first = Ui.vertical(requireContext());
        todoMetric = Ui.text(requireContext(), "0", 20, Ui.textColor(requireContext()), true);
        TextView firstLabel = Ui.caption(requireContext(), "待办");
        first.addView(todoMetric);
        first.addView(firstLabel);
        Ui.setWeight(first, 1);

        LinearLayout second = Ui.vertical(requireContext());
        taskMetric = Ui.text(requireContext(), "0 / 0", 20, Ui.textColor(requireContext()), true);
        TextView secondLabel = Ui.caption(requireContext(), "任务完成");
        second.addView(taskMetric);
        second.addView(secondLabel);
        Ui.setWeight(second, 1);

        LinearLayout third = Ui.vertical(requireContext());
        durationMetric = Ui.text(requireContext(), "0分钟", 20, Ui.accent(requireContext()), true);
        TextView thirdLabel = Ui.caption(requireContext(), "今日学习");
        third.addView(durationMetric);
        third.addView(thirdLabel);
        Ui.setWeight(third, 1);

        row.addView(first);
        row.addView(second);
        row.addView(third);
        card.addView(row);
        return card;
    }

    private LinearLayout buildTimerBar() {
        LinearLayout bar = Ui.horizontal(requireContext());
        bar.setPadding(
                Ui.dp(requireContext(), 13), Ui.dp(requireContext(), 10),
                Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 10)
        );
        bar.setBackground(Ui.rounded(
                requireContext(), Ui.withAlpha(Ui.accent(requireContext()), 22), 8
        ));
        timerText = Ui.text(requireContext(), "", 13, Ui.textColor(requireContext()), true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
        );
        bar.addView(timerText, textParams);
        pauseResume = Ui.button(requireContext(), "暂停", false);
        pauseResume.setOnClickListener(view -> {
            TimerState.Snapshot state = TimerState.read(requireContext());
            TimerService.sendAction(requireContext(),
                    state.running ? TimerService.ACTION_PAUSE : TimerService.ACTION_RESUME);
            ticker.postDelayed(() -> updateTimerBar(), 100);
        });
        Button stop = Ui.button(requireContext(), "结束", true);
        stop.setOnClickListener(view -> confirmStopTimer());
        bar.addView(pauseResume);
        bar.addView(stop);
        return bar;
    }

    private void updateTimerBar() {
        if (timerBar == null || !isAdded()) return;
        TimerState.Snapshot state = TimerState.read(requireContext());
        if (!state.active) {
            timerBar.setVisibility(View.GONE);
            if (timerWasActive) {
                timerWasActive = false;
                ticker.postDelayed(this::refresh, 350);
            }
            return;
        }
        timerWasActive = true;
        timerBar.setVisibility(View.VISIBLE);
        String shown = "countdown".equals(state.mode)
                ? "剩余 " + DateUtils.duration(state.remainingSec())
                : DateUtils.duration(state.elapsedSec);
        String title = state.title == null || state.title.isEmpty() ? state.subjectName : state.title;
        timerText.setText(title + "\n" + state.subjectName + " · " + shown
                + (state.running ? "" : " · 已暂停"));
        pauseResume.setText(state.running ? "暂停" : "继续");
    }

    private void updateTodayDuration() {
        if (loaded == null || durationMetric == null || !isAdded()) return;
        LocalDate today = LocalDate.now();
        long total = DateUtils.recordSecondsOn(loaded.records, today);
        TimerState.Snapshot timer = TimerState.read(requireContext());
        if (timer.active && timer.elapsedSec > 0) {
            LocalDate startDay = java.time.Instant.ofEpochMilli(timer.startedAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            if (startDay.equals(today)) {
                total += timer.elapsedSec;
            } else if (startDay.isBefore(today)) {
                long secondsToday = LocalDateTime.now().toLocalTime().toSecondOfDay();
                total += Math.min(timer.elapsedSec, secondsToday);
            }
        }
        durationMetric.setText(DateUtils.durationHuman(total));
    }

    private void addTodoSection() {
        LinearLayout header = sectionHeader("待办", "新增", () ->
                Dialogs.todoForm(requireContext(), null, loaded.subjects, todo -> {
                    repository.io(() -> repository.dao().insertTodo(todo), () -> refresh());
                }));
        content.addView(header);
        content.addView(Ui.space(requireContext(), 8));

        String today = DateUtils.today();
        List<Todo> unfinished = new ArrayList<>();
        List<Todo> finished = new ArrayList<>();
        for (Todo todo : loaded.todos) {
            if (todo.isFinished && DateUtils.isValidDate(todo.ddl)
                    && todo.ddl.compareTo(today) < 0) {
                continue;
            }
            (todo.isFinished ? finished : unfinished).add(todo);
        }
        unfinished.sort((a, b) -> {
            boolean aOverdue = DateUtils.isValidDate(a.ddl) && a.ddl.compareTo(today) < 0;
            boolean bOverdue = DateUtils.isValidDate(b.ddl) && b.ddl.compareTo(today) < 0;
            if (aOverdue != bOverdue) return aOverdue ? -1 : 1;
            return safe(a.ddl).compareTo(safe(b.ddl));
        });

        int openCount = unfinished.size();
        todoMetric.setText(String.valueOf(openCount));
        if (unfinished.isEmpty() && finished.isEmpty()) {
            content.addView(emptyLine("还没有待办，先给一个明确截止点。"));
        }
        int shown = 0;
        for (Todo todo : unfinished) {
            if (shown >= 5) break;
            content.addView(todoRow(todo));
            content.addView(Ui.space(requireContext(), 7));
            shown++;
        }
        if (!finished.isEmpty()) {
            TextView folded = Ui.caption(requireContext(), "已完成的待办 · " + finished.size());
            folded.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 6));
            content.addView(folded);
            for (Todo todo : finished) {
                content.addView(todoRow(todo));
                content.addView(Ui.space(requireContext(), 6));
            }
        }
        content.addView(Ui.space(requireContext(), 12));
    }

    private View todoRow(Todo todo) {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        AnimatedCheckView check = new AnimatedCheckView(requireContext());
        check.setChecked(todo.isFinished);

        LinearLayout text = Ui.vertical(requireContext());
        String overdueText = "";
        int titleColor = Ui.textColor(requireContext());
        boolean overdue = !todo.isFinished && DateUtils.isValidDate(todo.ddl)
                && todo.ddl.compareTo(DateUtils.today()) < 0;
        if (overdue) {
            long days = DateUtils.daysBetween(todo.ddl, DateUtils.today());
            overdueText = " · 已逾期" + Math.max(1, days) + "天";
            titleColor = Ui.danger(requireContext());
        } else if (!todo.isFinished && DateUtils.today().equals(todo.ddl)) {
            titleColor = Ui.accent(requireContext());
        }
        final int activeTitleColor = titleColor;
        TextView title = Ui.text(requireContext(), todo.title, 14, activeTitleColor, true);
        if (todo.isFinished) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(Ui.muted(requireContext()));
        }
        check.setListener(checked -> {
            todo.isFinished = checked;
            applyDoneStyle(title, checked, activeTitleColor);
            repository.io(() -> repository.dao().updateTodo(todo), this::refreshAfterAnimation);
        });
        TextView meta = Ui.caption(requireContext(),
                "截止 " + DateUtils.friendlyDate(todo.ddl) + overdueText
                        + subjectSuffix(todo.subjectId));
        text.addView(title);
        text.addView(meta);
        if (todo.note != null && !todo.note.isEmpty()) {
            TextView note = Ui.caption(requireContext(), todo.note);
            note.setPadding(0, Ui.dp(requireContext(), 3), 0, 0);
            text.addView(note);
        }
        Ui.setWeight(text, 1);
        row.addView(check);
        row.addView(text);
        card.addView(row);
        card.setOnLongClickListener(view -> {
            Dialogs.todoForm(requireContext(), todo, loaded.subjects, value ->
                    repository.io(() -> repository.dao().updateTodo(value), () -> refresh()));
            return true;
        });
        SwipeActionView swipe = new SwipeActionView(requireContext());
        swipe.setContent(card);
        List<SwipeActionView.Action> todoActions = new ArrayList<>();
        if (!todo.isFinished) {
            todoActions.add(new SwipeActionView.Action("开始", Ui.accent(requireContext()),
                    () -> startTodoTimer(todo)));
        }
        todoActions.add(new SwipeActionView.Action(todo.isFinished ? "恢复" : "完成",
                Ui.success(requireContext()), () -> {
                    todo.isFinished = !todo.isFinished;
                    applyDoneStyle(title, todo.isFinished);
                    repository.io(() -> repository.dao().updateTodo(todo),
                            this::refreshAfterAnimation);
                }));
        todoActions.add(new SwipeActionView.Action("删除", Ui.danger(requireContext()),
                () -> confirmDelete("删除待办？", () ->
                        repository.io(() -> repository.dao().deleteTodo(todo), () -> refresh()))));
        swipe.setActions(todoActions);
        return swipe;
    }

    private void addTasksSection() {
        LinearLayout header = sectionHeader("今日任务", "添加", this::showAddTaskMenu);
        content.addView(header);
        content.addView(Ui.space(requireContext(), 8));

        String today = DateUtils.today();
        List<Section> debts = new ArrayList<>();
        List<Section> current = new ArrayList<>();
        List<Section> completed = new ArrayList<>();
        for (Section section : loaded.sections) {
            if (section.isSkipped) continue;
            if (section.isFinished && today.equals(section.finishDate)) {
                completed.add(section);
            } else if (!section.isFinished && section.planDate != null
                    && section.planDate.compareTo(today) < 0) {
                debts.add(section);
            } else if (!section.isFinished && today.equals(section.planDate)) {
                current.add(section);
            }
        }
        List<RepeatTask> repeats = new ArrayList<>();
        for (RepeatTask task : loaded.repeats) {
            if (DateUtils.repeatOccursOn(task, LocalDate.now())) {
                repeats.add(task);
            }
        }
        repeats.sort(Comparator.comparing(task ->
                repeatDone(task, today) ? 1 : 0));
        debts.sort(Comparator.comparing(section -> safe(section.planDate)));

        int total = debts.size() + current.size() + completed.size() + repeats.size();
        int done = completed.size();
        for (RepeatTask task : repeats) {
            if (today.equals(task.lastCompletedDate)) done++;
        }
        taskMetric.setText(done + " / " + total);

        if (debts.size() > 3) {
            Button skip = Ui.button(requireContext(),
                    "一键跳过过去 " + debts.size() + " 个逾期任务", false);
            skip.setTextColor(Ui.danger(requireContext()));
            skip.setOnClickListener(view -> confirmSkipDebts(debts.size()));
            content.addView(skip);
            content.addView(Ui.space(requireContext(), 8));
        }

        if (total == 0) {
            LinearLayout empty = Ui.surface(requireContext());
            TextView text = Ui.text(requireContext(),
                    "今天没有安排任务，去科目目录排课吧", 14,
                    Ui.textColor(requireContext()), true);
            text.setGravity(Gravity.CENTER);
            TextView action = Ui.text(requireContext(), "打开科目目录", 12,
                    Ui.accent(requireContext()), true);
            action.setGravity(Gravity.CENTER);
            action.setPadding(0, Ui.dp(requireContext(), 10), 0, Ui.dp(requireContext(), 2));
            action.setOnClickListener(view -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTabByIndex(2);
                }
            });
            empty.addView(text);
            empty.addView(action);
            content.addView(empty);
        }

        for (Section section : debts) {
            content.addView(sectionRow(section, true));
            content.addView(Ui.space(requireContext(), 7));
        }
        for (Section section : current) {
            content.addView(sectionRow(section, false));
            content.addView(Ui.space(requireContext(), 7));
        }
        for (RepeatTask task : repeats) {
            content.addView(repeatRow(task));
            content.addView(Ui.space(requireContext(), 7));
        }
        for (Section section : completed) {
            content.addView(sectionRow(section, false));
            content.addView(Ui.space(requireContext(), 7));
        }
    }

    private View sectionRow(Section section, boolean debt) {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        AnimatedCheckView check = new AnimatedCheckView(requireContext());
        check.setChecked(section.isFinished);

        LinearLayout text = Ui.vertical(requireContext());
        int titleColor = section.isFinished ? Ui.muted(requireContext())
                : debt ? Ui.danger(requireContext()) : Ui.textColor(requireContext());
        TextView title = Ui.text(requireContext(), section.title, 14, titleColor, true);
        if (section.isFinished) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        check.setListener(checked -> {
            section.isFinished = checked;
            section.finishDate = checked ? DateUtils.today() : "";
            applyDoneStyle(title, checked, debt
                    ? Ui.danger(requireContext()) : Ui.textColor(requireContext()));
            repository.io(() -> repository.dao().updateSection(section),
                    this::refreshAfterAnimation);
        });
        String meta = DateUtils.friendlyDate(section.planDate) + " · " + section.planDate
                + subjectSuffix(section.subjectId);
        if (debt) {
            long days = DateUtils.daysBetween(section.planDate, DateUtils.today());
            meta += " · 欠债" + Math.max(1, days) + "天";
        }
        TextView detail = Ui.caption(requireContext(), meta);
        text.addView(title);
        text.addView(detail);
        if (section.note != null && !section.note.isEmpty()) {
            text.addView(Ui.caption(requireContext(), section.note));
        }
        Ui.setWeight(text, 1);
        row.addView(check);
        row.addView(text);
        card.addView(row);
        card.setOnLongClickListener(view -> {
            editSection(section);
            return true;
        });
        SwipeActionView swipe = new SwipeActionView(requireContext());
        swipe.setContent(card);
        List<SwipeActionView.Action> sectionActions = new ArrayList<>();
        if (!section.isFinished) {
            sectionActions.add(new SwipeActionView.Action("开始", Ui.accent(requireContext()),
                    () -> startSectionTimer(section)));
        }
        sectionActions.add(new SwipeActionView.Action(section.isFinished ? "恢复" : "完成",
                Ui.success(requireContext()), () -> {
                    section.isFinished = !section.isFinished;
                    section.finishDate = section.isFinished ? DateUtils.today() : "";
                    applyDoneStyle(title, section.isFinished, debt
                            ? Ui.danger(requireContext()) : Ui.textColor(requireContext()));
                    repository.io(() -> repository.dao().updateSection(section),
                            this::refreshAfterAnimation);
                }));
        sectionActions.add(new SwipeActionView.Action("删除", Ui.danger(requireContext()),
                () -> confirmDelete("删除这条任务？", () ->
                        repository.io(() -> repository.dao().deleteSection(section), () -> refresh()))));
        swipe.setActions(sectionActions);
        return swipe;
    }

    private View repeatRow(RepeatTask task) {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        String today = DateUtils.today();
        boolean done = repeatDone(task, today);
        AnimatedCheckView check = new AnimatedCheckView(requireContext());
        check.setChecked(done);
        LinearLayout text = Ui.vertical(requireContext());
        TextView title = Ui.text(requireContext(), task.title, 14,
                done ? Ui.muted(requireContext()) : Ui.textColor(requireContext()), true);
        if (done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        check.setListener(checked -> {
            task.lastCompletedDate = checked ? today : "";
            applyDoneStyle(title, checked, Ui.textColor(requireContext()));
            repository.io(() -> {
                repository.dao().updateRepeatTask(task);
                if (checked) {
                    RepeatCompletion completion = new RepeatCompletion();
                    completion.repeatTaskId = task.id;
                    completion.date = today;
                    completion.completedAt = DateUtils.nowText();
                    repository.dao().insertRepeatCompletion(completion);
                } else {
                    repository.dao().deleteRepeatCompletion(task.id, today);
                }
            }, this::refreshAfterAnimation);
        });
        text.addView(title);
        text.addView(Ui.caption(requireContext(),
                repeatLabel(task) + subjectSuffix(task.subjectId)));
        Ui.setWeight(text, 1);
        row.addView(check);
        row.addView(text);
        card.addView(row);
        card.setOnLongClickListener(view -> {
            Dialogs.repeatForm(requireContext(), task, loaded.subjects, value ->
                    repository.io(() -> repository.dao().updateRepeatTask(value), () -> refresh()));
            return true;
        });
        SwipeActionView swipe = new SwipeActionView(requireContext());
        swipe.setContent(card);
        List<SwipeActionView.Action> repeatActions = new ArrayList<>();
        if (!taskDone(task)) {
            repeatActions.add(new SwipeActionView.Action("开始", Ui.accent(requireContext()),
                    () -> startRepeatTimer(task)));
        }
        repeatActions.add(new SwipeActionView.Action(taskDone(task) ? "恢复" : "完成",
                Ui.success(requireContext()), () -> {
                    boolean target = !taskDone(task);
                    task.lastCompletedDate = target ? today : "";
                    applyDoneStyle(title, target, Ui.textColor(requireContext()));
                    repository.io(() -> {
                        repository.dao().updateRepeatTask(task);
                        if (target) {
                            RepeatCompletion completion = new RepeatCompletion();
                            completion.repeatTaskId = task.id;
                            completion.date = today;
                            completion.completedAt = DateUtils.nowText();
                            repository.dao().insertRepeatCompletion(completion);
                        } else {
                            repository.dao().deleteRepeatCompletion(task.id, today);
                        }
                    }, this::refreshAfterAnimation);
                }));
        repeatActions.add(new SwipeActionView.Action("删除", Ui.danger(requireContext()),
                () -> confirmDelete("删除这个重复任务？", () ->
                        repository.io(() -> repository.dao().deleteRepeatTask(task), () -> refresh()))));
        swipe.setActions(repeatActions);
        return swipe;
    }

    private void addRepeatFooter() {
        content.addView(Ui.space(requireContext(), 12));
        Button manage = Ui.button(requireContext(), "管理重复任务", false);
        manage.setOnClickListener(view -> showRepeatManager());
        content.addView(manage);
    }

    private void showAddTaskMenu() {
        if (loaded == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("添加任务")
                .setItems(new String[]{"从科目目录选择", "手动添加", "新增重复任务"}, (dialog, which) -> {
                    if (which == 0) pickCatalogSection();
                    else if (which == 1) {
                        Dialogs.sectionForm(requireContext(), null, loaded.subjects, null, section ->
                                repository.io(() -> repository.dao().insertSection(section), () -> refresh()));
                    } else {
                        Dialogs.repeatForm(requireContext(), null, loaded.subjects, task ->
                                repository.io(() -> repository.dao().insertRepeatTask(task), () -> refresh()));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickCatalogSection() {
        if (loaded.subjects.isEmpty()) {
            Toast.makeText(requireContext(), "请先在科目目录新增科目", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[loaded.subjects.size()];
        for (int i = 0; i < names.length; i++) names[i] = loaded.subjects.get(i).name;
        new AlertDialog.Builder(requireContext())
                .setTitle("选择科目")
                .setItems(names, (dialog, which) -> {
                    Subject subject = loaded.subjects.get(which);
                    List<Chapter> chapters = chaptersOf(subject.id);
                    if (chapters.isEmpty()) {
                        Toast.makeText(requireContext(), "该科目还没有章节", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] chapterNames = new String[chapters.size()];
                    for (int i = 0; i < chapterNames.length; i++) {
                        chapterNames[i] = chapters.get(i).title;
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(subject.name + " · 选择章节")
                            .setItems(chapterNames, (d, chapterIndex) ->
                                    pickSectionFromChapter(chapters.get(chapterIndex)))
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickSectionFromChapter(Chapter chapter) {
        List<Section> sections = new ArrayList<>();
        for (Section section : loaded.sections) {
            if (section.chapterId != null && section.chapterId == chapter.id) {
                sections.add(section);
            }
        }
        if (sections.isEmpty()) {
            Toast.makeText(requireContext(), "本章还没有小节", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[sections.size()];
        for (int i = 0; i < names.length; i++) names[i] = sections.get(i).title;
        new AlertDialog.Builder(requireContext())
                .setTitle(chapter.title)
                .setItems(names, (dialog, which) -> {
                    Section selected = sections.get(which);
                    Dialogs.pickDate(requireContext(), selected.planDate, date -> {
                        selected.planDate = date;
                        selected.isSkipped = false;
                        selected.isFinished = false;
                        selected.finishDate = "";
                        repository.io(() -> repository.dao().updateSection(selected), () -> refresh());
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTodoMenu(View anchor, Todo todo) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑");
        menu.getMenu().add(todo.isFinished ? "恢复为未完成" : "标记完成");
        menu.getMenu().add("删除");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("编辑".equals(title)) {
                Dialogs.todoForm(requireContext(), todo, loaded.subjects, value ->
                        repository.io(() -> repository.dao().updateTodo(value), () -> refresh()));
            } else if ("删除".equals(title)) {
                confirmDelete("删除待办？", () ->
                        repository.io(() -> repository.dao().deleteTodo(todo), () -> refresh()));
            } else {
                todo.isFinished = !todo.isFinished;
                repository.io(() -> repository.dao().updateTodo(todo), () -> refresh());
            }
            return true;
        });
        menu.show();
    }

    private void showSectionMenu(View anchor, Section section) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑 / 改日期");
        menu.getMenu().add("延后到明天");
        menu.getMenu().add(section.isFinished ? "恢复为未完成" : "标记完成");
        menu.getMenu().add("删除");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (title.startsWith("编辑")) {
                editSection(section);
            } else if (title.startsWith("延后")) {
                section.planDate = LocalDate.now().plusDays(1).format(DateUtils.ISO_DATE);
                section.isSkipped = false;
                repository.io(() -> repository.dao().updateSection(section), () -> refresh());
            } else if (title.equals("删除")) {
                confirmDelete("删除这条任务？", () ->
                        repository.io(() -> repository.dao().deleteSection(section), () -> refresh()));
            } else {
                section.isFinished = !section.isFinished;
                section.finishDate = section.isFinished ? DateUtils.today() : "";
                repository.io(() -> repository.dao().updateSection(section), () -> refresh());
            }
            return true;
        });
        menu.show();
    }

    private void showRepeatMenu(View anchor, RepeatTask task) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑重复规则");
        menu.getMenu().add("删除重复任务");
        menu.setOnMenuItemClickListener(item -> {
            if (String.valueOf(item.getTitle()).startsWith("编辑")) {
                Dialogs.repeatForm(requireContext(), task, loaded.subjects, value ->
                        repository.io(() -> repository.dao().updateRepeatTask(value), () -> refresh()));
            } else {
                confirmDelete("删除这个重复任务？", () ->
                        repository.io(() -> repository.dao().deleteRepeatTask(task), () -> refresh()));
            }
            return true;
        });
        menu.show();
    }

    private void showRepeatManager() {
        RepeatTaskManager.show(requireContext(), loaded.repeats, loaded.subjects,
                new RepeatTaskManager.Actions() {
                    @Override
                    public void onAdd() {
                        Dialogs.repeatForm(requireContext(), null, loaded.subjects, task ->
                                repository.io(() -> repository.dao().insertRepeatTask(task),
                                        TodayFragment.this::refresh));
                    }

                    @Override
                    public void onEdit(RepeatTask task) {
                        Dialogs.repeatForm(requireContext(), task, loaded.subjects, value ->
                                repository.io(() -> repository.dao().updateRepeatTask(value),
                                        TodayFragment.this::refresh));
                    }

                    @Override
                    public void onDelete(RepeatTask task) {
                        confirmDelete("删除这个重复任务？", () ->
                                repository.io(() -> repository.dao().deleteRepeatTask(task),
                                        TodayFragment.this::refresh));
                    }
                });
    }

    private void editSection(Section section) {
        long oldChapter = section.chapterId == null ? -1 : section.chapterId;
        Dialogs.sectionForm(requireContext(), section, loaded.subjects, null, value -> {
            value.chapterId = oldChapter < 0 ? null : oldChapter;
            repository.io(() -> repository.dao().updateSection(value), () -> refresh());
        });
    }

    private void confirmSkipDebts(int count) {
        new AlertDialog.Builder(requireContext())
                .setTitle("跳过逾期任务")
                .setMessage("将过去未完成的 " + count + " 个单次任务标记为已跳过。历史学习记录不会受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("跳过", (dialog, which) ->
                        repository.io(() -> repository.dao().skipAllDebts(DateUtils.today()),
                                () -> refresh()))
                .show();
    }

    private void startTodoTimer(Todo todo) {
        Subject subject = findSubject(todo.subjectId);
        Dialogs.timerRule(requireContext(), todo.title,
                subject == null ? "其他" : subject.name,
                subject == null ? null : subject.id,
                null, todo.id, null,
                subject == null ? "positive" : subject.timerMode,
                subject == null ? 25 : subject.timerDuration);
    }

    private void startSectionTimer(Section section) {
        Subject subject = findSubject(section.subjectId);
        Dialogs.timerRule(requireContext(), section.title,
                subject == null ? "其他" : subject.name,
                subject == null ? null : subject.id,
                section.id, null, null,
                subject == null ? "positive" : subject.timerMode,
                subject == null ? 25 : subject.timerDuration);
    }

    private void startRepeatTimer(RepeatTask task) {
        Subject subject = findSubject(task.subjectId);
        Dialogs.timerRule(requireContext(), task.title,
                subject == null ? "其他" : subject.name,
                subject == null ? null : subject.id,
                null, null, task.id,
                subject == null ? "positive" : subject.timerMode,
                subject == null ? 25 : subject.timerDuration);
    }

    private void confirmStopTimer() {
        new AlertDialog.Builder(requireContext())
                .setTitle("结束本次计时？")
                .setMessage("计时会保存为学习记录，任务完成状态需要手动勾选。")
                .setNegativeButton("继续", null)
                .setPositiveButton("结束", (dialog, which) ->
                        TimerService.sendAction(requireContext(), TimerService.ACTION_STOP))
                .show();
    }

    private LinearLayout sectionHeader(String title, String action, Runnable callback) {
        LinearLayout row = Ui.horizontal(requireContext());
        TextView label = Ui.sectionTitle(requireContext(), title);
        Ui.setWeight(label, 1);
        TextView actionView = Ui.text(requireContext(), action, 12,
                Ui.accent(requireContext()), true);
        actionView.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 6),
                Ui.dp(requireContext(), 2), Ui.dp(requireContext(), 6));
        actionView.setOnClickListener(view -> callback.run());
        row.addView(label);
        row.addView(actionView);
        return row;
    }

    private TextView compactAction(String label) {
        TextView action = Ui.text(requireContext(), label, 11,
                Ui.accent(requireContext()), true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(Ui.dp(requireContext(), 7), Ui.dp(requireContext(), 7),
                Ui.dp(requireContext(), 7), Ui.dp(requireContext(), 7));
        action.setBackground(Ui.rounded(requireContext(),
                Ui.withAlpha(Ui.accent(requireContext()), 18), 7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = Ui.dp(requireContext(), 5);
        params.gravity = Gravity.CENTER_VERTICAL;
        action.setLayoutParams(params);
        return action;
    }

    private TextView emptyLine(String text) {
        TextView view = Ui.caption(requireContext(), text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, Ui.dp(requireContext(), 18), 0, Ui.dp(requireContext(), 18));
        view.setBackground(Ui.rounded(requireContext(), Ui.surfaceAlt(requireContext()), 8));
        return view;
    }

    private void confirmDelete(String message, Runnable action) {
        new AlertDialog.Builder(requireContext())
                .setTitle(message)
                .setMessage("此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> action.run())
                .show();
    }

    private List<Chapter> chaptersOf(long subjectId) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter chapter : loaded.chapters) {
            if (chapter.subjectId == subjectId) result.add(chapter);
        }
        return result;
    }

    private Subject findSubject(Long id) {
        if (id == null) return null;
        for (Subject subject : loaded.subjects) {
            if (subject.id == id) return subject;
        }
        return null;
    }

    private String subjectSuffix(Long subjectId) {
        Subject subject = findSubject(subjectId);
        return subject == null ? " · 其他" : " · " + subject.name;
    }

    private String repeatLabel(RepeatTask task) {
        switch (task.repeatType) {
            case "weekly":
                return "每周 " + task.repeatValue.replace("1", "一").replace("2", "二")
                        .replace("3", "三").replace("4", "四").replace("5", "五")
                        .replace("6", "六").replace("7", "日");
            case "monthly":
                return "每月 " + task.repeatValue + " 日";
            default:
                return "每天";
        }
    }

    private boolean repeatDone(RepeatTask task, String date) {
        if (date.equals(task.lastCompletedDate)) return true;
        for (RepeatCompletion completion : loaded.completions) {
            if (completion.repeatTaskId == task.id && date.equals(completion.date)) {
                return true;
            }
        }
        return false;
    }

    private boolean taskDone(RepeatTask task) {
        return repeatDone(task, DateUtils.today());
    }

    private void applyDoneStyle(TextView title, boolean done) {
        applyDoneStyle(title, done, Ui.textColor(requireContext()));
    }

    private void applyDoneStyle(TextView title, boolean done, int activeColor) {
        if (done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(Ui.muted(requireContext()));
        } else {
            title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(activeColor);
        }
    }

    private void refreshAfterAnimation() {
        ticker.postDelayed(this::refresh, 460);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.removeCallbacks(timerTick);
        ticker.post(timerTick);
        if (isAdded()) refresh();
    }

    @Override
    public void onPause() {
        ticker.removeCallbacks(timerTick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        ticker.removeCallbacks(timerTick);
        content = null;
        timerBar = null;
        super.onDestroyView();
    }

    private static class Loaded {
        List<Subject> subjects = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        List<Todo> todos = new ArrayList<>();
        List<RepeatTask> repeats = new ArrayList<>();
        List<RepeatCompletion> completions = new ArrayList<>();
        List<StudyRecord> records = new ArrayList<>();
    }

    private static class DialogHolder {
        static DialogHolder instance;
        final AlertDialog dialog;

        DialogHolder(AlertDialog dialog) {
            this.dialog = dialog;
        }
    }
}
