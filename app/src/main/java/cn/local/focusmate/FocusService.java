package cn.local.focusmate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.UUID;

public class FocusService extends Service {
  public static final String START = "start";
  public static final String PAUSE = "pause";
  public static final String RESUME = "resume";
  public static final String FINISH = "finish";
  public static final String CANCEL = "cancel";
  public static final String SKIP_REST = "skip_rest";
  public static final String REFRESH = "refresh";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private PowerManager.WakeLock wake;
  private TextView overlay;
  private WindowManager windowManager;
  private int ticks;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      JSONObject t = store.timer();
      if (t == null) { shutdown(); return; }
      long elapsed = store.elapsed(t);
      if (!t.optBoolean("paused") && "countdown".equals(t.optString("mode"))) {
        long target = t.optLong("target");
        if (target > 0 && elapsed >= target) {
          if ("focus".equals(t.optString("phase"))) focusDone();
          else restDone();
          return;
        }
      }
      updateOverlay(t);
      if (ticks % 2 == 0) updateNotification(t);
      if (ticks % 20 == 0) FocusWidget.refresh(FocusService.this);
      ticks++;
      handler.postDelayed(this, 500);
    }
  };

  @Override public void onCreate() {
    super.onCreate();
    store = Store.get(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    NotificationChannel focus = new NotificationChannel("focus", "专注计时", NotificationManager.IMPORTANCE_LOW);
    focus.setShowBadge(false);
    focus.setSound(null, null);
    nm.createNotificationChannel(focus);
    NotificationChannel done = new NotificationChannel("done", "完成提示", NotificationManager.IMPORTANCE_HIGH);
    done.setSound(null, null);
    done.enableVibration(false);
    nm.createNotificationChannel(done);
    NotificationChannel reminder = new NotificationChannel("reminder", "待办提醒", NotificationManager.IMPORTANCE_HIGH);
    reminder.setShowBadge(true);
    nm.createNotificationChannel(reminder);
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":focus");
    wake.setReferenceCounted(false);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null || intent.getAction() == null ? REFRESH : intent.getAction();
    if (START.equals(action)) {
      if (store.timer() == null) startNew(intent);
    }
    JSONObject t = store.timer();
    if (t == null) {
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return START_NOT_STICKY;
    }
    startForegroundCompat(t);
    if (PAUSE.equals(action)) {
      boolean strictFocus = "focus".equals(t.optString("phase")) && t.optBoolean("strict");
      if (!strictFocus && !t.optBoolean("paused")) {
        Store.put(t, "accumulated", store.elapsed(t));
        Store.put(t, "paused", true);
        store.save();
        notifyChanged();
      }
    } else if (RESUME.equals(action)) {
      if (t.optBoolean("paused")) {
        Store.put(t, "anchorWall", System.currentTimeMillis());
        Store.put(t, "anchorElapsed", SystemClock.elapsedRealtime());
        Store.put(t, "paused", false);
        store.save();
        notifyChanged();
      }
    } else if (FINISH.equals(action)) {
      if ("focus".equals(t.optString("phase"))) {
        store.record(t, true, "");
        playDoneFeedback();
      }
      finishTimer();
      return START_NOT_STICKY;
    } else if (CANCEL.equals(action)) {
      if ("focus".equals(t.optString("phase"))) {
        store.record(t, false, intent.getStringExtra("reason") == null ? "主动放弃" : intent.getStringExtra("reason"));
      }
      finishTimer();
      return START_NOT_STICKY;
    } else if (SKIP_REST.equals(action)) {
      if ("rest".equals(t.optString("phase"))) {
        if (store.settings().optBoolean("autoNext", false)) {
          startNextFocus(t, t.optInt("roundsDone", 1));
        } else {
          finishTimer();
        }
        return START_STICKY;
      }
    }
    syncWake(t);
    syncAudio(t);
    syncOverlay();
    updateNotification(t);
    handler.removeCallbacks(tick);
    handler.post(tick);
    return START_STICKY;
  }

  private void startNew(Intent intent) {
    String taskId = intent.getStringExtra("taskId");
    if (taskId == null) taskId = "";
    JSONObject task = taskId.isEmpty() ? null : store.find(taskId);
    JSONObject settings = store.settings();
    String mode = intent.getStringExtra("mode");
    if (mode == null) mode = task == null ? "countdown" : task.optString("mode", "countdown");
    if (!"stopwatch".equals(mode)) mode = "countdown";
    int minutes = intent.getIntExtra("minutes", task == null ? settings.optInt("focusMinutes", 25) : task.optInt("minutes", 25));
    if (minutes < 1) minutes = 1;
    if (minutes > 1440) minutes = 1440;
    int rest = intent.getIntExtra("rest", task == null ? settings.optInt("restMinutes", 5) : task.optInt("restMinutes", 5));
    if (rest < 0) rest = 0;
    if (rest > 120) rest = 120;
    String title = intent.getStringExtra("title");
    if (title == null || title.isEmpty()) title = task == null ? "自由专注" : task.optString("title");
    String group = intent.getStringExtra("group");
    if (group == null) group = task == null ? "专注" : task.optString("group", "未分组");
    boolean strict = intent.hasExtra("strict")
        ? intent.getBooleanExtra("strict", false) : settings.optBoolean("strict", false);
    boolean forceGuard = intent.getBooleanExtra("forceGuard", false);
    long now = System.currentTimeMillis();
    JSONObject timer = Store.obj(
        "sessionId", UUID.randomUUID().toString(),
        "taskId", taskId,
        "title", title,
        "group", group,
        "mode", mode,
        "focusMode", mode,
        "phase", "focus",
        "target", "countdown".equals(mode) ? minutes * 60000L : 0,
        "focusMinutes", minutes,
        "restMinutes", rest,
        "longRestMinutes", settings.optInt("longRestMinutes", 15),
        "roundsBeforeLong", settings.optInt("roundsBeforeLong", 4),
        "roundsDone", 0,
        "startedWall", now,
        "anchorWall", now,
        "anchorElapsed", SystemClock.elapsedRealtime(),
        "accumulated", 0,
        "paused", false,
        "strict", strict,
        "forceGuard", forceGuard,
        "autoRest", settings.optBoolean("autoRest", false),
        "autoNext", settings.optBoolean("autoNext", false),
        "motto", store.randomMotto()
    );
    store.setTimer(timer);
    store.save();
    notifyChanged();
  }

  private void focusDone() {
    JSONObject t = store.timer();
    if (t == null) return;
    store.record(t, true, "");
    int rounds = t.optInt("roundsDone", 0) + 1;
    Store.put(t, "roundsDone", rounds);
    store.save();
    playDoneFeedback();
    notifyDone("这段专注完成了", t.optString("title"));
    FocusWidget.refresh(this);
    if (store.settings().optBoolean("autoRest", false)) {
      int per = Math.max(2, t.optInt("roundsBeforeLong", 4));
      int restMinutes = (rounds % per == 0) ? t.optInt("longRestMinutes", 15) : t.optInt("restMinutes", 5);
      if (restMinutes > 0) {
        startRest(t, rounds, restMinutes);
        return;
      }
    }
    finishTimer();
  }

  private void startRest(JSONObject t, int rounds, int restMinutes) {
    long now = System.currentTimeMillis();
    Store.put(t, "sessionId", UUID.randomUUID().toString());
    Store.put(t, "phase", "rest");
    Store.put(t, "mode", "countdown");
    Store.put(t, "target", restMinutes * 60000L);
    Store.put(t, "startedWall", now);
    Store.put(t, "anchorWall", now);
    Store.put(t, "anchorElapsed", SystemClock.elapsedRealtime());
    Store.put(t, "accumulated", 0);
    Store.put(t, "paused", false);
    Store.put(t, "roundsDone", rounds);
    store.save();
    syncWake(t);
    syncAudio(t);
    syncOverlay();
    updateNotification(t);
    notifyChanged();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  private void restDone() {
    JSONObject t = store.timer();
    if (t == null) return;
    playDoneFeedback();
    notifyDone("休息结束", "准备开始下一个番茄");
    if (store.settings().optBoolean("autoNext", false)) {
      startNextFocus(t, t.optInt("roundsDone", 1));
    } else {
      finishTimer();
    }
  }

  private void startNextFocus(JSONObject t, int rounds) {
    long now = System.currentTimeMillis();
    String mode = t.optString("focusMode", "countdown");
    int minutes = Math.max(1, t.optInt("focusMinutes", 25));
    Store.put(t, "sessionId", UUID.randomUUID().toString());
    Store.put(t, "phase", "focus");
    Store.put(t, "mode", mode);
    Store.put(t, "target", "countdown".equals(mode) ? minutes * 60000L : 0);
    Store.put(t, "startedWall", now);
    Store.put(t, "anchorWall", now);
    Store.put(t, "anchorElapsed", SystemClock.elapsedRealtime());
    Store.put(t, "accumulated", 0);
    Store.put(t, "paused", false);
    Store.put(t, "roundsDone", rounds);
    store.save();
    syncWake(t);
    syncAudio(t);
    syncOverlay();
    updateNotification(t);
    notifyChanged();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  private void finishTimer() {
    handler.removeCallbacks(tick);
    store.clearTimer();
    store.save();
    Sound.stopNoise();
    removeOverlay();
    releaseWake();
    stopForeground(STOP_FOREGROUND_REMOVE);
    FocusWidget.refresh(this);
    notifyChanged();
    stopSelf();
  }

  private void notifyChanged() {
    sendBroadcast(new Intent("cn.local.focusmate.CHANGED").setPackage(getPackageName()));
  }

  private void shutdown() {
    handler.removeCallbacks(tick);
    Sound.stopNoise();
    removeOverlay();
    releaseWake();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  private void startForegroundCompat(JSONObject timer) {
    Notification notification = buildNotification(timer);
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(101, notification);
    }
  }

  private PendingIntent serviceIntent(String action, int code) {
    return PendingIntent.getService(this, code,
        new Intent(this, FocusService.class).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private PendingIntent openIntent() {
    Intent open = new Intent(this, MainActivity.class).putExtra("openTimer", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(this, 90, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private Notification buildNotification(JSONObject t) {
    boolean rest = "rest".equals(t.optString("phase"));
    boolean paused = t.optBoolean("paused");
    boolean strict = t.optBoolean("strict");
    String title = rest ? "休息 · " + t.optString("title") : t.optString("title");
    String text = display(t) + " · " + (paused ? "已暂停" : rest ? "休息中" : "专注中");
    int accent = store.settings().optInt("accent", Store.DEFAULT_ACCENT);
    Notification.Builder b = new Notification.Builder(this, "focus")
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(title)
        .setContentText(text)
        .setColor(accent)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setShowWhen(false)
        .setCategory(Notification.CATEGORY_STOPWATCH)
        .setContentIntent(openIntent());
    if (!paused && (!strict || rest)) {
      b.addAction(new Notification.Action.Builder(
          android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notify),
          "暂停", serviceIntent(PAUSE, 1)).build());
    } else if (paused) {
      b.addAction(new Notification.Action.Builder(
          android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notify),
          "继续", serviceIntent(RESUME, 2)).build());
    }
    b.addAction(new Notification.Action.Builder(
        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notify),
        rest ? "结束休息" : "完成", serviceIntent(FINISH, 3)).build());
    return b.build();
  }

  private void updateNotification(JSONObject t) {
    getSystemService(NotificationManager.class).notify(101, buildNotification(t));
  }

  private void notifyDone(String title, String text) {
    Notification.Builder b = new Notification.Builder(this, "done")
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(title)
        .setContentText(text)
        .setColor(store.settings().optInt("accent", Store.DEFAULT_ACCENT))
        .setAutoCancel(true)
        .setContentIntent(openIntent());
    getSystemService(NotificationManager.class).notify(102, b.build());
  }

  private String display(JSONObject t) {
    long elapsed = store.elapsed(t);
    if ("countdown".equals(t.optString("mode"))) {
      return ClockMath.format(ClockMath.remaining(t.optLong("target"), elapsed));
    }
    return ClockMath.format(elapsed);
  }

  private void playDoneFeedback() {
    if (store.settings().optBoolean("sound", true)) Sound.chime();
    if (store.settings().optBoolean("vibrate", true)) {
      try {
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) {
          if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 160, 90, 120}, -1));
          } else {
            v.vibrate(new long[]{0, 160, 90, 120}, -1);
          }
        }
      } catch (Exception ignored) { }
    }
  }

  private void syncWake(JSONObject t) {
    if (t == null) { releaseWake(); return; }
    if (!t.optBoolean("paused")) {
      if (!wake.isHeld()) wake.acquire(25 * 60 * 60 * 1000L);
    } else {
      releaseWake();
    }
  }

  private void releaseWake() {
    try {
      if (wake != null && wake.isHeld()) wake.release();
    } catch (Exception ignored) { }
  }

  private void syncAudio(JSONObject t) {
    boolean want = store.settings().optBoolean("noise", false)
        && t != null && !t.optBoolean("paused") && "focus".equals(t.optString("phase"));
    if (want) Sound.startNoise(store.settings().optString("noiseType", "brown"));
    else Sound.stopNoise();
  }

  private void syncOverlay() {
    boolean want = store.settings().optBoolean("overlay", false) && Settings.canDrawOverlays(this);
    if (want && overlay == null) {
      try {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(13);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(16), dp(10), dp(16), dp(10));
        overlay.setBackground(rounded(store.settings().optInt("accent", Store.DEFAULT_ACCENT)));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getResources().getDisplayMetrics().widthPixels - dp(170);
        params.y = dp(140);
        overlay.setOnTouchListener(new OverlayDrag(params));
        windowManager.addView(overlay, params);
      } catch (Exception e) {
        overlay = null;
      }
    } else if (!want && overlay != null) {
      removeOverlay();
    }
    JSONObject t = store.timer();
    if (overlay != null && t != null) {
      boolean rest = "rest".equals(t.optString("phase"));
      overlay.setBackground(rounded(rest ? 0xff3fae8f : store.settings().optInt("accent", Store.DEFAULT_ACCENT)));
      overlay.setText(display(t) + "\n" + t.optString("title"));
    }
  }

  private void updateOverlay(JSONObject t) {
    if (overlay != null) {
      boolean rest = "rest".equals(t.optString("phase"));
      overlay.setBackground(rounded(rest ? 0xff3fae8f : store.settings().optInt("accent", Store.DEFAULT_ACCENT)));
      overlay.setText(display(t) + "\n" + t.optString("title"));
    }
  }

  private void removeOverlay() {
    if (overlay != null && windowManager != null) {
      try { windowManager.removeView(overlay); } catch (Exception ignored) { }
    }
    overlay = null;
  }

  private GradientDrawable rounded(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(20));
    return d;
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class OverlayDrag implements View.OnTouchListener {
    private final WindowManager.LayoutParams params;
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private boolean moved;

    OverlayDrag(WindowManager.LayoutParams params) { this.params = params; }

    @Override public boolean onTouch(View v, MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        downX = event.getRawX();
        downY = event.getRawY();
        startX = params.x;
        startY = params.y;
        moved = false;
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
        float dx = event.getRawX() - downX;
        float dy = event.getRawY() - downY;
        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
        params.x = startX + (int) dx;
        params.y = startY + (int) dy;
        try { windowManager.updateViewLayout(overlay, params); } catch (Exception ignored) { }
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP) {
        if (!moved) {
          try {
            startActivity(new Intent(FocusService.this, MainActivity.class)
                .putExtra("openTimer", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
          } catch (Exception ignored) { }
        }
        return true;
      }
      return false;
    }
  }

  @Override public void onDestroy() {
    handler.removeCallbacks(tick);
    Sound.stopNoise();
    removeOverlay();
    releaseWake();
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
}
