package com.example.whatsthego;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import android.Manifest;

public class MainActivity extends AppCompatActivity {
    static long selectedTimestamp = -1;
    static Map<String, Long> uncompletedTasks = new HashMap<>();
    static Map<Integer, String> idToTask = new HashMap<>();
    static Map<String, Integer> taskToId = new HashMap<>();
    static Map<String, Long> completedTasks = new HashMap<>();
    ArrayAdapter<String> adapter;
    static String CHANNEL_ID = "cool_as_channel";
    static int TASK_ID = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setUp();
    }

    /**
     * INITIALIZATION/SETUP STUFF
     */

    void setUp(){


        //set buttons to relevant actions
        TextView displaySelection = findViewById(R.id.selectedText);

        Button pickTimeButton = findViewById(R.id.pickTime);
        pickTimeButton.setOnClickListener(v -> {
            TimePickerFragment timePicker = new TimePickerFragment(displaySelection);
            timePicker.show(getSupportFragmentManager(), "timePicker");
        });

        Button pickDateButton = findViewById(R.id.pickDate);
        pickDateButton.setOnClickListener(v -> {
            DatePickerFragment datePicker = new DatePickerFragment(displaySelection);
            datePicker.show(getSupportFragmentManager(), "datePicker");
        });

        Button noDeadline = findViewById(R.id.noDeadline);
        noDeadline.setOnClickListener(v -> {
            updateSelectedText(displaySelection);
                });

        Button submit = findViewById(R.id.submitButton);
        submit.setOnClickListener(v ->{
            submitTask();
        });

        Button viewCompleted = findViewById(R.id.viewCompleted);
        viewCompleted.setOnClickListener(v -> {
            if (viewCompleted.getText().equals("View completed tasks")){
                viewCompleted();
            }
            else{
                updateListView();
            }
        });

        //call setup methods (to set things up)
        initializeTasks(this);
        createNotificationChannel();
        setUpList();
        checkAndRequestPermissions();
    }

    void setUpList(){
        ListView list = findViewById(R.id.mainListView);
        ArrayList<String> taskList = new ArrayList<>();
        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                taskList
        ){

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent){
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                //sort and display completed/uncompleted task lists
                Button viewCompletedButton = findViewById(R.id.viewCompleted);
                if (viewCompletedButton.getText().equals("View completed tasks")){
                    ArrayList<String> taskList = new ArrayList<>(uncompletedTasks.keySet());
                    taskList.sort((a, b) -> compareDates(a, b, false));
                    text1.setText(taskList.get(position));
                    text2.setText(unixToDate(uncompletedTasks.get(taskList.get(position))));
                }
                else{
                    ArrayList<String> taskList = new ArrayList<>(completedTasks.keySet());
                    taskList.sort((a,b) -> compareDates(a, b, true));
                    text1.setText(taskList.get(position));
                    text2.setText(unixToDate(completedTasks.get(taskList.get(position))));
                }

                return view;
            }
        };
        list.setAdapter(adapter);

        registerForContextMenu(list);

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id){
                Button viewCompletedButton = findViewById(R.id.viewCompleted);
                if (viewCompletedButton.getText().equals("View uncompleted tasks")){ return false; }

                PopupMenu popup = new PopupMenu(MainActivity.this, view);
                popup.getMenuInflater().inflate(R.menu.list_item_menu, popup.getMenu());

                taskList.sort((a,b) -> compareDates(a, b, false));

                //set delete task and mark completed buttons
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.menu_complete){
                        removeTask(taskToId.get(taskList.get(position)), true);
                        return true;
                    }
                    else if (item.getItemId() == R.id.menu_delete){
                        removeTask(taskToId.get(taskList.get(position)), false);
                        return true;
                    }
                    else{
                        return false;
                    }
                });
                popup.show();
                return true;
            }
        });
        updateListView();
    }

    /**
     * END INITIALIZATION/SETUP STUFF
     */


    /**
     * NOTIFICATION STUFF
     */

    static void generateNotification(Context context, int task_id, String titleString, String descString, boolean nonDismissable){

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        //build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_interrobang)
                .setContentTitle(titleString)
                .setContentText(descString)
                .setPriority(nonDismissable ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(nonDismissable);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (ActivityCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            Log.w("Notifications", "Permission not granted for notifications");
            return;
        }
        notificationManager.notify(task_id, builder.build());
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    //make sure notification and alarm permissions are assigned
    private void checkAndRequestPermissions() {
        if (!notificationsPermissionGranted()) {
            requestNotificationPermission();
        }

        if (!alarmPermissionGranted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Alarm Permission Required")
                    .setMessage("To send you reminders, this app needs the set exact alarms permission.")
                    .setPositiveButton("Grant", (dialog, which) -> requestAlarmPermission())
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        }
    }

    private boolean notificationsPermissionGranted() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                420
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 420) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Notification Permission Required")
                        .setMessage("This app needs notification permissions (otherwise it's just a list)")
                        .setPositiveButton("Grant", (dialog, which) -> requestNotificationPermission())
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            }
        }
    }

    private boolean alarmPermissionGranted() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return alarmManager.canScheduleExactAlarms();
    }

    private void requestAlarmPermission() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        startActivity(intent);
    }

    /**
     * END NOTIFICATION STUFF
     */

    /**
     * TASK UTILITY METHODS
     */

    static void initializeTasks(Context context){
        File tasksFile = new File(context.getFilesDir(), "tasks.txt");
        if (!tasksFile.exists()){ return; }

        String all = readFromFile("tasks.txt", context);
        if (all.length() < 2){ return; }

        //if tasks already initialized, return
        if (!uncompletedTasks.isEmpty()){ return; }

        //parse text from task file into maps
        String[] tasksDeadlines = all.split("\n");
        for (String taskDeadline : tasksDeadlines) {
            if (taskDeadline.trim().isEmpty()){ continue; }
            String[] thisTask = taskDeadline.split("~~~");
            uncompletedTasks.put(thisTask[0], Long.valueOf(thisTask[1]));
            idToTask.put(Integer.valueOf(thisTask[2]), thisTask[0]);
            taskToId.put(thisTask[0], Integer.valueOf(thisTask[2]));
        }
        TASK_ID = taskToId.values().stream().toList().get(taskToId.values().size() - 1) + 1; //next id will be last one + 1
    }



    void createTask(String task, long deadline){
        uncompletedTasks.put(task, deadline);
        idToTask.put(TASK_ID, task);
        taskToId.put(task, TASK_ID);
        generateNotification(this, TASK_ID, task + " - " + unixToDate(deadline), "", true);

        saveToFile(task + "~~~" + deadline + "~~~" + TASK_ID + "\n", "tasks.txt");
        Toast.makeText(this, "Task saved", Toast.LENGTH_SHORT).show();
        updateListView();

        scheduleReminders(TASK_ID);

        TASK_ID++;
    }

    void scheduleReminders(int taskId){
        long timeUntilDeadline = howMuchTimeLeft(taskId);

        List<Integer> reminderTimes =  Stream.of(15, 30, 60, 120, 240, 480, 1440, 2880, 4320).filter(time -> time < timeUntilDeadline).toList();
        long deadline = uncompletedTasks.get(idToTask.get(taskId));
        reminderTimes.forEach(time -> {
            TaskScheduler.scheduleTaskReminder(this, taskId, deadline, time);
            Log.d("BINGBONG", "scheduling reminder with time left: " + time + " and task id " + taskId);
        });
    }

    void submitTask(){
        EditText firstInput = findViewById(R.id.firstInput);
        String userInput = firstInput.getText().toString();
        firstInput.setText("");

        if (!userInput.isEmpty()){createTask(userInput, selectedTimestamp);}
        selectedTimestamp = -1; //reset timestamp after task saved
    }

    void removeTask(int taskId, boolean save){
        File originalFile = new File(getFilesDir(), "tasks.txt");
        File tempFile = new File(getFilesDir(), "temp.txt");

        try (FileOutputStream fos = new FileOutputStream(tempFile)){
            String[] lines = readFromFile("tasks.txt", this).split("\n");
            //write every task except task to remove to temp file
            for (String line : lines){
                if (line.endsWith(""+taskId)){
                    continue;
                }
                String toWrite = line + "\n";
                fos.write(toWrite.getBytes());
            }
            if (!originalFile.delete() || !tempFile.renameTo(originalFile)) {
                Log.e("MainActivity", "File rename failed");
            }
        } catch (IOException e){
            Log.e("MainActivity", "File write failed", e);
        }

        String task = idToTask.get(taskId);
        Log.d("BINGBONG", "removing task, id " + taskId + " and task is " + task);
        long deadline = uncompletedTasks.get(task);

        if (save){
            saveToFile( task + "~~~" + deadline + "\n", "completedTasks.txt");
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        //cancel original notification, as well as all possible reminders
        notificationManager.cancel(taskId);
        Stream.of(15, 30, 60, 120, 240, 480, 1440, 2880, 4320).forEach(time -> {
            notificationManager.cancel((taskId + 420) * time);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, TaskReminderReceiver.class);
            int requestCode = taskId + (time * 420);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);
        });

        uncompletedTasks.remove(task);
        completedTasks.put(task, deadline);
        updateListView();
    }
    void viewCompleted(){
        File completed = new File(getFilesDir(), "completedTasks.txt");
        if (!completed.exists()){
            Toast.makeText(this, "No completed tasks", Toast.LENGTH_LONG).show();
            return;
        }
        String[] completedTasksDeadlines = readFromFile("completedTasks.txt", this).split("\n");
        for (String completedTask : completedTasksDeadlines){
            String[] thisTask = completedTask.split("~~~");
            completedTasks.put(thisTask[0], Long.valueOf(thisTask[1]));
        }
        adapter.clear();
        adapter.addAll(completedTasks.keySet());
        adapter.notifyDataSetChanged();
        Button viewCompletedButton = findViewById(R.id.viewCompleted);
        viewCompletedButton.setText("View uncompleted tasks");
    }

    void updateListView(){
        adapter.clear();
        adapter.addAll(uncompletedTasks.keySet());
        adapter.notifyDataSetChanged();
        Button viewCompletedButton = findViewById(R.id.viewCompleted);
        viewCompletedButton.setText("View completed tasks");
    }

    static long howMuchTimeLeft(int task_id){
        long currentTime = System.currentTimeMillis() / 1000L;

        long deadline = uncompletedTasks.get(idToTask.get(task_id)) / 1000L;
        long timeLeftSeconds = deadline - currentTime;
        long timeLeftMinutes = (timeLeftSeconds / 60) + 1; //it's off by one normally. odd fix but works??

        Log.println(Log.DEBUG, "BINGBONG", "deadline is " + deadline + " minute == " + timeLeftMinutes);

        return timeLeftMinutes;
    }

    /**
     * END TASK UTILITY METHODS
     */

    /**
     * GENERAL UTILITY METHODS
     */

    int compareDates(String a, String b, boolean completed){
        long aDate = ((completed) ? completedTasks : uncompletedTasks).get(a);
        long bDate = ((completed) ? completedTasks : uncompletedTasks).get(b);
        if (aDate == bDate){return 0;}
        if (aDate == -1){return 1;}
        if (bDate == -1){return -1;}
        if (aDate < bDate){return -1;}
        return 1;
    }

    public static String unixToDate(long timestamp){
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm  -  dd/MM/yyyy", Locale.getDefault());
        String deadline = (timestamp == -1) ? "No deadline" : sdf.format(timestamp);
        return deadline;
    }

    void saveToFile(String output, String fileName){
        File file = new File(getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(file, true)){
            fos.write(output.getBytes());
        } catch (IOException e) {
            Log.e("MainActivity", "File write failed", e);
        }
    }

    static String readFromFile(String fileName, Context context){
        StringBuilder stringBuilder = new StringBuilder();
        File file = new File(context.getFilesDir(), fileName);

        try (FileInputStream fis = new FileInputStream(file)){
            int character;
            while ((character = fis.read()) != -1) {
                stringBuilder.append((char) character);
            }
        } catch (IOException e){
            Log.e("MainActivity", "File read failed", e);
            return "";
        }
        return stringBuilder.toString();
    }

    public static void updateSelectedText(TextView displaySelection){
        if (selectedTimestamp == -1) {
            displaySelection.setText("You've selected: No deadline");
        } else {
            Date date = new Date(selectedTimestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm  -  dd/MM/yyyy", Locale.getDefault());
            displaySelection.setText("You've selected: " + sdf.format(date));
        }
        displaySelection.setVisibility(TextView.VISIBLE);
    }

    /**
     * END GENERAL UTILITY METHODS
     */


    /**
     * TIME AND DATE STUFF
     */

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        TextView display;
        public TimePickerFragment(TextView view){
            this.display = view;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar c = Calendar.getInstance();
            if (selectedTimestamp != -1) {
                c.setTimeInMillis(selectedTimestamp);
            }
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            return new TimePickerDialog(getActivity(), this, hour, minute,
                    DateFormat.is24HourFormat(getActivity()));
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            Calendar c = Calendar.getInstance();
            if (selectedTimestamp != -1) {
                c.setTimeInMillis(selectedTimestamp);
            }
            c.set(Calendar.HOUR_OF_DAY, hourOfDay);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            selectedTimestamp = c.getTimeInMillis();
            updateSelectedText(display);
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        TextView display;
        public DatePickerFragment(TextView view){
            this.display = view;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            //use current date as default
            final Calendar c = Calendar.getInstance();
            if (selectedTimestamp != -1) {
                c.setTimeInMillis(selectedTimestamp);
            }
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            return new DatePickerDialog(requireContext(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            Calendar c = Calendar.getInstance();
            if (selectedTimestamp != -1) {
                c.setTimeInMillis(selectedTimestamp);
            }
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);
            selectedTimestamp = c.getTimeInMillis();
            updateSelectedText(display);
        }
    }

    /**
     * END TIME AND DATE STUFF
     */


}