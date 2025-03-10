package com.example.whatsthego;


import static com.example.whatsthego.MainActivity.uncompletedTasks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class TaskReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Extract task details from the intent
        int taskId = intent.getIntExtra("taskId", -1);
        if (taskId == -1){ return; }

        long timeLeft = intent.getLongExtra("timeLeft", -1);
        if (timeLeft == -1){ return; }

        Log.d("BINGBONG", "generating notification with time left: " + timeLeft + " and task id " + taskId);

        //initialize tasks before accessing map if map empty, so objects exist
        if (uncompletedTasks.isEmpty()){ MainActivity.initializeTasks(context); }
        //task id made unique as it is used for notification id
        MainActivity.generateNotification(context, (taskId + 420) * (int)timeLeft, timeToString(timeLeft) + " left - " + MainActivity.idToTask.get(taskId), "are you on track?", false);
    }

    String timeToString(long timeLeft){
        if (timeLeft == 15 || timeLeft == 30){ return timeLeft + " minutes"; }
        if (timeLeft == 60 || timeLeft == 120 || timeLeft == 240 || timeLeft == 480){ return (timeLeft / 60) + " hours"; }
        if (timeLeft == 1440){ return "1 day"; }
        if (timeLeft > 1440){ return (timeLeft / 60 / 24) + " days"; }
        return timeLeft + "";
    }
}


