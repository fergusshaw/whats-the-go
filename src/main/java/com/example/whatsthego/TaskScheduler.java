package com.example.whatsthego;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class TaskScheduler {
    public static void scheduleTaskReminder(Context context, int taskId, long deadlineUnix, long minutesBefore) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra("taskId", taskId);
        intent.putExtra("timeLeft", minutesBefore);

        int requestCode = taskId + ((int)minutesBefore * 420);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime = deadlineUnix - (minutesBefore * 60 * 1000);
        Log.println(Log.DEBUG, "BINGBONG", " minutesBefore is " + minutesBefore + " task id is " + taskId);
        Log.println(Log.DEBUG, "BINGBONG", "alarm set for " + MainActivity.unixToDate(triggerTime));

        // Schedule the exact alarm
        if (alarmManager.canScheduleExactAlarms()){
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        else{
            Log.e("TaskScheduler", "can't schedule exact alarms");
        }
    }
}
