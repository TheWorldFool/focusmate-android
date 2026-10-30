package cn.local.focusmate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Keeps a user-started lock session alive and visible. */
public class LockService extends Service {
  public static final String START = "lock_start";
  public static final String STOP = "lock_stop";
  public static final String REFRESH = "lock_refresh";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private PowerManager.WakeLock wake;
  private int ticks;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!store.lockActive()) {
        finishLock(store.lockExpired());
        return;
      }
      if (ticks % 2 == 0) notifyLock();
      ticks++;
      handler.postDelayed(this, 1000);
    }
  };

  @Override public void onCreate() {
    super.onCreate();
    store = Store.get(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    NotificationChannel lock = new NotificationChannel("lock", "锁机", NotificationManager.IMPORTANCE_LOW);
    lock.setShowBadge(false);
    lock.setSound(null, null);
    nm.createNotificationChannel(lock);
    NotificationChannel done = new NotificationChannel("done", "完成提示", NotificationManager.IMPORTANCE_HIGH);
    done.setSound(null, null);
    done.enableVibration(false);
    nm.createNotificationChannel(done);
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":lock");
    wake.setReferenceCounted(false);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null || intent.getAction() == null ? REFRESH : intent.getAction();
    if (STOP.equals(action)) {
      finishLock(false);
      return START_NOT_STICKY;
    }
    if (!store.lockActive()) {
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return START_NOT_STICKY;
    }
    startForegroundCompat();
    if (wake != null && !wake.isHeld()) wake.acquire(6 * 60 * 60 * 1000L);
    handler.removeCallbacks(tick);
    handler.post(tick);
    return START_STICKY;
  }

  private void startForegroundCompat() {
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(103, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(103, buildNotification());
    }
  }

  private void notifyLock() {
    getSystemService(NotificationManager.class).notify(103, buildNotification());
  }

  private Notification buildNotification() {
    long remain = store.lockRemaining();
    int accent = store.settings().optInt("accent", Store.DEFAULT_ACCENT);
    Intent open = new Intent(this, LockActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent content = PendingIntent.getActivity(this, 103, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent giveUp = PendingIntent.getActivity(this, 104,
        new Intent(this, LockActivity.class)
            .putExtra("giveup", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "lock")
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle("锁机中")
        .setContentText("还剩 " + ClockMath.format(remain) + " · 专注当下")
        .setColor(accent)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setShowWhen(false)
        .setCategory(Notification.CATEGORY_STOPWATCH)
        .setContentIntent(content)
        .addAction(new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notify),
            "打开锁机", content).build())
        .addAction(new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notify),
            "放弃", giveUp).build())
        .build();
  }

  private void finishLock(boolean natural) {
    handler.removeCallbacks(tick);
    store.clearLock();
    if (wake != null && wake.isHeld()) wake.release();
    if (natural) {
      Notification.Builder b = new Notification.Builder(this, "done")
          .setSmallIcon(R.drawable.ic_notify)
          .setContentTitle("锁机结束")
          .setContentText("辛苦了，起来活动一下吧。")
          .setColor(store.settings().optInt("accent", Store.DEFAULT_ACCENT))
          .setAutoCancel(true)
          .setContentIntent(PendingIntent.getActivity(this, 105,
              new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
      getSystemService(NotificationManager.class).notify(106, b.build());
      if (store.settings().optBoolean("sound", true)) Sound.chime();
    }
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override public void onDestroy() {
    handler.removeCallbacks(tick);
    if (wake != null && wake.isHeld()) wake.release();
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
}
