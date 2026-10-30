package cn.local.focusmate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Daily start alarm for the optional scheduled lock. */
public final class ScheduledLock {
  public static final String ACTION_START = "cn.local.focusmate.SCHEDULED_LOCK_START";

  private ScheduledLock() { }

  public static void schedule(Context context) {
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;
    PendingIntent alarm = pending(context);
    am.cancel(alarm);
    JSONObject settings = Store.get(context).settings();
    if (!settings.optBoolean("lockScheduleEnabled", false)) return;
    int start;
    try {
      start = Store.parseTime(settings.optString("lockScheduleStart", "22:00"));
    } catch (Exception e) {
      return;
    }
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime next = now.toLocalDate().atTime(LocalTime.of(start / 60, start % 60))
        .atZone(now.getZone());
    if (!next.isAfter(now)) next = next.plusDays(1);
    try {
      if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm);
      } else {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm);
      }
    } catch (Exception e) {
      am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm);
    }
  }

  public static void fire(Context context) {
    Store store = Store.get(context);
    JSONObject settings = store.settings();
    if (!settings.optBoolean("lockScheduleEnabled", false)) return;
    // Keep the daily chain alive first, even if this particular occurrence is skipped.
    schedule(context);
    int start, end;
    try {
      start = Store.parseTime(settings.optString("lockScheduleStart", "22:00"));
      end = Store.parseTime(settings.optString("lockScheduleEnd", "22:30"));
    } catch (Exception e) {
      return;
    }
    if (store.lockActive()) return;
    LocalTime now = LocalTime.now();
    int nowMinute = now.getHour() * 60 + now.getMinute();
    int late = (nowMinute - start + 1440) % 1440;
    // Alarms can arrive after a reboot or doze; starting a stale lock hours later is worse than skipping.
    if (late > 10) return;
    int remaining = (end - nowMinute + 1440) % 1440;
    if (remaining == 0) remaining = 1440;
    store.startLock(Math.max(1, remaining));
    try {
      context.startForegroundService(new Intent(context, LockService.class).setAction(LockService.START));
    } catch (Exception ignored) { }
  }

  private static PendingIntent pending(Context context) {
    Intent intent = new Intent(context, ScheduledLockReceiver.class).setAction(ACTION_START);
    return PendingIntent.getBroadcast(context, 120, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
}
