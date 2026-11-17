package com.localstudy.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.localstudy.app.BuildConfig;
import com.localstudy.app.MainActivity;
import com.localstudy.app.R;
import com.localstudy.app.data.BackupData;
import com.localstudy.app.data.StudyDao;
import com.localstudy.app.data.StudyRepository;
import com.localstudy.app.service.CalendarReminderScheduler;
import com.localstudy.app.service.CourseReminderScheduler;
import com.localstudy.app.service.ReminderScheduler;
import com.localstudy.app.util.BackupManager;
import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.SettingsStore;
import com.localstudy.app.util.Ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends Fragment implements Refreshable {
    private StudyRepository repository;
    private LinearLayout content;
    private String pendingExportJson;
    private final ActivityResultLauncher<String> createBackup =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null && pendingExportJson != null) {
                    writeBackup(uri, pendingExportJson);
                }
            });
    private final ActivityResultLauncher<String[]> openBackup =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) readBackup(uri);
            });

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
        scroll.addView(content);
        render();
        return scroll;
    }

    @Override
    public void refresh() {
        if (content != null && isAdded()) render();
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(Ui.title(requireContext(), "设置"));
        TextView intro = Ui.caption(requireContext(), "数据只保存在这台设备");
        intro.setPadding(0, Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 16));
        content.addView(intro);

        addSection("数据管理");
        LinearLayout backupCard = Ui.surface(requireContext());
        addActionRow(backupCard, "导出全部数据", "生成 JSON 文件", "导出", this::prepareExport);
        addDivider(backupCard);
        addActionRow(backupCard, "导入备份", "覆盖当前数据", "导入", () ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("覆盖导入？")
                        .setMessage("导入后，当前全部目录、任务、待办和学习记录会被备份文件覆盖。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("选择文件", (dialog, which) ->
                                openBackup.launch(new String[]{
                                        "application/json", "text/plain", "*/*"
                                }))
                        .show());
        content.addView(backupCard);
        content.addView(Ui.space(requireContext(), 16));

        addSection("清空数据");
        LinearLayout clearCard = Ui.surface(requireContext());
        addActionRow(clearCard, "清空学习记录", "保留目录、任务和待办", "清空",
                () -> confirmTwice("清空所有学习记录？", "此操作不可撤销。",
                        () -> repository.io(() -> {
                            BackupManager.clearRecords(repository.dao());
                        }, this::refresh)));
        addDivider(clearCard);
        addActionRow(clearCard, "清空科目目录", "保留下属历史学习记录", "清空",
                () -> confirmTwice("清空所有科目目录？", "章节和小节会一并删除。",
                        () -> repository.io(() -> {
                            BackupManager.clearCatalog(repository.dao());
                        }, this::refresh)));
        addDivider(clearCard);
        addActionRow(clearCard, "全部清空", "删除本机全部学习数据", "全部清空",
                () -> confirmTwice("清空全部数据？", "所有数据都会永久删除。",
                        () -> repository.io(() -> {
                            BackupManager.clearAll(repository.dao());
                            CourseReminderScheduler.rescheduleAll(requireContext());
                        }, this::refresh)));
        content.addView(clearCard);
        content.addView(Ui.space(requireContext(), 16));

        addSection("外观");
        content.addView(appearanceCard());
        content.addView(Ui.space(requireContext(), 16));

        addSection("提醒");
        content.addView(reminderCard());
        content.addView(Ui.space(requireContext(), 16));

        addSection("关于");
        LinearLayout about = Ui.surface(requireContext());
        LinearLayout aboutRow = Ui.horizontal(requireContext());
        LinearLayout aboutText = Ui.vertical(requireContext());
        aboutText.addView(Ui.text(requireContext(), "拾光学习", 14,
                Ui.textColor(requireContext()), true));
        aboutText.addView(Ui.caption(requireContext(), "本地优先的 Android 学习计划与专注记录"));
        Ui.setWeight(aboutText, 1);
        aboutRow.addView(aboutText);
        aboutRow.addView(Ui.caption(requireContext(), "v" + BuildConfig.VERSION_NAME));
        about.addView(aboutRow);
        content.addView(about);
        content.addView(Ui.space(requireContext(), 10));
        TextView privacy = Ui.caption(requireContext(),
                "无账号 · 无云同步 · 无网络权限 · 数据由你掌控");
        privacy.setGravity(Gravity.CENTER);
        content.addView(privacy);
    }

    private View appearanceCard() {
        LinearLayout card = Ui.surface(requireContext());
        card.addView(label("明暗模式"));
        LinearLayout themeRow = segmentedRow(new String[]{"跟随系统", "浅色", "深色"},
                new String[]{"system", "light", "dark"}, SettingsStore.theme(requireContext()),
                value -> {
                    SettingsStore.prefs(requireContext()).edit()
                            .putString(SettingsStore.KEY_THEME, value).apply();
                    SettingsStore.applyNightMode(requireContext());
                    requireActivity().recreate();
                });
        card.addView(themeRow);

        card.addView(label("主题颜色"));
        LinearLayout colorRow = Ui.horizontal(requireContext());
        addAccentChoice(colorRow, "青绿", "teal", 0xFF207A66);
        addAccentChoice(colorRow, "靛蓝", "blue", 0xFF356AA8);
        addAccentChoice(colorRow, "陶橙", "orange", 0xFFC66535);
        card.addView(colorRow);
        LinearLayout colorRowTwo = Ui.horizontal(requireContext());
        addAccentChoice(colorRowTwo, "莓紫", "plum", 0xFF8A5078);
        addAccentChoice(colorRowTwo, "珊瑚", "coral", 0xFFC95560);
        addAccentChoice(colorRowTwo, "琥珀", "gold", 0xFF9C741B);
        LinearLayout.LayoutParams secondColors = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        secondColors.topMargin = Ui.dp(requireContext(), 6);
        card.addView(colorRowTwo, secondColors);

        card.addView(label("字体大小"));
        SeekBar font = new SeekBar(requireContext());
        font.setMax(3);
        float current = SettingsStore.fontScale(requireContext());
        int progress = current <= 0.9f ? 0 : current <= 1.05f ? 1 : current <= 1.2f ? 2 : 3;
        font.setProgress(progress);
        TextView fontValue = Ui.caption(requireContext(), fontLabel(progress));
        font.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                fontValue.setText(fontLabel(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float[] values = {0.88f, 1f, 1.15f, 1.3f};
                SettingsStore.prefs(requireContext()).edit()
                        .putFloat(SettingsStore.KEY_FONT_SCALE, values[seekBar.getProgress()])
                        .apply();
                requireActivity().recreate();
            }
        });
        LinearLayout fontHeader = Ui.horizontal(requireContext());
        Ui.setWeight(fontValue, 1);
        fontHeader.addView(fontValue);
        card.addView(fontHeader);
        card.addView(font);
        return card;
    }

    private View reminderCard() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(), "每日学习提醒", 14,
                Ui.textColor(requireContext()), true));
        text.addView(Ui.caption(requireContext(), "每天 21:00 提醒未完成任务与逾期待办"));
        Ui.setWeight(text, 1);
        SwitchCompat toggle = new SwitchCompat(requireContext());
        toggle.setChecked(SettingsStore.reminder(requireContext()));
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                toggle.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 902);
                return;
            }
            SettingsStore.prefs(requireContext()).edit()
                    .putBoolean(SettingsStore.KEY_REMINDER, checked)
                    .apply();
            ReminderScheduler.schedule(requireContext());
        });
        row.addView(text);
        row.addView(toggle);
        card.addView(row);
        card.addView(Ui.space(requireContext(), 8));
        card.addView(Ui.caption(requireContext(),
                "提醒使用系统本地通知，不需要联网或第三方推送。"));
        addDivider(card);

        LinearLayout summaryRow = Ui.horizontal(requireContext());
        LinearLayout summaryText = Ui.vertical(requireContext());
        summaryText.addView(Ui.text(requireContext(), "每日课程汇总", 14,
                Ui.textColor(requireContext()), true));
        summaryText.addView(Ui.caption(requireContext(),
                "有早课时，在第一节课前 30 分钟汇总当天课程"));
        Ui.setWeight(summaryText, 1);
        SwitchCompat summarySwitch = new SwitchCompat(requireContext());
        summarySwitch.setChecked(SettingsStore.courseSummary(requireContext()));
        summarySwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            SettingsStore.prefs(requireContext()).edit()
                    .putBoolean(SettingsStore.KEY_COURSE_SUMMARY, checked).apply();
            CourseReminderScheduler.rescheduleAll(requireContext());
        });
        summaryRow.addView(summaryText);
        summaryRow.addView(summarySwitch);
        card.addView(summaryRow);
        card.addView(Ui.space(requireContext(), 12));

        LinearLayout classRow = Ui.horizontal(requireContext());
        LinearLayout classText = Ui.vertical(requireContext());
        classText.addView(Ui.text(requireContext(), "每节课前提醒", 14,
                Ui.textColor(requireContext()), true));
        classText.addView(Ui.caption(requireContext(),
                "包含课程、时间、教室和老师")); 
        Ui.setWeight(classText, 1);
        SwitchCompat classSwitch = new SwitchCompat(requireContext());
        classSwitch.setChecked(SettingsStore.courseReminder(requireContext()));
        classRow.addView(classText);
        classRow.addView(classSwitch);
        card.addView(classRow);

        Spinner minutes = new Spinner(requireContext());
        minutes.setAdapter(new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"提前 5 分钟", "提前 10 分钟", "提前 15 分钟"}
        ));
        int[] minuteValues = {5, 10, 15};
        int selected = 1;
        for (int i = 0; i < minuteValues.length; i++) {
            if (minuteValues[i] == SettingsStore.courseReminderMinutes(requireContext())) {
                selected = i;
            }
        }
        minutes.setSelection(selected);
        minutes.setEnabled(classSwitch.isChecked());
        minutes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       View view, int position, long id) {
                SettingsStore.prefs(requireContext()).edit()
                        .putInt(SettingsStore.KEY_COURSE_REMINDER_MINUTES,
                                minuteValues[position]).apply();
                CourseReminderScheduler.rescheduleAll(requireContext());
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        classSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            SettingsStore.prefs(requireContext()).edit()
                    .putBoolean(SettingsStore.KEY_COURSE_REMINDER, checked).apply();
            minutes.setEnabled(checked);
            CourseReminderScheduler.rescheduleAll(requireContext());
        });
        LinearLayout minutesHolder = Ui.vertical(requireContext());
        minutesHolder.setPadding(0, Ui.dp(requireContext(), 8),
                0, Ui.dp(requireContext(), 2));
        minutesHolder.addView(minutes);
        card.addView(minutesHolder);
        return card;
    }

    private void prepareExport() {
        repository.io(() -> BackupManager.export(requireContext(), repository.dao()),
                (json, error) -> {
                    if (!isAdded()) return;
                    if (error != null) {
                        Toast.makeText(requireContext(), "生成备份失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pendingExportJson = json;
                    String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                    createBackup.launch("拾光学习备份_" + date + ".json");
                });
    }

    private void writeBackup(Uri uri, String json) {
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("无法打开文件");
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(requireContext(), "备份已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(requireContext(), "写入备份失败：" + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void readBackup(Uri uri) {
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line).append('\n');
            BackupData data = BackupManager.parse(json.toString());
            String summary = data.subjects.size() + " 个科目 · "
                    + data.sections.size() + " 个小节 · "
                    + data.todos.size() + " 个待办 · "
                    + data.calendarEvents.size() + " 项日历日程 · "
                    + data.courses.size() + " 门课程 · "
                    + data.records.size() + " 条学习记录";
            new AlertDialog.Builder(requireContext())
                    .setTitle("确认覆盖导入")
                    .setMessage(summary + "\n\n当前数据将被完整覆盖。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("覆盖导入", (dialog, which) ->
                            repository.io(() -> {
                                BackupManager.restore(requireContext(), repository.dao(), data);
                            }, () -> {
                                SettingsStore.applyNightMode(requireContext());
                                ReminderScheduler.schedule(requireContext());
                                CalendarReminderScheduler.rescheduleAll(requireContext());
                                CourseReminderScheduler.rescheduleAll(requireContext());
                                requireActivity().recreate();
                            }))
                    .show();
        } catch (Exception error) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("无法导入")
                    .setMessage("文件不是有效的拾光学习 JSON 备份。\n\n" + error.getMessage())
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }

    private void confirmTwice(String title, String message, Runnable action) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message + "\n\n继续后会再要求确认一次。")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle("再次确认")
                                .setMessage("这是最后一步，确定执行吗？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("确定清空", (d, w) -> {
                                    action.run();
                                    Toast.makeText(requireContext(), "已清空", Toast.LENGTH_SHORT).show();
                                })
                                .show())
                .show();
    }

    private LinearLayout segmentedRow(String[] labels, String[] values, String selected,
                                      ValueCallback callback) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setBackground(Ui.rounded(requireContext(), Ui.surfaceAlt(requireContext()), 8));
        row.setPadding(Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3),
                Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3));
        for (int i = 0; i < labels.length; i++) {
            TextView tab = Ui.text(requireContext(), labels[i], 12,
                    selected.equals(values[i]) ? Color.WHITE : Ui.muted(requireContext()), true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 8));
            tab.setBackground(Ui.rounded(requireContext(),
                    selected.equals(values[i]) ? Ui.accent(requireContext()) : Color.TRANSPARENT, 7));
            final String value = values[i];
            tab.setOnClickListener(view -> callback.onValue(value));
            row.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private void addAccentChoice(LinearLayout row, String label, String value, int color) {
        boolean selected = SettingsStore.accent(requireContext()).equals(value);
        LinearLayout choice = Ui.vertical(requireContext());
        choice.setGravity(Gravity.CENTER);
        View swatch = new View(requireContext());
        swatch.setBackground(Ui.rounded(requireContext(), color, 20));
        choice.addView(swatch, new LinearLayout.LayoutParams(
                Ui.dp(requireContext(), 34), Ui.dp(requireContext(), 34)
        ));
        TextView text = Ui.text(requireContext(), label, 11,
                selected ? Ui.textColor(requireContext()) : Ui.muted(requireContext()), selected);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(requireContext(), 4), 0, 0);
        choice.addView(text);
        if (selected) {
            choice.setBackground(Ui.rounded(requireContext(),
                    Ui.withAlpha(color, 24), 8));
        }
        choice.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 8),
                Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 8));
        choice.setOnClickListener(view -> {
            SettingsStore.prefs(requireContext()).edit()
                    .putString(SettingsStore.KEY_ACCENT, value).apply();
            requireActivity().recreate();
        });
        row.addView(choice, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void addSection(String title) {
        TextView label = Ui.text(requireContext(), title, 12,
                Ui.muted(requireContext()), true);
        label.setPadding(Ui.dp(requireContext(), 2), 0, 0, Ui.dp(requireContext(), 7));
        content.addView(label);
    }

    private void addActionRow(LinearLayout card, String title, String subtitle,
                              String actionLabel, Runnable action) {
        LinearLayout row = Ui.horizontal(requireContext());
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(), title, 14, Ui.textColor(requireContext()), true));
        text.addView(Ui.caption(requireContext(), subtitle));
        Ui.setWeight(text, 1);
        TextView button = Ui.text(requireContext(), actionLabel, 12,
                "全部清空".equals(actionLabel) ? Ui.danger(requireContext()) : Ui.accent(requireContext()), true);
        button.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 8),
                Ui.dp(requireContext(), 2), Ui.dp(requireContext(), 8));
        button.setOnClickListener(view -> action.run());
        row.addView(text);
        row.addView(button);
        row.setPadding(0, Ui.dp(requireContext(), 3), 0, Ui.dp(requireContext(), 3));
        card.addView(row);
    }

    private TextView label(String text) {
        TextView view = Ui.caption(requireContext(), text);
        view.setPadding(0, Ui.dp(requireContext(), 12), 0, Ui.dp(requireContext(), 6));
        return view;
    }

    private void addDivider(LinearLayout card) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(Ui.border(requireContext()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
        );
        params.topMargin = Ui.dp(requireContext(), 8);
        params.bottomMargin = Ui.dp(requireContext(), 8);
        card.addView(divider, params);
    }

    private String fontLabel(int progress) {
        return new String[]{"小", "标准", "大", "特大"}[Math.max(0, Math.min(3, progress))];
    }

    private interface ValueCallback {
        void onValue(String value);
    }
}
