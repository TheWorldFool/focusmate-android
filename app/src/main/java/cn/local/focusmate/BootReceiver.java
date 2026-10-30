package cn.local.focusmate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    Store store = Store.get(context);
    String action = intent.getAction();
    if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
      JSONObject timer = store.timer();
      if (timer != null) {
        long elapsed = store.elapsed(timer);
        boolean countdown = "countdown".equals(timer.optString("mode"));
        Store.put(timer, "accumulated", Math.min(elapsed, countdown ? timer.optLong("target") : elapsed));
        Store.put(timer, "paused", true);
        store.save();
      }
    }
    if (store.lockExpired()) store.clearLock();
    if (store.lockActive()) {
      try {
        context.startForegroundService(new Intent(context, LockService.class).setAction(LockService.REFRESH));
      } catch (Exception ignored) { }
    }
    ReminderReceiver.schedule(context);
    ScheduledLock.schedule(context);
    FocusWidget.refresh(context);
  }
}
