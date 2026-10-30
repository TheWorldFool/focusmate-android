package cn.local.focusmate;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 我的：目标、成就、学霸模式、默认参数、外观、数据。 */
public final class MePage {
  private final MainActivity act;
  private final Store store;
  private final Ui ui;

  public MePage(MainActivity act) {
    this.act = act;
    this.store = act.store;
    this.ui = act.ui;
  }

  public View build() {
    ScrollView scroll = new ScrollView(act);
    scroll.setFillViewport(true);
    LinearLayout body = ui.column();
    body.setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(26));
    scroll.addView(body);

    profile(body);
    goalCard(body);
    achievements(body);
    guardCard(body);
    lockCard(body);
    scheduledLockCard(body);
    timerCard(body);
    mottoCard(body);
    relaxCard(body);
    appearanceCard(body);
    dataCard(body);
    aboutCard(body);
    return scroll;
  }

  private void section(LinearLayout parent, String title, String subtitle) {
    LinearLayout row = ui.row();
    row.setPadding(ui.dp(2), ui.dp(10), ui.dp(2), ui.dp(4));
    row.addView(ui.medium(title, 13, ui.muted), new LinearLayout.LayoutParams(0, -2, 1));
    if (subtitle != null) row.addView(ui.text(subtitle, 10, ui.muted));
    parent.addView(row);
  }

  private void profile(LinearLayout body) {
    LinearLayout card = ui.card(body);
    LinearLayout head = ui.row();
    LinearLayout names = ui.column();
    names.addView(ui.medium("专注清单", 19, ui.ink));
    names.addView(ui.text("本地版 · 不需要账号", 11, ui.muted));
    head.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.medium("Lv. " + store.level(), 20, ui.accent));
    card.addView(head);

    long totalMinutes = store.total("0000-01-01", "9999-12-31", null) / 60000;
    TextView total = ui.medium(Store.minutesText(totalMinutes), 25, ui.ink);
    total.setPadding(0, ui.dp(12), 0, ui.dp(2));
    card.addView(total);
    card.addView(ui.text("累计专注时长", 10, ui.muted));

    Charts.Progress progress = new Charts.Progress(act, ui);
    progress.set(store.levelProgress() / 600f, ui.accent);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.dp(6));
    p.topMargin = ui.dp(10);
    card.addView(progress, p);
    TextView levelHint = ui.text("再专注 " + (600 - store.levelProgress()) + " 分钟升到 Lv. " + (store.level() + 1), 10, ui.muted);
    levelHint.setPadding(0, ui.dp(4), 0, ui.dp(10));
    card.addView(levelHint);

    LinearLayout stats = ui.row();
    stats.addView(profileStat(String.valueOf(store.streak(Store.today())), "连续天数"));
    stats.addView(profileStat(String.valueOf(store.sessionCount("0000-01-01", "9999-12-31", true)), "完成番茄"));
    stats.addView(profileStat(String.valueOf(store.completedTaskCount("0000-01-01", "9999-12-31")), "完成任务"));
    card.addView(stats);
  }

  private View profileStat(String value, String label) {
    LinearLayout box = ui.column();
    box.setPadding(0, ui.dp(2), ui.dp(6), 0);
    box.addView(ui.medium(value, 17, ui.ink));
    box.addView(ui.text(label, 10, ui.muted));
    box.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    return box;
  }

  private void goalCard(LinearLayout body) {
    int goalMin = store.settings().optInt("dailyGoalMinutes", 120);
    int goalPom = store.settings().optInt("dailyGoalPomodoros", 4);
    int todayMin = store.todayMinutes();
    int todayPom = store.todayPomodoros();

    LinearLayout card = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.medium("今日目标", 15, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.textBtn("调整", this::goalDialog));
    card.addView(head);

    TextView minLabel = ui.text("专注 " + todayMin + " / " + goalMin + " 分钟", 12, ui.ink);
    minLabel.setPadding(0, ui.dp(8), 0, ui.dp(4));
    card.addView(minLabel);
    Charts.Progress minProgress = new Charts.Progress(act, ui);
    minProgress.set(goalMin <= 0 ? 1 : todayMin / (float) goalMin, todayMin >= goalMin ? ui.success : ui.accent);
    card.addView(minProgress, new LinearLayout.LayoutParams(-1, ui.dp(7)));
    TextView pomLabel = ui.text("番茄 " + todayPom + " / " + goalPom + " 个", 12, ui.ink);
    pomLabel.setPadding(0, ui.dp(12), 0, ui.dp(4));
    card.addView(pomLabel);
    Charts.Progress pomProgress = new Charts.Progress(act, ui);
    pomProgress.set(goalPom <= 0 ? 1 : todayPom / (float) goalPom, todayPom >= goalPom ? ui.success : ui.accent);
    card.addView(pomProgress, new LinearLayout.LayoutParams(-1, ui.dp(7)));
    card.addView(ui.text("目标只用于提醒自己，不会限制任何功能。", 10, ui.muted));
  }

  private void goalDialog() {
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    EditText minutes = ui.input(form, "每日专注目标（分钟）",
        String.valueOf(store.settings().optInt("dailyGoalMinutes", 120)), InputType.TYPE_CLASS_NUMBER);
    EditText pomodoros = ui.input(form, "每日番茄目标（个）",
        String.valueOf(store.settings().optInt("dailyGoalPomodoros", 4)), InputType.TYPE_CLASS_NUMBER);
    ui.show(ui.dialog().setTitle("每日目标").setView(form)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", (d, w) -> {
          try {
            int min = Integer.parseInt(minutes.getText().toString().trim());
            int pom = Integer.parseInt(pomodoros.getText().toString().trim());
            if (min < 10 || min > 1440) throw new IllegalArgumentException("分钟目标应在 10—1440");
            if (pom < 1 || pom > 48) throw new IllegalArgumentException("番茄目标应在 1—48");
            Store.put(store.settings(), "dailyGoalMinutes", min);
            Store.put(store.settings(), "dailyGoalPomodoros", pom);
            store.save();
            act.render();
          } catch (Exception e) {
            ui.toast(e.getMessage() == null ? "请输入整数" : e.getMessage());
          }
        }));
  }

  private void achievements(LinearLayout body) {
    JSONArray list = store.achievements();
    int unlocked = 0;
    for (int i = 0; i < list.length(); i++) {
      JSONObject b = list.optJSONObject(i);
      if (b != null && b.optBoolean("unlocked")) unlocked++;
    }
    section(body, "成就", unlocked + " / " + list.length());
    LinearLayout card = ui.card(body);
    for (int start = 0; start < list.length(); start += 3) {
      LinearLayout row = ui.row();
      row.setGravity(Gravity.TOP);
      for (int i = start; i < Math.min(start + 3, list.length()); i++) {
        JSONObject badge = list.optJSONObject(i);
        boolean open = badge != null && badge.optBoolean("unlocked");
        LinearLayout cell = ui.column();
        cell.setPadding(ui.dp(7), ui.dp(8), ui.dp(7), ui.dp(8));
        cell.setMinimumHeight(ui.dp(88));
        cell.setBackground(ui.rounded(open ? ui.accentSoft : ui.bg, 12));
        if (badge != null) {
          TextView icon = ui.text(badge.optString("icon"), 19, open ? ui.ink : ui.muted);
          icon.setAlpha(open ? 1f : 0.35f);
          cell.addView(icon);
          TextView title = ui.medium(badge.optString("title"), 11, open ? ui.ink : ui.muted);
          title.setAlpha(open ? 1f : 0.5f);
          cell.addView(title);
          TextView desc = ui.text(badge.optString("desc"), 9, ui.muted);
          desc.setMaxLines(2);
          desc.setAlpha(open ? 1f : 0.5f);
          cell.addView(desc);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = ui.dp(3);
        lp.rightMargin = ui.dp(3);
        lp.bottomMargin = ui.dp(6);
        row.addView(cell, lp);
      }
      for (int i = list.length(); i < start + 3; i++) {
        View filler = new View(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
        lp.leftMargin = ui.dp(3);
        lp.rightMargin = ui.dp(3);
        row.addView(filler, lp);
      }
      card.addView(row);
    }
  }

  private void guardCard(LinearLayout body) {
    section(body, "学霸模式", "需要无障碍权限");
    LinearLayout card = ui.card(body);
    act.settingsSwitch(card, "启用学霸模式", "guard", false);
    card.addView(ui.text("专注时识别前台应用；非白名单应用可被返回桌面。系统设置、电话、桌面与输入法始终放行。不读取页面内容、不收集输入、不联网。", 10, ui.muted));

    JSONArray whitelist = store.settings().optJSONArray("whitelist");
    int count = whitelist == null ? 0 : whitelist.length();
    TextView white = ui.outline("应用白名单（" + count + "）", this::whitelist);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = ui.dp(8);
    card.addView(white, p);
    String action = "home".equals(store.settings().optString("guardAction", "home")) ? "返回桌面" : "仅提醒";
    card.addView(ui.outline("拦截方式：" + action, this::guardActionDialog));
    card.addView(ui.textBtn("打开系统无障碍设置", () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  }

  private void guardActionDialog() {
    String[] labels = {"返回桌面（更严格）", "仅提醒，不强制返回"};
    String current = store.settings().optString("guardAction", "home");
    int selected = "home".equals(current) ? 0 : 1;
    ui.show(ui.dialog().setTitle("学霸模式拦截方式").setSingleChoiceItems(labels, selected, (d, which) -> {
      Store.put(store.settings(), "guardAction", which == 0 ? "home" : "toast");
      store.save();
      d.dismiss();
      act.render();
    }));
  }

  private void whitelist() {
    AppWhitelist.pick(act, ui, "专注中允许使用的应用",
        store.settings().optJSONArray("whitelist"), false, result -> {
          Store.put(store.settings(), "whitelist", result == null ? new JSONArray() : result);
          store.save();
          ui.toast("白名单已保存");
          act.render();
        });
  }

  private void timerCard(LinearLayout body) {
    section(body, "计时默认值", null);
    LinearLayout card = ui.card(body);
    int focus = store.settings().optInt("focusMinutes", 25);
    int rest = store.settings().optInt("restMinutes", 5);
    int longRest = store.settings().optInt("longRestMinutes", 15);
    int rounds = store.settings().optInt("roundsBeforeLong", 4);
    TextView summary = ui.medium("专注 " + focus + " · 休息 " + rest + " · 长休息 " + longRest + " · 每 " + rounds + " 个", 13, ui.ink);
    card.addView(summary);
    TextView edit = ui.textBtn("修改默认时长与轮次", this::timerDefaultsDialog);
    card.addView(edit);
    act.settingsSwitch(card, "严格模式（专注中不可暂停）", "strict", false);
    act.settingsSwitch(card, "专注后自动开始休息", "autoRest", false);
    act.settingsSwitch(card, "休息后自动开始下一个番茄", "autoNext", false);
    act.settingsSwitch(card, "完成提示音", "sound", true);
    act.settingsSwitch(card, "完成时震动", "vibrate", true);
    act.settingsSwitch(card, "柔和白噪音", "noise", false);
    act.settingsSwitch(card, "保持屏幕常亮", "keepScreen", false);

    int midnight = store.settings().optInt("midnightHour", 0);
    String midnightLabel = midnight == 0 ? "00:00（自然日）" : String.format(Locale.US, "%02d:00", midnight);
    card.addView(ui.outline("每日起始时间：" + midnightLabel, this::midnightDialog));

    String[] noiseNames = {"棕噪音", "白噪音", "雨声", "海浪"};
    String[] noiseValues = {"brown", "white", "rain", "waves"};
    String noise = store.settings().optString("noiseType", "brown");
    String noiseLabel = "棕噪音";
    for (int i = 0; i < noiseValues.length; i++) if (noiseValues[i].equals(noise)) noiseLabel = noiseNames[i];
    card.addView(ui.outline("白噪音类型：" + noiseLabel, () -> noiseDialog(noiseNames, noiseValues)));

    act.settingsSwitch(card, "显示悬浮窗", "overlay", false);
    card.addView(ui.textBtn("授予悬浮窗权限", () -> {
      if (Build.VERSION.SDK_INT >= 23) {
        openSettingsUri(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + act.getPackageName()));
      }
    }));
    card.addView(ui.textBtn("通知设置", () -> openSettingsWithPackage(Settings.ACTION_APP_NOTIFICATION_SETTINGS)));
    if (Build.VERSION.SDK_INT >= 31) {
      card.addView(ui.textBtn("精确提醒权限", () -> openSettingsUri(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
          Uri.parse("package:" + act.getPackageName()))));
    }
    card.addView(ui.textBtn("电池优化白名单", () -> openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
    card.addView(ui.text("未授予精确提醒权限时，到点提醒可能延后；部分系统需要允许后台运行才能保证计时不被清理。", 10, ui.muted));
  }

  private void timerDefaultsDialog() {
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    EditText focus = ui.input(form, "专注时长（分钟）", String.valueOf(store.settings().optInt("focusMinutes", 25)), InputType.TYPE_CLASS_NUMBER);
    EditText rest = ui.input(form, "短休息（分钟）", String.valueOf(store.settings().optInt("restMinutes", 5)), InputType.TYPE_CLASS_NUMBER);
    EditText longRest = ui.input(form, "长休息（分钟）", String.valueOf(store.settings().optInt("longRestMinutes", 15)), InputType.TYPE_CLASS_NUMBER);
    EditText rounds = ui.input(form, "每几个番茄后长休息", String.valueOf(store.settings().optInt("roundsBeforeLong", 4)), InputType.TYPE_CLASS_NUMBER);
    ui.show(ui.dialog().setTitle("计时默认值").setView(form)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", (d, w) -> {
          try {
            int f = Integer.parseInt(focus.getText().toString().trim());
            int r = Integer.parseInt(rest.getText().toString().trim());
            int lr = Integer.parseInt(longRest.getText().toString().trim());
            int per = Integer.parseInt(rounds.getText().toString().trim());
            if (f < 1 || f > 180) throw new IllegalArgumentException("专注时长应为 1—180 分钟");
            if (r < 0 || r > 60) throw new IllegalArgumentException("短休息应为 0—60 分钟");
            if (lr < 0 || lr > 120) throw new IllegalArgumentException("长休息应为 0—120 分钟");
            if (per < 2 || per > 12) throw new IllegalArgumentException("轮次应为 2—12");
            Store.put(store.settings(), "focusMinutes", f);
            Store.put(store.settings(), "restMinutes", r);
            Store.put(store.settings(), "longRestMinutes", lr);
            Store.put(store.settings(), "roundsBeforeLong", per);
            store.save();
            act.idleMinutes = f;
            act.render();
          } catch (Exception e) {
            ui.toast(e.getMessage() == null ? "请输入整数" : e.getMessage());
          }
        }));
  }

  private void midnightDialog() {
    String[] names = new String[7];
    for (int h = 0; h < 7; h++) {
      names[h] = h == 0 ? "00:00（自然日）" : String.format(Locale.US, "%02d:00 起算", h);
    }
    int selected = Math.max(0, Math.min(6, store.settings().optInt("midnightHour", 0)));
    ui.show(ui.dialog().setTitle("每日起始时间")
        .setMessage("凌晨的专注记录与待办会归到前一天，适合熬夜学习。默认 00:00 按自然日计算。")
        .setSingleChoiceItems(names, selected, (d, which) -> {
          Store.put(store.settings(), "midnightHour", which);
          store.save();
          Store.setDayStartHour(which);
          act.date = Store.today();
          act.calendarMonth = act.date;
          act.statAnchor = act.date;
          d.dismiss();
          act.render();
        }));
  }

  private void noiseDialog(String[] names, String[] values) {
    String current = store.settings().optString("noiseType", "brown");
    int selected = 0;
    for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
    ui.show(ui.dialog().setTitle("白噪音类型").setSingleChoiceItems(names, selected, (d, which) -> {
      Store.put(store.settings(), "noiseType", values[which]);
      store.save();
      refreshTimerService();
      d.dismiss();
      act.render();
    }));
  }

  private void refreshTimerService() {
    if (store.timer() == null) return;
    try {
      act.startService(new Intent(act, FocusService.class).setAction(FocusService.REFRESH));
    } catch (Exception ignored) { }
  }

  private void mottoCard(LinearLayout body) {
    section(body, "格言", "显示在计时页上方");
    LinearLayout card = ui.card(body);
    TextView quote = ui.medium("「" + store.mottoForToday() + "」", 14, ui.ink);
    quote.setPadding(0, ui.dp(2), 0, ui.dp(4));
    card.addView(quote);
    card.addView(ui.textBtn("管理格言", this::mottoDialog));
  }

  private void lockCard(LinearLayout body) {
    section(body, "锁机", "把手机放下一段时间");
    LinearLayout card = ui.card(body);
    JSONObject lock = store.lock();
    if (lock != null) {
      card.addView(ui.medium("锁机进行中 · 还剩 " + ClockMath.format(store.lockRemaining()), 15, ui.accent));
      card.addView(ui.text("离开应用会被带回锁机页；如需提前结束，请在锁机页长按放弃。", 11, ui.muted));
      card.addView(ui.outline("返回锁机页", () -> act.startActivity(new Intent(act, LockActivity.class))));
    } else {
      card.addView(ui.text("设定一段时间，期间打开其他应用会被带回锁机页。需要开启学霸模式的无障碍权限；系统设置、电话、桌面和输入法始终放行，白名单应用不受限制。", 11, ui.muted));
      TextView start = ui.outline("开始锁机", this::lockDurationDialog);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = ui.dp(8);
      card.addView(start, lp);
    }
    JSONArray own = store.settings().optJSONArray("lockWhitelist");
    JSONArray global = store.settings().optJSONArray("whitelist");
    int globalCount = global == null ? 0 : global.length();
    String whiteLabel = own == null
        ? "锁机白名单：跟随全局（" + globalCount + "）"
        : "锁机独立白名单（" + own.length() + "）";
    card.addView(ui.outline(whiteLabel, this::lockWhitelist));
    card.addView(ui.textBtn("快速锁机小组件时长", this::quickLockDialog));
  }

  private void lockWhitelist() {
    AppWhitelist.pick(act, ui, "锁机时允许使用的应用",
        store.settings().optJSONArray("lockWhitelist"), true, result -> {
          if (result == null) {
            store.settings().remove("lockWhitelist");
            ui.toast("锁机白名单已跟随全局");
          } else {
            Store.put(store.settings(), "lockWhitelist", result);
            ui.toast("锁机白名单已保存");
          }
          store.save();
          act.render();
        });
  }

  private void quickLockDialog() {
    JSONArray current = store.settings().optJSONArray("lockQuickMinutes");
    int[] values = {15, 30, 45, 60};
    if (current != null) {
      for (int i = 0; i < values.length && i < current.length(); i++) {
        int value = current.optInt(i, values[i]);
        if (value >= 2 && value <= 720) values[i] = value;
      }
    }
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    EditText[] inputs = new EditText[4];
    for (int i = 0; i < 4; i++) {
      inputs[i] = ui.input(form, "按钮 " + (i + 1) + "（分钟，2—720）", String.valueOf(values[i]), InputType.TYPE_CLASS_NUMBER);
    }
    AlertDialog dialog = ui.dialog().setTitle("快速锁机小组件")
        .setMessage("桌面小组件上的四个按钮会立即开始对应时长的锁机。")
        .setView(form)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", null)
        .create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        JSONArray result = new JSONArray();
        try {
          for (EditText input : inputs) {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < 2 || value > 720) throw new IllegalArgumentException("时长应在 2—720 分钟");
            result.put(value);
          }
        } catch (Exception e) {
          ui.toast(e.getMessage() == null ? "请输入整数分钟" : e.getMessage());
          return;
        }
        Store.put(store.settings(), "lockQuickMinutes", result);
        store.save();
        QuickLockWidget.refresh(act);
        dialog.dismiss();
        ui.toast("已保存小组件时长");
      });
    });
    dialog.show();
  }

  private void scheduledLockCard(LinearLayout body) {
    section(body, "定时锁机", "每天固定时间自动进入");
    LinearLayout card = ui.card(body);
    act.settingsSwitch(card, "启用定时锁机", "lockScheduleEnabled", false);
    String start = store.settings().optString("lockScheduleStart", "22:00");
    String end = store.settings().optString("lockScheduleEnd", "22:30");
    card.addView(ui.text("每天 " + start + " 自动进入锁机，到 " + end + " 自动解除。结束时间早于开始时间时按跨天处理。", 11, ui.muted));
    LinearLayout row = ui.row();
    TextView startButton = ui.outline("开始 " + start, () -> pickScheduleTime(true));
    TextView endButton = ui.outline("结束 " + end, () -> pickScheduleTime(false));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.rightMargin = ui.dp(8);
    row.addView(startButton, lp);
    row.addView(endButton, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
    rowLp.topMargin = ui.dp(8);
    card.addView(row, rowLp);
    card.addView(ui.text("需要开启学霸模式的无障碍权限才能拦截其他应用。定时锁机同样可以在锁机页长按放弃。", 10, ui.muted));
  }

  private void pickScheduleTime(boolean start) {
    String key = start ? "lockScheduleStart" : "lockScheduleEnd";
    String otherKey = start ? "lockScheduleEnd" : "lockScheduleStart";
    String value = store.settings().optString(key, start ? "22:00" : "22:30");
    int[] hm = clock(value, start ? 22 : 22, start ? 0 : 30);
    TimePickerDialog picker = new TimePickerDialog(act, (view, hour, minute) -> {
      String time = String.format(Locale.US, "%02d:%02d", hour, minute);
      String other = store.settings().optString(otherKey, start ? "22:30" : "22:00");
      if (time.equals(other)) {
        ui.toast("开始时间和结束时间不能相同");
        return;
      }
      Store.put(store.settings(), key, time);
      store.save();
      ScheduledLock.schedule(act);
      act.render();
    }, hm[0], hm[1], true);
    picker.show();
  }

  private int[] clock(String value, int fallbackHour, int fallbackMinute) {
    try {
      String[] parts = value.split(":");
      return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    } catch (Exception e) {
      return new int[]{fallbackHour, fallbackMinute};
    }
  }

  private void lockDurationDialog() {
    final int[] minutes = {15, 30, 45, 60, 90};
    String[] labels = {"15 分钟", "30 分钟", "45 分钟", "60 分钟", "90 分钟", "自定义…"};
    ui.show(ui.dialog().setTitle("锁机时长").setItems(labels, (d, which) -> {
      if (which == labels.length - 1) customLockDialog();
      else act.startLock(minutes[which]);
    }));
  }

  private void customLockDialog() {
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    EditText input = ui.input(form, "分钟数（2—720）", "45", InputType.TYPE_CLASS_NUMBER);
    AlertDialog dialog = ui.dialog().setTitle("自定义锁机时长").setView(form)
        .setNegativeButton("取消", null)
        .setPositiveButton("开始", null)
        .create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        int value;
        try {
          value = Integer.parseInt(input.getText().toString().trim());
        } catch (Exception e) {
          ui.toast("请输入整数分钟");
          return;
        }
        if (value < 2 || value > 720) {
          ui.toast("锁机时长应在 2—720 分钟");
          return;
        }
        dialog.dismiss();
        act.startLock(value);
      });
    });
    dialog.show();
  }

  private void relaxCard(LinearLayout body) {
    section(body, "放松", "一分钟深呼吸");
    LinearLayout card = ui.card(body);
    card.addView(ui.text("小憩会让身体在一分钟内慢慢放松。", 12, ui.muted));
    card.addView(ui.outline("开始一分钟小憩", () -> act.startActivity(new Intent(act, RelaxActivity.class))));
  }

  private void mottoDialog() {
    final AlertDialog[] holder = new AlertDialog[1];
    LinearLayout panel = ui.column();
    panel.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    JSONArray mottos = store.mottos();
    for (int i = 0; i < mottos.length(); i++) {
      JSONObject item = mottos.optJSONObject(i);
      if (item == null) continue;
      LinearLayout row = ui.row();
      row.setGravity(Gravity.CENTER_VERTICAL);
      TextView text = ui.text(item.optString("text"), 13, ui.ink);
      text.setPadding(0, ui.dp(8), 0, ui.dp(8));
      row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      if (item.optBoolean("builtin")) {
        row.addView(ui.text("内置", 10, ui.muted));
      } else {
        row.addView(ui.textBtn("×", () -> {
          store.removeMotto(item.optString("id"));
          if (holder[0] != null) holder[0].dismiss();
          mottoDialog();
        }));
      }
      panel.addView(row);
      View line = new View(act);
      line.setBackgroundColor(ui.line);
      panel.addView(line, new LinearLayout.LayoutParams(-1, 1));
    }
    LinearLayout addRow = ui.row();
    EditText input = ui.inputInline(addRow, "添加一句自己的格言", InputType.TYPE_CLASS_TEXT);
    addRow.addView(input, new LinearLayout.LayoutParams(0, ui.dp(42), 1));
    addRow.addView(ui.textBtn("添加", () -> {
      if (store.addMotto(input.getText().toString())) {
        if (holder[0] != null) holder[0].dismiss();
        mottoDialog();
      } else {
        ui.toast("格言为空、重复或超过 60 个字");
      }
    }));
    panel.addView(addRow);

    ScrollView scroll = new ScrollView(act);
    scroll.addView(panel);
    AlertDialog dialog = ui.dialog().setTitle("格言")
        .setView(scroll)
        .setNegativeButton("关闭", null)
        .create();
    holder[0] = dialog;
    dialog.setOnShowListener(v -> ui.style(dialog));
    dialog.show();
  }

  private void appearanceCard(LinearLayout body) {
    section(body, "外观", null);
    LinearLayout card = ui.card(body);
    Switch dark = new Switch(act);
    dark.setText("深色界面");
    dark.setTextSize(13);
    dark.setTextColor(ui.ink);
    dark.setPadding(0, ui.dp(8), 0, ui.dp(8));
    dark.setChecked(store.settings().optBoolean("dark", false));
    int[][] states = {{android.R.attr.state_checked}, {}};
    dark.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{ui.accent, ui.muted}));
    dark.setTrackTintList(new android.content.res.ColorStateList(states, new int[]{ui.accentSoft, ui.line}));
    dark.setOnCheckedChangeListener((v, checked) -> {
      Store.put(store.settings(), "dark", checked);
      store.save();
      act.render();
    });
    card.addView(dark);
    final String[] names = {"番茄红", "珊瑚橙", "薄荷绿", "天空蓝", "紫罗兰", "石墨灰"};
    final int[] colors = {0xffe0604f, 0xffee8c4a, 0xff3fae8f, 0xff3e9dd6, 0xff8a78c8, 0xff5b6572};
    int accent = store.settings().optInt("accent", Store.DEFAULT_ACCENT);
    String accentName = names[0];
    for (int i = 0; i < colors.length; i++) if (colors[i] == accent) accentName = names[i];
    LinearLayout swatches = ui.row();
    swatches.setPadding(0, ui.dp(6), 0, ui.dp(4));
    for (int i = 0; i < colors.length; i++) {
      final int color = colors[i];
      TextView dot = ui.text("●", 22, color);
      dot.setGravity(Gravity.CENTER);
      dot.setBackground(ui.ripple(0, 20));
      if (color == accent) dot.setBackground(ui.stroke(ui.accentSoft, color, 20, 2));
      dot.setOnClickListener(v -> {
        Store.put(store.settings(), "accent", color);
        store.save();
        refreshTimerService();
        act.render();
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(40), 1);
      swatches.addView(dot, lp);
    }
    card.addView(swatches);
    card.addView(ui.text("主题颜色：" + accentName, 10, ui.muted));

    ui.label(card, "计时器外观");
    LinearLayout styles = ui.row();
    String style = store.settings().optString("timerStyle", "normal");
    String[][] styleOptions = {{"normal", "普通"}, {"electronic", "电子"}, {"flip", "翻页"}};
    for (String[] option : styleOptions) {
      styles.addView(ui.chip(option[1], option[0].equals(style), () -> {
        Store.put(store.settings(), "timerStyle", option[0]);
        store.save();
        act.render();
      }));
    }
    card.addView(styles);
    Switch large = new Switch(act);
    large.setText("大号计时文字");
    large.setTextSize(13);
    large.setTextColor(ui.ink);
    large.setPadding(0, ui.dp(8), 0, ui.dp(8));
    large.setChecked(store.settings().optBoolean("timerLarge", false));
    large.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{ui.accent, ui.muted}));
    large.setTrackTintList(new android.content.res.ColorStateList(states, new int[]{ui.accentSoft, ui.line}));
    large.setOnCheckedChangeListener((v, checked) -> {
      Store.put(store.settings(), "timerLarge", checked);
      store.save();
      act.render();
    });
    card.addView(large);
  }

  private void dataCard(LinearLayout body) {
    section(body, "数据与计划", "全部保存在本机");
    LinearLayout card = ui.card(body);
    card.addView(ui.outline("导入 AI 计划 JSON", () -> act.openImport(false)));
    card.addView(ui.textBtn("查看计划格式", this::planFormat));
    card.addView(ui.outline("导出完整备份", () ->
        act.exportFile(store.exportJson(), "专注清单-备份-" + Store.today() + ".json", "application/json")));
    card.addView(ui.outline("恢复本地备份", () -> act.openImport(true)));
    card.addView(ui.outline("导出专注记录 CSV", () ->
        act.exportFile(store.exportCsv(), "专注记录-" + Store.today() + ".csv", "text/csv")));
    card.addView(ui.outline("添加桌面小组件", act::pinWidget));
    card.addView(ui.textBtn("清空本机全部数据", this::clearData));
    card.addView(ui.text("导入计划会替换同一天已导入的任务，保留手动待办与打卡。", 10, ui.muted));
  }

  private void planFormat() {
    String message = "kind: daily-focus-plan\n"
        + "version: 1\n"
        + "days: [\n"
        + "  { date: \"2026-10-30\",\n"
        + "    tasks: [ { id: \"a\", start: \"10:00\", end: \"10:45\",\n"
        + "      category: \"math\", title: \"真题训练\",\n"
        + "      output: \"完成标准\", detail: \"具体说明\" } ],\n"
        + "    lowTasks: [ 可选任务，同样格式 ] }\n"
        + "]\n\n"
        + "时间使用 HH:MM；同一天主任务不能重叠。默认只导入 tasks；导入时可选择把 lowTasks 作为可选任务一并导入。相同日期的已导入任务会被替换，手动任务保留，打卡按任务 ID 保留。";
    ui.show(ui.dialog().setTitle("支持的计划格式").setMessage(message).setPositiveButton("知道了", null));
  }

  private void clearData() {
    if (store.timer() != null) {
      ui.toast("请先结束当前计时");
      return;
    }
    ui.show(ui.dialog().setTitle("清空全部数据？")
        .setMessage("待办、专注记录、设置和导入计划都会被删除，且无法恢复。建议先导出备份。")
        .setNegativeButton("取消", null)
        .setPositiveButton("清空", (d, w) -> {
          store.clearAll();
          ReminderReceiver.schedule(act);
          FocusWidget.refresh(act);
          act.date = Store.today();
          act.page = "tasks";
          act.groupFilter = "全部";
          act.query = "";
          act.statAnchor = Store.today();
          act.render();
          ui.toast("本机数据已清空");
        }));
  }

  private void aboutCard(LinearLayout body) {
    section(body, "关于", null);
    LinearLayout card = ui.card(body);
    String version = "1.0.0";
    try {
      version = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
    } catch (Exception ignored) { }
    card.addView(ui.medium("专注清单 " + version, 15, ui.ink));
    card.addView(ui.text("本地独立实现 · 无账号 · 无云同步 · 不申请网络权限", 11, ui.muted));
    card.addView(ui.text("不含自习室、会员专属功能、账号登录与云同步。功能范围参照番茄 ToDo Android 版（目标版本 10.2.9.508）的公开免费功能整理；界面与交互为独立设计，与官方无关联。", 10, ui.muted));
    card.addView(ui.textBtn("隐私与权限说明", () -> ui.show(ui.dialog().setTitle("隐私说明")
        .setMessage("所有数据只写入本应用私有目录，不联网、不上传。\n\n无障碍权限仅用于学霸模式识别前台应用包名，不读取页面内容、不采集输入。\n悬浮窗权限仅用于显示计时浮窗。\n通知权限用于前台计时与到点提醒。\n所有权限都可以在系统设置中随时关闭。")
        .setPositiveButton("知道了", null))));
  }

  private void openSettings(String action) {
    try {
      act.startActivity(new Intent(action));
    } catch (Exception e) {
      ui.toast("当前系统没有对应的设置页面");
    }
  }

  private void openSettingsUri(String action, Uri uri) {
    try {
      act.startActivity(new Intent(action, uri));
    } catch (Exception e) {
      ui.toast("当前系统没有对应的设置页面");
    }
  }

  private void openSettingsWithPackage(String action) {
    try {
      Intent i = new Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName());
      act.startActivity(i);
    } catch (Exception e) {
      ui.toast("当前系统没有对应的设置页面");
    }
  }

  /** java.util.Collections.sort with a Comparator without importing Collections everywhere. */
  private static final class CollectionsSort {
    static <T> void sort(List<T> list, Comparator<? super T> comparator) {
      java.util.Collections.sort(list, comparator);
    }
  }
}
