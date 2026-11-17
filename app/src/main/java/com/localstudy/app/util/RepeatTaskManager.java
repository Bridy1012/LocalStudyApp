package com.localstudy.app.util;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.localstudy.app.data.RepeatTask;
import com.localstudy.app.data.Subject;

import java.util.List;

public final class RepeatTaskManager {
    private RepeatTaskManager() {
    }

    public interface Actions {
        void onAdd();
        void onEdit(RepeatTask task);
        void onDelete(RepeatTask task);
    }

    public static void show(Context context, List<RepeatTask> tasks,
                            List<Subject> subjects, Actions actions) {
        LinearLayout body = Ui.vertical(context);
        body.setPadding(Ui.dp(context, 20), Ui.dp(context, 2),
                Ui.dp(context, 20), Ui.dp(context, 9));
        TextView summary = Ui.text(context,
                tasks.isEmpty() ? "还没有重复任务" : "共 " + tasks.size() + " 个重复习惯",
                13, Ui.muted(context), false);
        summary.setPadding(0, 0, 0, Ui.dp(context, 12));
        body.addView(summary);

        if (tasks.isEmpty()) {
            LinearLayout empty = Ui.surface(context);
            TextView title = Ui.text(context, "建立第一个重复习惯", 15,
                    Ui.textColor(context), true);
            TextView detail = Ui.caption(context,
                    "重复任务只在设定的日期出现，不会累积成逾期欠债。");
            detail.setPadding(0, Ui.dp(context, 5), 0, 0);
            empty.addView(title);
            empty.addView(detail);
            body.addView(empty);
        }

        for (RepeatTask task : tasks) {
            body.addView(taskCard(context, task, subjects, actions));
            body.addView(Ui.space(context, 10));
        }

        Button add = Ui.button(context, "新增重复任务", true);
        add.setOnClickListener(view -> {
            DialogHolder holder = DialogHolder.current;
            if (holder != null) holder.dialog.dismiss();
            actions.onAdd();
        });
        body.addView(add);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("重复任务管理")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();
        DialogHolder.current = new DialogHolder(dialog);
        dialog.show();
    }

    private static View taskCard(Context context, RepeatTask task,
                                 List<Subject> subjects, Actions actions) {
        LinearLayout card = Ui.surface(context);
        card.setPadding(Ui.dp(context, 16), Ui.dp(context, 14),
                Ui.dp(context, 14), Ui.dp(context, 12));
        LinearLayout titleRow = Ui.horizontal(context);
        TextView title = Ui.text(context, task.title, 16,
                Ui.textColor(context), true);
        Ui.setWeight(title, 1);
        TextView frequency = Ui.pill(context, shortRule(task), Ui.accent(context));
        titleRow.addView(title);
        titleRow.addView(frequency);
        card.addView(titleRow);

        TextView detail = Ui.caption(context,
                subjectName(task, subjects) + " · " + fullRule(task));
        detail.setPadding(0, Ui.dp(context, 7), 0, Ui.dp(context, 10));
        card.addView(detail);

        View divider = new View(context);
        divider.setBackgroundColor(Ui.border(context));
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
        ));

        LinearLayout actionsRow = Ui.horizontal(context);
        actionsRow.setGravity(Gravity.END);
        actionsRow.setPadding(0, Ui.dp(context, 9), 0, 0);
        TextView edit = action(context, "编辑", Ui.accent(context));
        edit.setOnClickListener(view -> {
            DialogHolder holder = DialogHolder.current;
            if (holder != null) holder.dialog.dismiss();
            actions.onEdit(task);
        });
        TextView delete = action(context, "删除", Ui.danger(context));
        delete.setOnClickListener(view -> {
            DialogHolder holder = DialogHolder.current;
            if (holder != null) holder.dialog.dismiss();
            actions.onDelete(task);
        });
        actionsRow.addView(edit);
        actionsRow.addView(delete);
        card.addView(actionsRow);
        card.setOnLongClickListener(view -> {
            DialogHolder holder = DialogHolder.current;
            if (holder != null) holder.dialog.dismiss();
            actions.onEdit(task);
            return true;
        });
        return card;
    }

    private static TextView action(Context context, String text, int color) {
        TextView view = Ui.text(context, text, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(context, 12), Ui.dp(context, 7),
                Ui.dp(context, 12), Ui.dp(context, 7));
        view.setBackground(Ui.rounded(context, Ui.withAlpha(color, 18), 7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = Ui.dp(context, 8);
        view.setLayoutParams(params);
        return view;
    }

    private static String shortRule(RepeatTask task) {
        switch (task.repeatType) {
            case "weekly":
                return "每周";
            case "monthly":
                return "每月";
            default:
                return "每天";
        }
    }

    private static String fullRule(RepeatTask task) {
        switch (task.repeatType) {
            case "weekly":
                return "每周 " + task.repeatValue.replace(",", "、");
            case "monthly":
                return "每月 " + task.repeatValue.replace(",", "、") + " 日";
            default:
                return "每天";
        }
    }

    private static String subjectName(RepeatTask task, List<Subject> subjects) {
        if (task.subjectId == null) return "其他";
        for (Subject subject : subjects) {
            if (subject.id == task.subjectId) return subject.name;
        }
        return "其他";
    }

    private static class DialogHolder {
        static DialogHolder current;
        final AlertDialog dialog;

        DialogHolder(AlertDialog dialog) {
            this.dialog = dialog;
        }
    }
}
