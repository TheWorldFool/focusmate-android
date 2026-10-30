package cn.local.focusmate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Full-screen voluntary lock. Leaving is intentionally inconvenient. */
public class LockActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private Ui ui;
  private Charts.Ring ring;
  private TextView remainView;
  private long duration;
  private long startedWall;
  private boolean dialogShowing;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!store.lockActive()) {
        ui.toast("锁机结束");
        finish();
        return;
      }
      long remain = store.lockRemaining();
      long used = Math.max(0, duration - remain);
      ring.set(duration <= 0 ? 0 : remain / (float) duration, ClockMath.format(remain), "距解锁", false);
      remainView.setText("已坚持 " + ClockMath.format(used));
      handler.postDelayed(this, 1000);
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    store = Store.get(this);
    int startMinutes = getIntent() == null ? 0 : getIntent().getIntExtra("startMinutes", 0);
    if (startMinutes >= 2 && !store.lockActive()) {
      store.startLock(startMinutes);
      try {
        startService(new Intent(this, LockService.class).setAction(LockService.START));
      } catch (Exception ignored) { }
    }
    if (!store.lockActive()) {
      finish();
      return;
    }
    ui = new Ui(this, store.settings().optInt("accent", Store.DEFAULT_ACCENT), true);
    JSONObject lock = store.lock();
    duration = lock == null ? 0 : lock.optLong("duration", 0);
    if (duration <= 0) duration = store.lockRemaining();
    startedWall = lock == null ? System.currentTimeMillis() : lock.optLong("startedWall", System.currentTimeMillis());

    Window w = getWindow();
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true);
      setTurnScreenOn(true);
    } else {
      w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
    w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    build();
    startService(new Intent(this, LockService.class).setAction(LockService.REFRESH));
    if (getIntent() != null && getIntent().getBooleanExtra("giveup", false)) {
      handler.postDelayed(this::confirmGiveUp, 300);
    }
  }

  private void build() {
    LinearLayout root = ui.column();
    ui.window(root);
    root.setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(18));

    LinearLayout head = ui.row();
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(0, ui.dp(16), 0, 0);
    View dot = new View(this);
    dot.setBackground(ui.rounded(ui.accent, 5));
    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ui.dp(9), ui.dp(9));
    dotLp.rightMargin = ui.dp(8);
    head.addView(dot, dotLp);
    head.addView(ui.medium("锁机中", 16, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    TextView total = ui.text("共 " + duration / 60000 + " 分钟", 12, ui.muted);
    head.addView(total);
    root.addView(head);

    TextView motto = ui.text("「" + store.mottoForToday() + "」", 13, ui.muted);
    motto.setGravity(Gravity.CENTER);
    motto.setPadding(ui.dp(4), ui.dp(20), ui.dp(4), 0);
    root.addView(motto);

    ring = new Charts.Ring(this, ui);
    ring.set(duration <= 0 ? 0 : store.lockRemaining() / (float) duration,
        ClockMath.format(store.lockRemaining()), "距解锁", false);
    LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1, 0, 1);
    ringLp.topMargin = ui.dp(4);
    root.addView(ring, ringLp);

    remainView = ui.text("已坚持 00:00", 13, ui.muted);
    remainView.setGravity(Gravity.CENTER);
    root.addView(remainView);

    LinearLayout facts = ui.card();
    facts.setGravity(Gravity.CENTER);
    facts.setPadding(ui.dp(6), ui.dp(14), ui.dp(6), ui.dp(14));
    facts.addView(fact("本次时长", duration / 60000 + " 分钟"), new LinearLayout.LayoutParams(0, -2, 1));
    facts.addView(fact("豁免应用", whitelistCount() + " 个"), new LinearLayout.LayoutParams(0, -2, 1));
    facts.addView(fact("开始于", android.text.format.DateFormat.format("HH:mm", startedWall).toString()),
        new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams factsLp = new LinearLayout.LayoutParams(-1, -2);
    factsLp.topMargin = ui.dp(16);
    root.addView(facts, factsLp);

    if (!guardEnabled()) {
      LinearLayout warning = ui.card();
      warning.setBackground(ui.rounded(ui.accentSoft, 14));
      warning.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
      warning.addView(ui.medium("未开启学霸模式", 14, ui.accent));
      TextView line = ui.text("开启后才能在其他应用上把你带回锁机页；现在只有打开本应用时才会显示。", 12, ui.muted);
      line.setPadding(0, ui.dp(3), 0, ui.dp(6));
      warning.addView(line);
      TextView open = ui.textBtn("去开启", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
      warning.addView(open);
      LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(-1, -2);
      warnLp.topMargin = ui.dp(10);
      root.addView(warning, warnLp);
    }

    TextView giveUp = ui.textBtn("长按放弃锁机", this::confirmGiveUp);
    giveUp.setTextColor(ui.muted);
    giveUp.setGravity(Gravity.CENTER);
    giveUp.setOnLongClickListener(v -> {
      confirmGiveUp();
      return true;
    });
    giveUp.setOnClickListener(v -> ui.toast("请长按，给冲动一点缓冲"));
    LinearLayout.LayoutParams giveUpLp = new LinearLayout.LayoutParams(-1, -2);
    giveUpLp.topMargin = ui.dp(10);
    root.addView(giveUp, giveUpLp);

    setContentView(root);
    Ui.applyInsets(this, root, null, ui);
  }

  private LinearLayout fact(String label, String value) {
    LinearLayout col = ui.column();
    col.setGravity(Gravity.CENTER);
    TextView v = ui.medium(value, 15, ui.ink);
    v.setGravity(Gravity.CENTER);
    col.addView(v);
    TextView l = ui.text(label, 11, ui.muted);
    l.setGravity(Gravity.CENTER);
    l.setPadding(0, ui.dp(3), 0, 0);
    col.addView(l);
    return col;
  }

  private int whitelistCount() {
    JSONArray list = store.settings().optJSONArray("lockWhitelist");
    if (list == null) list = store.settings().optJSONArray("whitelist");
    return list == null ? 0 : list.length();
  }

  private boolean guardEnabled() {
    try {
      String enabled = Settings.Secure.getString(getContentResolver(),
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
      return enabled != null && enabled.contains(getPackageName()) && enabled.contains("StudyGuard");
    } catch (Exception e) {
      return false;
    }
  }

  private void confirmGiveUp() {
    if (dialogShowing) return;
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    TextView hint = ui.text("锁机是为了保护你原本想做的事。确定现在离开吗？", 13, ui.muted);
    form.addView(hint);
    EditText reason = ui.input(form, "离开原因（可选）", "", android.text.InputType.TYPE_CLASS_TEXT);
    final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
    android.app.AlertDialog dialog = ui.dialog()
        .setTitle("放弃这次锁机？")
        .setView(form)
        .setNegativeButton("继续坚持", null)
        .setPositiveButton("放弃", null)
        .create();
    holder[0] = dialog;
    dialogShowing = true;
    dialog.setOnDismissListener(d -> dialogShowing = false);
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(ui.danger);
      dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        String note = reason.getText().toString().trim();
        dialog.dismiss();
        store.clearLock();
        startService(new Intent(this, LockService.class).setAction(LockService.STOP));
        ui.toast(note.isEmpty() ? "已结束锁机" : "已结束锁机：" + note);
        finish();
      });
    });
    dialog.show();
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent != null && intent.getBooleanExtra("giveup", false)) {
      handler.postDelayed(this::confirmGiveUp, 300);
    }
  }

  @Override protected void onResume() {
    super.onResume();
    if (store == null) return;
    if (!store.lockActive()) {
      finish();
      return;
    }
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override protected void onDestroy() {
    handler.removeCallbacks(tick);
    super.onDestroy();
  }

  @Override public void onBackPressed() {
    if (store != null && store.lockActive()) {
      ui.toast("锁机中，请先完成或长按底部放弃");
      return;
    }
    super.onBackPressed();
  }
}
