package cn.local.focusmate;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public class ReminderReceiver extends BroadcastReceiver {
  private static final int MAX_REMINDERS = 8;

  private static int requestCode(String taskId, int index) {
    return (taskId.hashCode() & 0x7fffffff) % 1000000 * MAX_REMINDERS + index;
  }

  private static PendingIntent pendingFor(Context c, String taskId, int index) {
    Intent intent = new Intent(c, ReminderReceiver.class)
        .setAction("reminder:" + taskId + ":" + index)
        .putExtra("taskId", taskId)
        .putExtra("reminderIndex", index);
    return PendingIntent.getBroadcast(c, requestCode(taskId, index), intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static void schedule(Context c) {
    Store store = Store.get(c);
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    for (int i = 0; i < store.tasks().length(); i++) {
      JSONObject task = store.tasks().optJSONObject(i);
      if (task == null) continue;
      String id = task.optString("id");
      JSONArray times = Store.reminders(task);
      for (int index = 0; index < MAX_REMINDERS; index++) {
        alarms.cancel(pendingFor(c, id, index));
      }
      if (task.optBoolean("archived")) continue;
      for (int index = 0; index < times.length() && index < MAX_REMINDERS; index++) {
        String reminder = times.optString(index);
        if (reminder.isEmpty()) continue;
        PendingIntent pending = pendingFor(c, id, index);
        try {
          LocalTime time = LocalTime.parse(reminder);
          ZonedDateTime now = ZonedDateTime.now();
          for (int offset = 0; offset < 370; offset++) {
            LocalDate day = now.toLocalDate().plusDays(offset);
            if (!store.due(task, day.toString())) continue;
            ZonedDateTime at = day.atTime(time).atZone(now.getZone());
            if (!at.isAfter(now)) continue;
            long millis = at.toInstant().toEpochMilli();
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
              alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            } else {
              alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            }
            break;
          }
        } catch (Exception ignored) { }
      }
    }
  }

  @Override public void onReceive(Context c, Intent intent) {
    Store store = Store.get(c);
    String taskId = intent.getStringExtra("taskId");
    int index = intent.getIntExtra("reminderIndex", 0);
    JSONObject task = store.find(taskId);
    NotificationManager nm = c.getSystemService(NotificationManager.class);
    if (task == null) {
      schedule(c);
      return;
    }
    if ("mark_done".equals(intent.getAction())) {
      store.setDone(task, Store.today(), true);
      cancelTaskNotifications(nm, taskId);
      FocusWidget.refresh(c);
      schedule(c);
      return;
    }
    if (task.optBoolean("archived") || store.done(task, Store.today())) {
      cancelTaskNotifications(nm, taskId);
      schedule(c);
      return;
    }

    boolean autoStarted = false;
    if (task.optBoolean("timedStrict") && store.timer() == null && !store.lockActive()) {
      autoStarted = startTimedFocus(c, task);
    }

    nm.createNotificationChannel(new NotificationChannel(
        "reminder", "待办提醒", NotificationManager.IMPORTANCE_HIGH));
    Intent open = new Intent(c, MainActivity.class).putExtra("openTasks", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent content = PendingIntent.getActivity(c, requestCode(taskId, index), open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent done = PendingIntent.getBroadcast(c, requestCode(taskId, index) + MAX_REMINDERS,
        new Intent(c, ReminderReceiver.class).setAction("mark_done").putExtra("taskId", taskId),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    String body = task.optString("title");
    if (autoStarted) body += " · 已自动开始严格专注";
    Notification.Builder b = new Notification.Builder(c, "reminder")
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(autoStarted ? "定时学霸已开始" : "待办提醒")
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(content)
        .setColor(store.settings().optInt("accent", Store.DEFAULT_ACCENT))
        .addAction(new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notify),
            "标记完成", done).build());
    nm.notify(requestCode(taskId, index) + 1, b.build());
    schedule(c);
  }

  private void cancelTaskNotifications(NotificationManager nm, String taskId) {
    for (int index = 0; index < MAX_REMINDERS; index++) {
      nm.cancel(requestCode(taskId, index) + 1);
    }
  }

  private boolean startTimedFocus(Context c, JSONObject task) {
    Intent start = new Intent(c, FocusService.class)
        .setAction(FocusService.START)
        .putExtra("taskId", task.optString("id"))
        .putExtra("mode", task.optString("mode", "countdown"))
        .putExtra("minutes", task.optInt("minutes", 25))
        .putExtra("rest", task.optInt("restMinutes", 5))
        .putExtra("title", task.optString("title"))
        .putExtra("group", task.optString("group", "未分组"))
        .putExtra("strict", true)
        .putExtra("forceGuard", true);
    try {
      c.startForegroundService(start);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
