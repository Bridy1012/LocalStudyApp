package com.localstudy.app.ui;

import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.localstudy.app.data.Chapter;
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.data.StudyRepository;
import com.localstudy.app.data.Subject;
import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.Dialogs;
import com.localstudy.app.util.DirectoryParser;
import com.localstudy.app.util.RepeatTaskManager;
import com.localstudy.app.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectoryFragment extends Fragment implements Refreshable {
    private static final int COLOR_TODO = 0xFFD07A30;
    private StudyRepository repository;
    private LinearLayout content;
    private ScrollView scrollView;
    private LinearLayout batchBar;
    private TextView batchCountText;
    private final Set<Long> expanded = new HashSet<>();
    private final Set<Long> expandedChapters = new HashSet<>();
    private final Set<Long> selectedSectionIds = new HashSet<>();
    private long batchSubjectId = -1;
    private Loaded loaded;
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        repository = StudyRepository.get(requireContext());
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(Ui.bg(requireContext()));
        scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(Ui.bg(requireContext()));
        content = Ui.vertical(requireContext());
        content.setPadding(
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 16),
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 28)
        );
        scrollView.addView(content);
        root.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        batchBar = buildBatchBar();
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barParams.gravity = Gravity.BOTTOM;
        root.addView(batchBar, barParams);
        refresh();
        return root;
    }

    @Override
    public void refresh() {
        if (!isAdded() || loading || repository == null) return;
        loading = true;
        repository.io(() -> {
            Loaded data = new Loaded();
            data.subjects = repository.dao().getAllSubjectsSync();
            data.chapters = repository.dao().getAllChaptersSync();
            data.sections = repository.dao().getAllSectionsSync();
            data.records = repository.dao().getAllRecordsSync();
            data.repeats = repository.dao().getAllRepeatTasksSync();
            return data;
        }, (value, error) -> {
            loading = false;
            if (!isAdded()) return;
            if (error != null) {
                Toast.makeText(requireContext(), "读取目录失败", Toast.LENGTH_SHORT).show();
                return;
            }
            loaded = value;
            render();
        });
    }

    private void render() {
        if (content == null || loaded == null) return;
        content.removeAllViews();

        LinearLayout header = Ui.horizontal(requireContext());
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.title(requireContext(), "科目目录"));
        text.addView(Ui.caption(requireContext(),
                loaded.subjects.size() + " 个科目 · " + loaded.sections.size() + " 个小节"));
        Ui.setWeight(text, 1);
        Button importButton = Ui.button(requireContext(), "导入目录", false);
        importButton.setOnClickListener(view -> showImportDialog());
        ImageButton add = Ui.iconButton(requireContext(), com.localstudy.app.R.drawable.ic_add, "新增科目");
        add.setOnClickListener(view -> addSubject());
        header.addView(text);
        header.addView(importButton);
        header.addView(add);
        content.addView(header);
        content.addView(Ui.space(requireContext(), 16));

        if (loaded.subjects.isEmpty()) {
            LinearLayout empty = Ui.surface(requireContext());
            TextView title = Ui.text(requireContext(), "从一门课开始", 18,
                    Ui.textColor(requireContext()), true);
            TextView body = Ui.caption(requireContext(),
                    "新增科目后，可以手动建立章节，也可以粘贴学科目录自动分层。");
            body.setPadding(0, Ui.dp(requireContext(), 6), 0, Ui.dp(requireContext(), 12));
            Button create = Ui.button(requireContext(), "新增科目", true);
            create.setOnClickListener(view -> addSubject());
            empty.addView(title);
            empty.addView(body);
            empty.addView(create);
            content.addView(empty);
        } else {
            for (Subject subject : loaded.subjects) {
                content.addView(subjectCard(subject));
                content.addView(Ui.space(requireContext(), 10));
            }
        }

        content.addView(Ui.space(requireContext(), 8));
        Button repeat = Ui.button(requireContext(), "管理重复任务 · " + loaded.repeats.size(), false);
        repeat.setOnClickListener(view -> showRepeatManager());
        content.addView(repeat);
        updateBatchBar();
    }

    private View subjectCard(Subject subject) {
        LinearLayout card = Ui.surface(requireContext());
        card.setPadding(
                Ui.dp(requireContext(), 15), Ui.dp(requireContext(), 13),
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 13)
        );
        LinearLayout mainRow = Ui.horizontal(requireContext());
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(), subject.name, 17,
                Ui.textColor(requireContext()), true));
        long seconds = subjectSeconds(subject.id);
        List<Section> sections = sectionsOf(subject.id);
        int finished = 0;
        for (Section section : sections) if (section.isFinished) finished++;
        TextView meta = Ui.caption(requireContext(),
                "累计 " + DateUtils.durationHuman(seconds)
                        + (sections.isEmpty() ? " · 尚未建立目录"
                        : " · 完成 " + finished + "/" + sections.size()));
        meta.setPadding(0, Ui.dp(requireContext(), 3), 0, 0);
        text.addView(meta);
        Ui.setWeight(text, 1);
        Button start = Ui.button(requireContext(), "开始", true);
        start.setOnClickListener(view -> startSubjectTimer(subject));
        TextView expand = Ui.text(requireContext(),
                expanded.contains(subject.id) ? "收起" : "展开", 12,
                Ui.accent(requireContext()), true);
        expand.setGravity(Gravity.CENTER);
        expand.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 8),
                Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 8));
        expand.setOnClickListener(view -> {
            if (!expanded.remove(subject.id)) expanded.add(subject.id);
            if (!expanded.contains(subject.id) && batchSubjectId == subject.id) {
                batchSubjectId = -1;
                selectedSectionIds.clear();
            }
            render();
        });
        mainRow.addView(text);
        RingProgressView ring = new RingProgressView(requireContext());
        ring.setProgress(finished, sections.size());
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 50), Ui.dp(requireContext(), 50)
        );
        ringParams.rightMargin = Ui.dp(requireContext(), 8);
        mainRow.addView(ring, ringParams);
        mainRow.addView(start);
        mainRow.addView(expand);
        card.addView(mainRow);

        if (!sections.isEmpty()) {
            ProgressBar progress = Ui.progress(requireContext(),
                    sections.size(), finished, Ui.accent(requireContext()));
            progress.setPadding(0, Ui.dp(requireContext(), 8), 0, 0);
            card.addView(progress);
        }

        card.setOnLongClickListener(view -> {
            editSubject(subject);
            return true;
        });

        if (expanded.contains(subject.id)) {
            View divider = new View(requireContext());
            divider.setBackgroundColor(Ui.border(requireContext()));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 1)
            );
            dividerParams.topMargin = Ui.dp(requireContext(), 12);
            dividerParams.bottomMargin = Ui.dp(requireContext(), 10);
            card.addView(divider, dividerParams);
            addExpandedChapters(card, subject);
        }
        return card;
    }

    private void addExpandedChapters(LinearLayout card, Subject subject) {
        List<Chapter> chapters = chaptersOf(subject.id);
        boolean batchMode = batchSubjectId == subject.id;
        for (Chapter chapter : chapters) {
            boolean chapterExpanded = expandedChapters.contains(chapter.id);
            LinearLayout chapterHeader = Ui.horizontal(requireContext());
            chapterHeader.setPadding(0, Ui.dp(requireContext(), 5), 0, Ui.dp(requireContext(), 5));
            TextView arrow = Ui.text(requireContext(), chapterExpanded ? "▾" : "▸",
                    13, Ui.accent(requireContext()), true);
            arrow.setPadding(0, 0, Ui.dp(requireContext(), 6), 0);
            TextView chapterTitle = Ui.sectionTitle(requireContext(), chapter.title);
            chapterTitle.setTextColor(chapterExpanded
                    ? Ui.textColor(requireContext()) : Ui.muted(requireContext()));
            Ui.setWeight(chapterTitle, 1);
            View.OnClickListener toggleChapter = view -> {
                if (!expandedChapters.remove(chapter.id)) expandedChapters.add(chapter.id);
                render();
            };
            arrow.setOnClickListener(toggleChapter);
            chapterTitle.setOnClickListener(toggleChapter);
            chapterHeader.addView(arrow);
            chapterHeader.addView(chapterTitle);
            if (batchMode) {
                TextView selectChapter = actionText(
                        chapterFullySelected(chapter.id) ? "取消本章" : "全选本章");
                selectChapter.setOnClickListener(view -> toggleChapterSelection(chapter.id));
                chapterHeader.addView(selectChapter);
            } else {
                TextView addSection = actionText("新增小节");
                addSection.setOnClickListener(view -> addSection(subject, chapter));
                TextView editChapter = actionText("编辑");
                editChapter.setOnClickListener(view -> editChapter(chapter));
                TextView more = actionText("更多");
                more.setOnClickListener(view -> showChapterMenu(more, chapter));
                chapterHeader.addView(editChapter);
                chapterHeader.addView(addSection);
                chapterHeader.addView(more);
            }
            card.addView(chapterHeader);

            List<Section> sections = sectionsOfChapter(chapter.id);
            if (sections.isEmpty()) {
                TextView empty = Ui.caption(requireContext(), "本章还没有小节");
                empty.setPadding(0, Ui.dp(requireContext(), 7), 0, Ui.dp(requireContext(), 7));
                if (chapterExpanded) card.addView(empty);
            }
            if (chapterExpanded) {
                for (Section section : sections) {
                    card.addView(sectionRow(section, subject, batchMode));
                }
            } else {
                TextView count = Ui.caption(requireContext(),
                        sections.size() + " 个小节 · 点击章节展开");
                count.setPadding(Ui.dp(requireContext(), 20),
                        Ui.dp(requireContext(), 1), 0, Ui.dp(requireContext(), 7));
                card.addView(count);
            }
            card.addView(Ui.space(requireContext(), 8));
        }

        Button addChapter = Ui.button(requireContext(), "新增章节", false);
        if (!batchMode) {
            addChapter.setOnClickListener(view -> addChapter(subject));
            card.addView(addChapter);
        }
    }

    private View sectionRow(Section section, Subject subject, boolean selectable) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 7), 0, Ui.dp(requireContext(), 7));
        if (selectable) {
            AnimatedCheckView check = new AnimatedCheckView(requireContext());
            check.setChecked(selectedSectionIds.contains(section.id));
            check.setListener(checked -> toggleSectionSelection(section.id));
            row.addView(check);
        }
        View dot = new View(requireContext());
        int dotColor = section.isSkipped ? Ui.muted(requireContext())
                : section.isFinished ? Ui.success(requireContext())
                : DateUtils.isValidDate(section.planDate)
                && section.planDate.compareTo(DateUtils.today()) < 0
                ? Ui.danger(requireContext()) : Ui.accent(requireContext());
        dot.setBackground(Ui.rounded(requireContext(), dotColor, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 7), Ui.dp(requireContext(), 7)
        );
        dotParams.rightMargin = Ui.dp(requireContext(), 9);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(dot, dotParams);

        LinearLayout text = Ui.vertical(requireContext());
        int titleColor = section.isFinished || section.isSkipped
                ? Ui.muted(requireContext()) : Ui.textColor(requireContext());
        TextView title = Ui.text(requireContext(), section.title, 13, titleColor, false);
        if (section.isFinished) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        text.addView(title);
        String status = section.isSkipped ? "已跳过"
                : section.isFinished ? "已完成 " + DateUtils.friendlyDate(section.finishDate)
                : "计划 " + DateUtils.friendlyDate(section.planDate);
        text.addView(Ui.caption(requireContext(), status));
        Ui.setWeight(text, 1);
        row.addView(text);
        if (!selectable) {
            row.setOnClickListener(view -> editSection(section));
            row.setOnLongClickListener(view -> {
                enterBatch(subject, section);
                return true;
            });
            SwipeActionView swipe = new SwipeActionView(requireContext());
            swipe.setContent(row);
            List<SwipeActionView.Action> sectionActions = new ArrayList<>();
            if (!section.isFinished && !section.isSkipped) {
                sectionActions.add(new SwipeActionView.Action("开始", Ui.accent(requireContext()),
                        () -> startSectionTimer(section, subject)));
            }
            sectionActions.add(new SwipeActionView.Action(section.isFinished ? "恢复" : "完成",
                    Ui.success(requireContext()), () -> {
                        section.isFinished = !section.isFinished;
                        section.finishDate = section.isFinished ? DateUtils.today() : "";
                        if (section.isFinished) section.isSkipped = false;
                        applySectionTitleStyle(title, section);
                        repository.io(() -> repository.dao().updateSection(section),
                                this::refresh);
                    }));
            sectionActions.add(new SwipeActionView.Action(section.isSkipped ? "恢复" : "跳过",
                    COLOR_TODO, () -> {
                        section.isSkipped = !section.isSkipped;
                        if (section.isSkipped) {
                            section.isFinished = false;
                            section.finishDate = "";
                        }
                        repository.io(() -> repository.dao().updateSection(section),
                                this::refresh);
                    }));
            sectionActions.add(new SwipeActionView.Action("删除", Ui.danger(requireContext()),
                    () -> new AlertDialog.Builder(requireContext())
                            .setTitle("删除小节？")
                            .setMessage("历史学习记录会保留。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", (dialog, which) ->
                                    repository.io(() -> repository.dao().deleteSection(section),
                                            this::refresh))
                            .show()));
            swipe.setActions(sectionActions);
            return swipe;
        } else {
            row.setOnClickListener(view -> toggleSectionSelection(section.id));
            if (selectedSectionIds.contains(section.id)) {
                row.setBackground(Ui.rounded(requireContext(),
                        Ui.withAlpha(Ui.accent(requireContext()), 22), 7));
            }
        }
        return row;
    }

    private void enterBatch(Subject subject, Section section) {
        batchSubjectId = subject.id;
        selectedSectionIds.clear();
        selectedSectionIds.add(section.id);
        if (section.chapterId != null) expandedChapters.add(section.chapterId);
        render();
    }

    private void applySectionTitleStyle(TextView title, Section section) {
        boolean done = section.isFinished || section.isSkipped;
        if (done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(Ui.muted(requireContext()));
        } else {
            title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(Ui.textColor(requireContext()));
        }
    }

    private void toggleSectionSelection(long sectionId) {
        if (!selectedSectionIds.remove(sectionId)) {
            selectedSectionIds.add(sectionId);
        }
        updateBatchBar();
    }

    private LinearLayout buildBatchBar() {
        LinearLayout bar = Ui.vertical(requireContext());
        bar.setVisibility(View.GONE);
        bar.setPadding(Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 8),
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 10));
        bar.setBackgroundColor(Ui.surfaceColor(requireContext()));
        bar.setElevation(Ui.dp(requireContext(), 12));
        LinearLayout header = Ui.horizontal(requireContext());
        batchCountText = Ui.text(requireContext(), "已选择 0 个小节", 12,
                Ui.accent(requireContext()), true);
        Ui.setWeight(batchCountText, 1);
        TextView exit = actionText("退出");
        exit.setTextColor(Ui.muted(requireContext()));
        exit.setOnClickListener(view -> exitBatchMode());
        header.addView(batchCountText);
        header.addView(exit);
        bar.addView(header);

        LinearLayout actions = Ui.horizontal(requireContext());
        actions.setPadding(0, Ui.dp(requireContext(), 6), 0, 0);
        Button selectAll = batchAction("全选");
        selectAll.setOnClickListener(view -> toggleAllSubjectSections());
        Button postpone = batchAction("延后");
        postpone.setOnClickListener(view -> choosePostponeDate());
        Button skip = batchAction("跳过");
        skip.setOnClickListener(view -> setSelectedSkipped(true));
        Button complete = batchAction("完成");
        complete.setOnClickListener(view -> setSelectedFinished(true));
        Button delete = batchAction("删除");
        delete.setTextColor(Ui.danger(requireContext()));
        delete.setOnClickListener(view -> confirmBatchDelete());
        actions.addView(selectAll);
        actions.addView(postpone);
        actions.addView(skip);
        actions.addView(complete);
        actions.addView(delete);
        bar.addView(actions);
        return bar;
    }

    private Button batchAction(String label) {
        Button button = Ui.button(requireContext(), label, false);
        button.setTextSize(11);
        button.setMinHeight(Ui.dp(requireContext(), 38));
        button.setPadding(Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 2), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(requireContext(), 40), 1
        );
        params.setMargins(Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void updateBatchBar() {
        if (batchBar == null) return;
        boolean visible = batchSubjectId > 0;
        batchBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (batchCountText != null) {
            batchCountText.setText("已选择 " + selectedSectionIds.size() + " 个小节");
        }
        if (scrollView != null) {
            scrollView.setPadding(0, 0, 0, visible ? Ui.dp(requireContext(), 92) : 0);
        }
    }

    private void exitBatchMode() {
        batchSubjectId = -1;
        selectedSectionIds.clear();
        render();
    }

    private void toggleAllSubjectSections() {
        List<Section> sections = sectionsOf(batchSubjectId);
        if (selectedSectionIds.size() >= sections.size()) {
            selectedSectionIds.clear();
        } else {
            selectedSectionIds.clear();
            for (Section section : sections) selectedSectionIds.add(section.id);
        }
        render();
    }

    private boolean chapterFullySelected(long chapterId) {
        List<Section> sections = sectionsOfChapter(chapterId);
        if (sections.isEmpty()) return false;
        for (Section section : sections) {
            if (!selectedSectionIds.contains(section.id)) return false;
        }
        return true;
    }

    private void toggleChapterSelection(long chapterId) {
        boolean fully = chapterFullySelected(chapterId);
        for (Section section : sectionsOfChapter(chapterId)) {
            if (fully) selectedSectionIds.remove(section.id);
            else selectedSectionIds.add(section.id);
        }
        render();
    }

    private void setSelectedFinished(boolean finished) {
        if (selectedSectionIds.isEmpty()) return;
        repository.io(() -> {
            for (Section section : loaded.sections) {
                if (selectedSectionIds.contains(section.id)) {
                    section.isFinished = finished;
                    section.finishDate = finished ? DateUtils.today() : "";
                    if (finished) section.isSkipped = false;
                    repository.dao().updateSection(section);
                }
            }
        }, this::refresh);
    }

    private void setSelectedSkipped(boolean skipped) {
        if (selectedSectionIds.isEmpty()) return;
        repository.io(() -> {
            for (Section section : loaded.sections) {
                if (selectedSectionIds.contains(section.id)) {
                    section.isSkipped = skipped;
                    if (skipped) {
                        section.isFinished = false;
                        section.finishDate = "";
                    }
                    repository.dao().updateSection(section);
                }
            }
        }, this::refresh);
    }

    private void choosePostponeDate() {
        if (selectedSectionIds.isEmpty()) return;
        Dialogs.pickDate(requireContext(), DateUtils.today(), date -> {
            repository.io(() -> {
                for (Section section : loaded.sections) {
                    if (selectedSectionIds.contains(section.id)) {
                        section.planDate = date;
                        repository.dao().updateSection(section);
                    }
                }
            }, this::refresh);
        });
    }

    private void confirmBatchDelete() {
        if (selectedSectionIds.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("批量删除 " + selectedSectionIds.size() + " 个小节？")
                .setMessage("历史学习记录会保留，此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    List<Long> ids = new ArrayList<>(selectedSectionIds);
                    repository.io(() -> {
                        for (Section section : loaded.sections) {
                            if (ids.contains(section.id)) {
                                repository.dao().deleteSection(section);
                            }
                        }
                    }, () -> {
                        selectedSectionIds.clear();
                        batchSubjectId = -1;
                        refresh();
                    });
                })
                .show();
    }

    private void addSubject() {
        Dialogs.subjectForm(requireContext(), null, subject ->
                repository.io(() -> {
                    if (subject.createAt == null || subject.createAt.isEmpty()) {
                        subject.createAt = DateUtils.nowText();
                    }
                    repository.dao().insertSubject(subject);
                }, this::refresh));
    }

    private void editSubject(Subject subject) {
        AlertDialog.Builder choice = new AlertDialog.Builder(requireContext())
                .setTitle(subject.name);
        choice.setItems(new String[]{"编辑名称与默认计时", "删除科目"}, (dialog, which) -> {
            if (which == 0) {
                Dialogs.subjectForm(requireContext(), subject, value ->
                        repository.io(() -> repository.dao().updateSubject(value), this::refresh));
            } else {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除科目？")
                        .setMessage("章节和小节会一起删除；历史学习记录会保留。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) ->
                                repository.deleteSubjectCascade(subject, this::refresh))
                        .show();
            }
        });
        choice.setNegativeButton("取消", null);
        choice.show();
    }

    private void addChapter(Subject subject) {
        EditText title = Ui.input(requireContext(), "章节标题");
        title.setText("第" + (chaptersOf(subject.id).size() + 1) + "章");
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("新增章节")
                .setView(wrapDialog(title))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) {
                        title.setError("请输入章节标题");
                        return;
                    }
                    Chapter chapter = new Chapter();
                    chapter.subjectId = subject.id;
                    chapter.title = value;
                    chapter.sortOrder = chaptersOf(subject.id).size();
                    repository.io(() -> repository.dao().insertChapter(chapter), this::refresh);
                    expanded.add(subject.id);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void editChapter(Chapter chapter) {
        EditText title = Ui.input(requireContext(), "章节标题");
        title.setText(chapter.title);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("编辑章节")
                .setView(wrapDialog(title))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) return;
                    chapter.title = value;
                    repository.io(() -> repository.dao().updateChapter(chapter), this::refresh);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showChapterMenu(View anchor, Chapter chapter) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑章节");
        menu.getMenu().add("删除章节");
        menu.setOnMenuItemClickListener(item -> {
            if (String.valueOf(item.getTitle()).startsWith("编辑")) {
                editChapter(chapter);
            } else {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除章节？")
                        .setMessage("章节及其中的小节会一起删除。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> repository.io(() -> {
                            repository.dao().deleteSectionsByChapter(chapter.id);
                            repository.dao().deleteChapter(chapter);
                        }, this::refresh))
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private void addSection(Subject subject, Chapter chapter) {
        Dialogs.sectionForm(requireContext(), null, loaded.subjects, subject.id, "", section -> {
            section.chapterId = chapter.id;
            section.source = "course";
            section.sortOrder = sectionsOfChapter(chapter.id).size();
            expandedChapters.add(chapter.id);
            repository.io(() -> repository.dao().insertSection(section), this::refresh);
        });
    }

    private void editSection(Section section) {
        long chapterId = section.chapterId == null ? -1 : section.chapterId;
        Dialogs.sectionForm(requireContext(), section, loaded.subjects, section.subjectId, value -> {
            value.chapterId = chapterId < 0 ? null : chapterId;
            repository.io(() -> repository.dao().updateSection(value), this::refresh);
        });
    }

    private void showSectionMenu(View anchor, Section section, Subject subject) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("编辑 / 排日期");
        menu.getMenu().add(section.isFinished ? "恢复为未完成" : "标记完成");
        menu.getMenu().add(section.isSkipped ? "取消跳过" : "跳过");
        menu.getMenu().add("删除");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (title.startsWith("编辑")) {
                editSection(section);
            } else if (title.equals("删除")) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除小节？")
                        .setMessage("历史学习记录会保留。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) ->
                                repository.io(() -> repository.dao().deleteSection(section), this::refresh))
                        .show();
            } else if (title.startsWith("跳过") || title.startsWith("取消跳过")) {
                section.isSkipped = !section.isSkipped;
                repository.io(() -> repository.dao().updateSection(section), this::refresh);
            } else {
                section.isFinished = !section.isFinished;
                section.finishDate = section.isFinished ? DateUtils.today() : "";
                repository.io(() -> repository.dao().updateSection(section), this::refresh);
            }
            return true;
        });
        menu.show();
    }

    private void startSubjectTimer(Subject subject) {
        Dialogs.timerRule(requireContext(), subject.name, subject.name, subject.id,
                null, null, null, subject.timerMode, subject.timerDuration);
    }

    private void startSectionTimer(Section section, Subject subject) {
        Dialogs.timerRule(requireContext(), section.title, subject.name, subject.id,
                section.id, null, null, subject.timerMode, subject.timerDuration);
    }

    private void showImportDialog() {
        LinearLayout body = Ui.dialogBody(requireContext());
        EditText name = Ui.input(requireContext(), "科目名称");
        EditText raw = Ui.multilineInput(requireContext(),
                "粘贴学科目录，例如：\n第1章 操作系统概述\n1.1 进程与线程\n1.2 内存管理");
        raw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView hint = Ui.caption(requireContext(),
                "支持“第X章”、“1. / 1.1 / 1.1.1”和分点符号。导入前会显示预览。");
        hint.setPadding(0, Ui.dp(requireContext(), 8), 0, 0);
        body.addView(name);
        body.addView(Ui.space(requireContext(), 8));
        body.addView(raw);
        body.addView(hint);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("粘贴导入学科目录")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("预览", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String subjectName = name.getText().toString().trim();
                    String text = raw.getText().toString().trim();
                    if (subjectName.isEmpty()) {
                        name.setError("请输入科目名称");
                        return;
                    }
                    if (text.isEmpty()) {
                        raw.setError("请粘贴目录文字");
                        return;
                    }
                    DirectoryParser.Parsed parsed = DirectoryParser.parse(text);
                    if (parsed.chapters.isEmpty()) {
                        raw.setError("没有识别到可用目录");
                        return;
                    }
                    dialog.dismiss();
                    previewImport(subjectName, parsed);
                }));
        dialog.show();
    }

    private void previewImport(String subjectName, DirectoryParser.Parsed parsed) {
        LinearLayout body = Ui.dialogBody(requireContext());
        TextView summary = Ui.text(requireContext(),
                parsed.chapters.size() + " 个章节 · " + parsed.sectionCount() + " 个小节",
                14, Ui.accent(requireContext()), true);
        body.addView(summary);
        body.addView(Ui.space(requireContext(), 8));
        int shown = 0;
        for (DirectoryParser.ParsedChapter chapter : parsed.chapters) {
            body.addView(Ui.text(requireContext(), chapter.title, 13,
                    Ui.textColor(requireContext()), true));
            int sectionShown = 0;
            for (String section : chapter.sections) {
                if (sectionShown >= 3) break;
                TextView row = Ui.caption(requireContext(), "  " + section);
                row.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 2),
                        0, Ui.dp(requireContext(), 2));
                body.addView(row);
                sectionShown++;
            }
            if (chapter.sections.size() > sectionShown) {
                body.addView(Ui.caption(requireContext(),
                        "  还有 " + (chapter.sections.size() - sectionShown) + " 个小节"));
            }
            shown++;
            if (shown >= 8) break;
        }
        if (shown < parsed.chapters.size()) {
            body.addView(Ui.caption(requireContext(),
                    "预览只显示前 8 章，导入时会包含全部内容。"));
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(subjectName)
                .setView(body)
                .setNegativeButton("返回修改", null)
                .setPositiveButton("确认导入", (dialog, which) -> importDirectory(subjectName, parsed))
                .show();
    }

    private void importDirectory(String subjectName, DirectoryParser.Parsed parsed) {
        repository.io(() -> {
            Subject subject = null;
            for (Subject existing : repository.dao().getAllSubjectsSync()) {
                if (existing.name.trim().equalsIgnoreCase(subjectName.trim())) {
                    subject = existing;
                    break;
                }
            }
            if (subject == null) {
                subject = new Subject();
                subject.name = subjectName;
                subject.createAt = DateUtils.nowText();
                subject.timerMode = "positive";
                subject.timerDuration = 25;
                subject.id = repository.dao().insertSubject(subject);
            }
            int chapterOrder = 0;
            for (Chapter existing : repository.dao().getAllChaptersSync()) {
                if (existing.subjectId == subject.id) chapterOrder++;
            }
            for (DirectoryParser.ParsedChapter parsedChapter : parsed.chapters) {
                Chapter chapter = new Chapter();
                chapter.subjectId = subject.id;
                chapter.title = parsedChapter.title;
                chapter.sortOrder = chapterOrder++;
                chapter.id = repository.dao().insertChapter(chapter);
                int sectionOrder = 0;
                for (String title : parsedChapter.sections) {
                    Section section = new Section();
                    section.subjectId = subject.id;
                    section.chapterId = chapter.id;
                    section.title = title;
                    section.planDate = "";
                    section.source = "course";
                    section.sortOrder = sectionOrder++;
                    repository.dao().insertSection(section);
                }
            }
            expanded.add(subject.id);
        }, this::refresh);
    }

    private void showRepeatManager() {
        RepeatTaskManager.show(requireContext(), loaded.repeats, loaded.subjects,
                new RepeatTaskManager.Actions() {
                    @Override
                    public void onAdd() {
                        Dialogs.repeatForm(requireContext(), null, loaded.subjects, value ->
                                repository.io(() -> repository.dao().insertRepeatTask(value),
                                        DirectoryFragment.this::refresh));
                    }

                    @Override
                    public void onEdit(RepeatTask task) {
                        Dialogs.repeatForm(requireContext(), task, loaded.subjects, value ->
                                repository.io(() -> repository.dao().updateRepeatTask(value),
                                        DirectoryFragment.this::refresh));
                    }

                    @Override
                    public void onDelete(RepeatTask task) {
                        repository.io(() -> repository.dao().deleteRepeatTask(task),
                                DirectoryFragment.this::refresh);
                    }
                });
    }

    private List<Chapter> chaptersOf(long subjectId) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter chapter : loaded.chapters) {
            if (chapter.subjectId == subjectId) result.add(chapter);
        }
        return result;
    }

    private List<Section> sectionsOf(long subjectId) {
        List<Section> result = new ArrayList<>();
        for (Section section : loaded.sections) {
            if (section.subjectId != null && section.subjectId == subjectId
                    && section.chapterId != null) {
                result.add(section);
            }
        }
        return result;
    }

    private List<Section> sectionsOfChapter(long chapterId) {
        List<Section> result = new ArrayList<>();
        for (Section section : loaded.sections) {
            if (section.chapterId != null && section.chapterId == chapterId) {
                result.add(section);
            }
        }
        result.sort((left, right) -> {
            int byOrder = Integer.compare(left.sortOrder, right.sortOrder);
            return byOrder != 0 ? byOrder : Long.compare(left.id, right.id);
        });
        return result;
    }

    private long subjectSeconds(long subjectId) {
        long seconds = 0;
        for (StudyRecord record : loaded.records) {
            if (record.subjectId != null && record.subjectId == subjectId) {
                seconds += Math.max(0, record.durationSec);
            }
        }
        return seconds;
    }

    private TextView actionText(String label) {
        TextView view = Ui.text(requireContext(), label, 11,
                Ui.accent(requireContext()), true);
        view.setPadding(Ui.dp(requireContext(), 7), Ui.dp(requireContext(), 7),
                Ui.dp(requireContext(), 5), Ui.dp(requireContext(), 7));
        return view;
    }

    private View wrapDialog(View content) {
        LinearLayout wrapper = Ui.dialogBody(requireContext());
        wrapper.addView(content);
        return wrapper;
    }

    private String repeatLabel(RepeatTask task) {
        switch (task.repeatType) {
            case "weekly": return "每周 " + task.repeatValue + " · " + subjectName(task.subjectId);
            case "monthly": return "每月 " + task.repeatValue + " 日 · " + subjectName(task.subjectId);
            default: return "每天 · " + subjectName(task.subjectId);
        }
    }

    private String subjectName(Long id) {
        if (id == null) return "其他";
        for (Subject subject : loaded.subjects) if (subject.id == id) return subject.name;
        return "其他";
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
        List<Subject> subjects = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        List<StudyRecord> records = new ArrayList<>();
        List<RepeatTask> repeats = new ArrayList<>();
    }

    private static class DialogHolder {
        static DialogHolder instance;
        final AlertDialog dialog;

        DialogHolder(AlertDialog dialog) {
            this.dialog = dialog;
        }
    }
}
