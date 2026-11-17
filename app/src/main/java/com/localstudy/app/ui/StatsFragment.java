package com.localstudy.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.localstudy.app.data.Chapter;
import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.Section;
import com.localstudy.app.data.StudyRecord;
import com.localstudy.app.data.StudyRepository;
import com.localstudy.app.data.Subject;
import com.localstudy.app.util.DateUtils;
import com.localstudy.app.util.Dialogs;
import com.localstudy.app.util.Ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsFragment extends Fragment implements Refreshable {
    private StudyRepository repository;
    private LinearLayout content;
    private LinearLayout chartContainer;
    private String range = "week";
    private int dayOffset;
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
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 16),
                Ui.dp(requireContext(), 18),
                Ui.dp(requireContext(), 28)
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
                Toast.makeText(requireContext(), "读取统计失败", Toast.LENGTH_SHORT).show();
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
        LinearLayout headerText = Ui.vertical(requireContext());
        headerText.addView(Ui.title(requireContext(), "学习统计"));
        headerText.addView(Ui.caption(requireContext(), "只记录真实的专注时间"));
        Ui.setWeight(headerText, 1);
        header.addView(headerText);
        content.addView(header);
        content.addView(Ui.space(requireContext(), 14));

        content.addView(metricBand());
        content.addView(Ui.space(requireContext(), 10));
        content.addView(rangeSelector());
        content.addView(Ui.space(requireContext(), 10));

        chartContainer = Ui.vertical(requireContext());
        content.addView(chartContainer);
        renderCharts();

        content.addView(Ui.space(requireContext(), 12));
        content.addView(progressSection());
        content.addView(Ui.space(requireContext(), 12));
        content.addView(weeklyReport());
    }

    private View metricBand() {
        LinearLayout band = Ui.surface(requireContext());
        LinearLayout row = Ui.horizontal(requireContext());
        addMetric(row, String.valueOf(DateUtils.consecutiveDays(loaded.records)), "连续学习天数", Ui.accent(requireContext()));
        addMetric(row, DateUtils.durationHuman(totalSeconds()), "累计学习", Ui.textColor(requireContext()));
        LocalDate now = LocalDate.now();
        long month = DateUtils.totalForRange(loaded.records,
                now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth()));
        addMetric(row, DateUtils.durationHuman(month), "本月学习", Ui.textColor(requireContext()));
        band.addView(row);
        return band;
    }

    private void addMetric(LinearLayout row, String value, String label, int color) {
        LinearLayout item = Ui.vertical(requireContext());
        TextView number = Ui.text(requireContext(), value, 19, color, true);
        number.setMaxLines(1);
        TextView caption = Ui.caption(requireContext(), label);
        item.addView(number);
        item.addView(caption);
        Ui.setWeight(item, 1);
        row.addView(item);
    }

    private View rangeSelector() {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setBackground(Ui.rounded(requireContext(), Ui.surfaceAlt(requireContext()), 8));
        row.setPadding(Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3),
                Ui.dp(requireContext(), 3), Ui.dp(requireContext(), 3));
        String[][] items = {{"day", "日"}, {"week", "近 7 天"}, {"month", "近 30 天"}};
        for (String[] item : items) {
            TextView tab = Ui.text(requireContext(), item[1], 12,
                    range.equals(item[0]) ? Color.WHITE : Ui.muted(requireContext()), true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 8));
            tab.setBackground(Ui.rounded(requireContext(),
                    range.equals(item[0]) ? Ui.accent(requireContext()) : Color.TRANSPARENT, 7));
            tab.setOnClickListener(view -> {
                if (!range.equals(item[0])) dayOffset = 0;
                range = item[0];
                render();
            });
            row.addView(tab, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
            ));
        }
        return row;
    }

    private void renderCharts() {
        if (chartContainer == null) return;
        chartContainer.removeAllViews();
        List<LocalDate> days = rangeDays();
        Map<String, Long> subjectTotals = collectSubjectTotals(days);

        LinearLayout barCard = Ui.surface(requireContext());
        TextView barTitle = Ui.sectionTitle(requireContext(), "每日学习时长");
        barCard.addView(barTitle);
        barCard.addView(Ui.caption(requireContext(), "按科目分色堆叠，单位：分钟"));
        if (range.equals("day")) {
            barCard.addView(daySwitcher());
        }
        BarChart bar = new BarChart(requireContext());
        LinearLayout detail = Ui.vertical(requireContext());
        detail.setVisibility(View.GONE);
        detail.setPadding(Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 10),
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 10));
        detail.setBackground(Ui.rounded(requireContext(),
                Ui.surfaceAlt(requireContext()), 8));
        configureBarChart(bar, days, subjectTotals, detail);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 220)
        );
        barParams.topMargin = Ui.dp(requireContext(), 8);
        barCard.addView(bar, barParams);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = Ui.dp(requireContext(), 6);
        barCard.addView(detail, detailParams);
        barCard.addView(barLegend(days, subjectTotals, detail));
        chartContainer.addView(barCard);
        chartContainer.addView(Ui.space(requireContext(), 10));

        LinearLayout pieCard = Ui.surface(requireContext());
        pieCard.addView(Ui.sectionTitle(requireContext(), "科目占比"));
        PieChart pie = new PieChart(requireContext());
        configurePieChart(pie, subjectTotals);
        LinearLayout.LayoutParams pieParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 220)
        );
        pieParams.topMargin = Ui.dp(requireContext(), 4);
        pieCard.addView(pie, pieParams);
        pieCard.addView(legend(subjectTotals));
        chartContainer.addView(pieCard);
    }

    private List<LocalDate> rangeDays() {
        int count = range.equals("day") ? 1 : range.equals("month") ? 30 : 7;
        if (range.equals("day")) {
            return java.util.Collections.singletonList(LocalDate.now().plusDays(dayOffset));
        }
        return DateUtils.recentDays(count);
    }

    private View daySwitcher() {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 6), 0, Ui.dp(requireContext(), 2));
        TextView previous = Ui.text(requireContext(), "‹", 22,
                Ui.accent(requireContext()), true);
        previous.setPadding(Ui.dp(requireContext(), 12), 0,
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 2));
        previous.setOnClickListener(view -> shiftDay(-1));
        TextView date = Ui.text(requireContext(),
                (dayOffset == 0 ? "今天 · " : "") + LocalDate.now().plusDays(dayOffset)
                        .getMonthValue() + "月"
                        + LocalDate.now().plusDays(dayOffset).getDayOfMonth() + "日",
                13, Ui.textColor(requireContext()), true);
        date.setGravity(Gravity.CENTER);
        Ui.setWeight(date, 1);
        TextView next = Ui.text(requireContext(), "›", 22,
                dayOffset < 0 ? Ui.accent(requireContext()) : Ui.muted(requireContext()), true);
        next.setPadding(Ui.dp(requireContext(), 12), 0,
                Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 2));
        next.setOnClickListener(view -> shiftDay(1));
        row.addView(previous);
        row.addView(date);
        row.addView(next);
        return row;
    }

    private void shiftDay(int direction) {
        dayOffset = Math.min(0, dayOffset + direction);
        render();
    }

    private Map<String, Long> collectSubjectTotals(List<LocalDate> days) {
        LocalDate first = days.get(0);
        LocalDate last = days.get(days.size() - 1);
        Map<String, Long> result = new LinkedHashMap<>();
        for (StudyRecord record : loaded.records) {
            LocalDate day = DateUtils.parseRecordDate(record);
            if (day == null || day.isBefore(first) || day.isAfter(last)) continue;
            String name = record.subjectName == null || record.subjectName.isEmpty()
                    ? "其他" : record.subjectName;
            result.put(name, result.getOrDefault(name, 0L) + Math.max(0, record.durationSec));
        }
        return result;
    }

    private void configureBarChart(BarChart chart, List<LocalDate> days,
                                   Map<String, Long> subjectTotals,
                                   LinearLayout detail) {
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        if (range.equals("day")) {
            GestureDetector detector = new GestureDetector(requireContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onFling(MotionEvent first, MotionEvent second,
                                               float velocityX, float velocityY) {
                            float dx = second.getX() - first.getX();
                            if (Math.abs(dx) > Ui.dp(requireContext(), 45)
                                    && Math.abs(velocityX) > Math.abs(velocityY)) {
                                shiftDay(dx < 0 ? -1 : 1);
                                return true;
                            }
                            return false;
                        }
                    });
            chart.setOnTouchListener((view, event) -> {
                detector.onTouchEvent(event);
                return false;
            });
        }
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setTextColor(Ui.muted(requireContext()));
        chart.getAxisLeft().setGridColor(Ui.border(requireContext()));
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setGranularity(5f);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setTextColor(Ui.muted(requireContext()));
        chart.getXAxis().setGranularity(range.equals("month") ? 5f : 1f);
        chart.getXAxis().setLabelCount(
                range.equals("month") ? 6 : Math.max(1, days.size()), false
        );
        chart.setNoDataTextColor(Ui.muted(requireContext()));
        chart.setNoDataText("当前区间还没有学习记录");

        List<String> names = new ArrayList<>(subjectTotals.keySet());
        if (names.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }
        List<BarEntry> entries = new ArrayList<>();
        int stackCount = names.size();
        for (int x = 0; x < days.size(); x++) {
            LocalDate day = days.get(x);
            float[] values = new float[stackCount];
            for (StudyRecord record : loaded.records) {
                LocalDate recordDate = DateUtils.parseRecordDate(record);
                if (!day.equals(recordDate)) continue;
                String name = record.subjectName == null || record.subjectName.isEmpty()
                        ? "其他" : record.subjectName;
                int index = names.indexOf(name);
                if (index >= 0) values[index] += record.durationSec / 60f;
            }
            entries.add(new BarEntry(x, values));
        }
        BarDataSet set = new BarDataSet(entries, "分钟");
        int[] colors = new int[stackCount];
        for (int i = 0; i < stackCount; i++) colors[i] = Ui.chartColor(i);
        set.setColors(colors);
        set.setDrawValues(false);
        BarData data = new BarData(set);
        data.setBarWidth(0.62f);
        chart.setData(data);
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry entry, Highlight highlight) {
                int dayIndex = Math.round(entry.getX());
                int stackIndex = highlight.getStackIndex();
                if (dayIndex < 0 || dayIndex >= days.size()
                        || stackIndex < 0 || stackIndex >= names.size()) return;
                showDaySubjectDetail(detail, days.get(dayIndex), names.get(stackIndex));
            }

            @Override
            public void onNothingSelected() {
                // Keep the last selection visible while the chart is being inspected.
            }
        });
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int index = Math.round(value);
                if (index < 0 || index >= days.size()) return "";
                return range.equals("day")
                        ? String.valueOf(days.get(index).getDayOfMonth())
                        : DateUtils.weekdayShort(days.get(index));
            }
        });
        chart.setVisibleXRangeMaximum(Math.max(1, days.size()));
        chart.moveViewToX(0f);
        chart.animateY(350);
        chart.invalidate();
    }

    private View barLegend(List<LocalDate> days, Map<String, Long> totals,
                           LinearLayout detail) {
        LinearLayout wrap = Ui.vertical(requireContext());
        wrap.setPadding(0, Ui.dp(requireContext(), 8), 0, 0);
        int index = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            LinearLayout row = Ui.horizontal(requireContext());
            row.setPadding(Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 7),
                    Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 7));
            View swatch = new View(requireContext());
            swatch.setBackground(Ui.rounded(requireContext(), Ui.chartColor(index++), 4));
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    Ui.dp(requireContext(), 9), Ui.dp(requireContext(), 9)
            );
            swatchParams.rightMargin = Ui.dp(requireContext(), 8);
            row.addView(swatch, swatchParams);
            TextView label = Ui.text(requireContext(),
                    entry.getKey(), 12, Ui.textColor(requireContext()), false);
            Ui.setWeight(label, 1);
            TextView duration = Ui.caption(requireContext(),
                    DateUtils.durationHuman(entry.getValue()) + "  ›");
            row.addView(label);
            row.addView(duration);
            row.setOnClickListener(view -> showSubjectDetail(detail, entry.getKey(), entry.getValue()));
            wrap.addView(row);
        }
        return wrap;
    }

    private void showDaySubjectDetail(LinearLayout detail, LocalDate day, String subject) {
        detail.removeAllViews();
        detail.setVisibility(View.VISIBLE);
        long total = 0;
        List<StudyRecord> matching = new ArrayList<>();
        for (StudyRecord record : loaded.records) {
            String recordSubject = record.subjectName == null || record.subjectName.isEmpty()
                    ? "其他" : record.subjectName;
            if (day.equals(DateUtils.parseRecordDate(record))
                    && subject.equals(recordSubject)) {
                total += Math.max(0, record.durationSec);
                matching.add(record);
            }
        }
        TextView title = Ui.text(requireContext(),
                day.getMonthValue() + "月" + day.getDayOfMonth() + "日 · " + subject,
                13, Ui.textColor(requireContext()), true);
        TextView totalView = Ui.caption(requireContext(),
                "当日合计 " + DateUtils.durationHuman(total));
        totalView.setPadding(0, Ui.dp(requireContext(), 3), 0, 0);
        detail.addView(title);
        detail.addView(totalView);
        matching.sort(Comparator.comparing(record -> record.startTime));
        int shown = 0;
        for (StudyRecord record : matching) {
            if (shown++ >= 4) break;
            TextView line = Ui.caption(requireContext(),
                    displayRecordRange(record) + " · " + DateUtils.durationHuman(record.durationSec));
            line.setPadding(0, Ui.dp(requireContext(), 6), 0, 0);
            detail.addView(line);
        }
        if (matching.isEmpty()) {
            detail.addView(Ui.caption(requireContext(), "该色块暂无可展开的记录"));
        }
    }

    private void showSubjectDetail(LinearLayout detail, String subject, long seconds) {
        detail.removeAllViews();
        detail.setVisibility(View.VISIBLE);
        detail.addView(Ui.text(requireContext(), subject, 13,
                Ui.textColor(requireContext()), true));
        TextView summary = Ui.caption(requireContext(),
                "当前区间共 " + DateUtils.durationHuman(seconds));
        summary.setPadding(0, Ui.dp(requireContext(), 4), 0, 0);
        detail.addView(summary);
        int count = 0;
        for (StudyRecord record : loaded.records) {
            String recordSubject = record.subjectName == null || record.subjectName.isEmpty()
                    ? "其他" : record.subjectName;
            if (subject.equals(recordSubject)
                    && DateUtils.parseRecordDate(record) != null
                    && rangeDays().contains(DateUtils.parseRecordDate(record))) {
                count++;
            }
        }
        TextView countView = Ui.caption(requireContext(),
                "共 " + count + " 次学习记录，点击柱子可查看当天明细");
        countView.setPadding(0, Ui.dp(requireContext(), 4), 0, 0);
        detail.addView(countView);
    }

    private String displayRecordRange(StudyRecord record) {
        String start = record.startTime == null ? "" : record.startTime;
        String end = record.endTime == null ? "" : record.endTime;
        if (start.length() >= 16) start = start.substring(11, 16);
        if (end.length() >= 16) end = end.substring(Math.max(0, end.length() - 5));
        return start + "–" + end;
    }

    private void configurePieChart(PieChart chart, Map<String, Long> totals) {
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setTouchEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setUsePercentValues(true);
        chart.setDrawEntryLabels(false);
        chart.setNoDataTextColor(Ui.muted(requireContext()));
        chart.setNoDataText("当前区间还没有学习记录");
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(62f);
        chart.setCenterTextColor(Ui.textColor(requireContext()));
        chart.setCenterTextSize(17f);
        long total = 0;
        for (long value : totals.values()) total += value;
        chart.setCenterText(DateUtils.durationHuman(total));
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (totals.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }
        int index = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            if (entry.getValue() <= 0) continue;
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            colors.add(Ui.chartColor(index++));
        }
        PieDataSet set = new PieDataSet(entries, "科目");
        set.setColors(colors);
        set.setSliceSpace(2f);
        set.setDrawValues(false);
        PieData data = new PieData(set);
        chart.setData(data);
        chart.animateY(360);
        chart.invalidate();
    }

    private View legend(Map<String, Long> totals) {
        LinearLayout wrap = Ui.vertical(requireContext());
        int index = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            LinearLayout row = Ui.horizontal(requireContext());
            View swatch = new View(requireContext());
            swatch.setBackground(Ui.rounded(requireContext(), Ui.chartColor(index++), 4));
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 8)
            );
            swatchParams.rightMargin = Ui.dp(requireContext(), 7);
            row.addView(swatch, swatchParams);
            TextView label = Ui.caption(requireContext(),
                    entry.getKey() + " · " + DateUtils.durationHuman(entry.getValue()));
            row.addView(label);
            wrap.addView(row);
        }
        return wrap;
    }

    private View progressSection() {
        LinearLayout card = Ui.surface(requireContext());
        card.addView(Ui.sectionTitle(requireContext(), "科目完成总览"));
        if (loaded.subjects.isEmpty()) {
            card.addView(Ui.caption(requireContext(), "还没有科目目录"));
            return card;
        }
        for (Subject subject : loaded.subjects) {
            int total = 0;
            int finished = 0;
            for (Section section : loaded.sections) {
                if (section.subjectId != null && section.subjectId == subject.id
                        && section.chapterId != null) {
                    total++;
                    if (section.isFinished) finished++;
                }
            }
            LinearLayout row = Ui.horizontal(requireContext());
            TextView name = Ui.text(requireContext(), subject.name, 13,
                    Ui.textColor(requireContext()), true);
            TextView value = Ui.caption(requireContext(), finished + "/" + total);
            Ui.setWeight(name, 1);
            row.addView(name);
            row.addView(value);
            row.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 3));
            card.addView(row);
            card.addView(Ui.progress(requireContext(), total, finished, Ui.accent(requireContext())));
        }
        return card;
    }

    private View weeklyReport() {
        LinearLayout card = Ui.surface(requireContext());
        LocalDate today = LocalDate.now();
        LocalDate start = DateUtils.startOfWeek(today);
        LocalDate end = start.plusDays(6);
        long seconds = DateUtils.totalForRange(loaded.records, start, end);
        int nextWeekSections = 0;
        for (Section section : loaded.sections) {
            if (!section.isFinished && DateUtils.isValidDate(section.planDate)) {
                LocalDate day = LocalDate.parse(section.planDate);
                if (!day.isBefore(end.plusDays(1)) && !day.isAfter(end.plusDays(7))) {
                    nextWeekSections++;
                }
            }
        }
        TextView label = Ui.sectionTitle(requireContext(), "本周学习周报");
        TextView date = Ui.caption(requireContext(),
                start.getMonthValue() + "月" + start.getDayOfMonth() + "日 - "
                        + end.getMonthValue() + "月" + end.getDayOfMonth() + "日");
        date.setPadding(0, Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 10));
        card.addView(label);
        card.addView(date);
        addReportLine(card, "本周总学习", DateUtils.durationHuman(seconds));
        int elapsedDays = Math.max(1, today.getDayOfWeek().getValue());
        addReportLine(card, "日均学习", DateUtils.durationHuman(seconds / elapsedDays));
        addReportLine(card, "连续学习", DateUtils.consecutiveDays(loaded.records) + " 天");
        addReportLine(card, "下周已排任务", nextWeekSections + " 项");

        Map<String, Long> bySubject = new HashMap<>();
        for (StudyRecord record : loaded.records) {
            LocalDate day = DateUtils.parseRecordDate(record);
            if (day != null && !day.isBefore(start) && !day.isAfter(end)) {
                bySubject.put(record.subjectName,
                        bySubject.getOrDefault(record.subjectName, 0L) + record.durationSec);
            }
        }
        if (!bySubject.isEmpty()) {
            StringBuilder distribution = new StringBuilder();
            for (Map.Entry<String, Long> entry : bySubject.entrySet()) {
                if (distribution.length() > 0) distribution.append(" · ");
                distribution.append(entry.getKey()).append(" ")
                        .append(DateUtils.durationHuman(entry.getValue()));
            }
            TextView text = Ui.caption(requireContext(), "科目分布：" + distribution);
            text.setPadding(0, Ui.dp(requireContext(), 9), 0, 0);
            card.addView(text);
        }
        return card;
    }

    private void addReportLine(LinearLayout card, String label, String value) {
        LinearLayout row = Ui.horizontal(requireContext());
        TextView labelView = Ui.caption(requireContext(), label);
        TextView valueView = Ui.text(requireContext(), value, 12,
                Ui.textColor(requireContext()), true);
        Ui.setWeight(labelView, 1);
        row.addView(labelView);
        row.addView(valueView);
        row.setPadding(0, Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 4));
        card.addView(row);
    }

    private View recordSection() {
        LinearLayout card = Ui.surface(requireContext());
        LinearLayout header = Ui.horizontal(requireContext());
        TextView title = Ui.sectionTitle(requireContext(), "学习记录");
        Ui.setWeight(title, 1);
        TextView add = Ui.text(requireContext(), "补记", 12,
                Ui.accent(requireContext()), true);
        add.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 6), 0, Ui.dp(requireContext(), 6));
        add.setOnClickListener(view -> addRecord(null));
        header.addView(title);
        header.addView(add);
        card.addView(header);

        if (loaded.records.isEmpty()) {
            TextView empty = Ui.caption(requireContext(),
                    "还没有学习记录。完成一次计时或点击补记。");
            empty.setPadding(0, Ui.dp(requireContext(), 16), 0, Ui.dp(requireContext(), 6));
            card.addView(empty);
            return card;
        }
        int shown = 0;
        for (StudyRecord record : loaded.records) {
            if (shown++ >= 20) break;
            View row = recordRow(record, shown == 20);
            card.addView(row);
            View divider = new View(requireContext());
            divider.setBackgroundColor(Ui.border(requireContext()));
            card.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
            ));
        }
        return card;
    }

    private View recordRow(StudyRecord record, boolean last) {
        LinearLayout row = Ui.horizontal(requireContext());
        row.setPadding(0, Ui.dp(requireContext(), 10), 0, Ui.dp(requireContext(), 10));
        LinearLayout text = Ui.vertical(requireContext());
        text.addView(Ui.text(requireContext(),
                record.subjectName == null ? "其他" : record.subjectName,
                13, Ui.textColor(requireContext()), true));
        text.addView(Ui.caption(requireContext(),
                (record.startTime == null ? "" : record.startTime)
                        + " · " + DateUtils.durationHuman(record.durationSec)));
        Ui.setWeight(text, 1);
        TextView edit = Ui.text(requireContext(), "编辑", 11,
                Ui.accent(requireContext()), true);
        edit.setPadding(Ui.dp(requireContext(), 7), Ui.dp(requireContext(), 6), 0, Ui.dp(requireContext(), 6));
        edit.setOnClickListener(view -> addRecord(record));
        TextView delete = Ui.text(requireContext(), "删除", 11,
                Ui.danger(requireContext()), true);
        delete.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 6), 0, Ui.dp(requireContext(), 6));
        delete.setOnClickListener(view -> new AlertDialog.Builder(requireContext())
                .setTitle("删除学习记录？")
                .setMessage("统计时长会同步减少。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                        repository.io(() -> repository.dao().deleteRecord(record), this::refresh))
                .show());
        row.addView(text);
        row.addView(edit);
        row.addView(delete);
        return row;
    }

    private void addRecord(StudyRecord existing) {
        Dialogs.recordForm(requireContext(), existing, loaded.subjects, record -> {
            if (existing == null) {
                repository.io(() -> repository.dao().insertRecord(record), this::refresh);
            } else {
                repository.io(() -> repository.dao().updateRecord(record), this::refresh);
            }
        });
    }

    private long totalSeconds() {
        long total = 0;
        for (StudyRecord record : loaded.records) {
            total += Math.max(0, record.durationSec);
        }
        return total;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        content = null;
        chartContainer = null;
        super.onDestroyView();
    }

    private static class Loaded {
        List<Subject> subjects = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        List<StudyRecord> records = new ArrayList<>();
        List<RepeatTask> repeats = new ArrayList<>();
    }
}
