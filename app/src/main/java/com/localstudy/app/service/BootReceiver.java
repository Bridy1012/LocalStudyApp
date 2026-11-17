package com.localstudy.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.schedule(context);
            PendingResult result = goAsync();
            final int[] remaining = {2};
            Runnable finish = () -> {
                if (--remaining[0] == 0) result.finish();
            };
            CalendarReminderScheduler.rescheduleAll(context, finish);
            CourseReminderScheduler.rescheduleAll(context, finish);
        }
    }
}
