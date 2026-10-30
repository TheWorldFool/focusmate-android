package cn.local.focusmate;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Blocks non-whitelisted foreground apps while a focus phase is running. */
public class StudyGuard extends AccessibilityService {
  private long lastAction;
  private long lastLockAction;

  @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    Store store = Store.get(this);
    CharSequence raw = event.getPackageName();
    if (raw == null) return;
    String pkg = raw.toString();

    if (store.lockActive()) {
      if (allow(store, pkg) || lockWhitelisted(store, pkg)) return;
      long now = System.currentTimeMillis();
      if (now - lastLockAction < 1500) return;
      lastLockAction = now;
      startActivity(new Intent(this, LockActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
      Toast.makeText(this, "锁机中：请回到锁机页，或长按放弃。", Toast.LENGTH_SHORT).show();
      return;
    }

    JSONObject timer = store.timer();
    if (timer == null || timer.optBoolean("paused")) return;
    if (!"focus".equals(timer.optString("phase"))) return;
    if (!store.settings().optBoolean("guard", false) && !timer.optBoolean("forceGuard")) return;
    if (allow(store, pkg) || timerWhitelisted(store, timer, pkg)) return;
    long now = System.currentTimeMillis();
    if (now - lastAction < 2500) return;
    lastAction = now;
    boolean home = !"toast".equals(store.settings().optString("guardAction", "home"));
    if (home) performGlobalAction(GLOBAL_ACTION_HOME);
    Toast.makeText(this, "专注中：请回到任务，或使用白名单应用。", Toast.LENGTH_SHORT).show();
  }

  private boolean lockWhitelisted(Store store, String pkg) {
    JSONArray own = store.settings().optJSONArray("lockWhitelist");
    return inList(own != null ? own : store.settings().optJSONArray("whitelist"), pkg);
  }

  private boolean timerWhitelisted(Store store, JSONObject timer, String pkg) {
    String taskId = timer.optString("taskId");
    JSONObject task = taskId.isEmpty() ? null : store.find(taskId);
    JSONArray own = task == null ? null : task.optJSONArray("whitelist");
    return inList(own != null ? own : store.settings().optJSONArray("whitelist"), pkg);
  }

  private static boolean inList(JSONArray list, String pkg) {
    if (list == null) return false;
    for (int i = 0; i < list.length(); i++) {
      if (pkg.equals(list.optString(i))) return true;
    }
    return false;
  }

  private boolean allow(Store store, String pkg) {
    if (pkg.equals(getPackageName())) return true;
    if (pkg.equals("android") || pkg.equals("com.android.systemui")) return true;
    String[] always = {
        "com.android.settings", "permissioncontroller", "launcher", "dialer", "incallui", "telecom",
        "inputmethod", "keyboard", "com.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.android.providers.settings", "com.android.phone", "com.android.mms"
    };
    for (String token : always) if (pkg.contains(token)) return true;
    return false;
  }

  @Override public void onInterrupt() { }
}
