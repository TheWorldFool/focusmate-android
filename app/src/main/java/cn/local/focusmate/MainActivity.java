package cn.local.focusmate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
  Store store;
  Ui ui;
  final Handler handler = new Handler(Looper.getMainLooper());

  String page = "tasks";
  String date = Store.today();
  String groupFilter = "全部";
  String query = "";
  boolean archivedView = false;
  boolean showDone = true;
  boolean importantOnly = false;

  int statRange = 0;
  String statAnchor = Store.today();
  String calendarMonth = Store.today();

  int idleMinutes = 25;
  String idleMode = "countdown";
  String idleTaskId = "";

  Runnable ticker;
  String seenDay = "";
  Charts.Ring ring;
  TextView timerRoundView;
  TextView timerTodayView;
  LinearLayout bottomBar;
  boolean firstResume = true;

  String pendingExport;
  String pendingExportMime = "application/json";

  boolean changeReceiverOn;
  boolean crashScreen;
  private final BroadcastReceiver changeReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (isFinishing() || isDestroyed()) return;
      render();
    }
  };

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      init(state);
      // A report from an older version must not block a repaired app's startup.
      CrashLog.clear(this);
    } catch (Throwable t) {
      CrashLog.write(this, t);
      showCrashScreen(CrashLog.text(t));
    }
  }

  private void init(Bundle state) {
    store = Store.get(this);
    if (state != null) {
      page = state.getString("page", page);
      date = state.getString("date", date);
      statAnchor = state.getString("statAnchor", statAnchor);
    } else {
      date = Store.today();
      statAnchor = date;
      calendarMonth = date;
    }
    handleIntent(getIntent());
    idleMinutes = store.settings().optInt("focusMinutes", 25);
    seenDay = Store.today();
    render();
    ReminderReceiver.schedule(this);
  }

  private void showCrashScreen(String report) {
    crashScreen = true;
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xfff5f7fa);
    root.setPadding(36, 54, 36, 28);

    TextView title = new TextView(this);
    title.setText("专注清单启动失败");
    title.setTextSize(20);
    title.setTextColor(0xff282e36);
    title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    root.addView(title);

    TextView hint = new TextView(this);
    hint.setText("请把下面的错误信息截图发给开发者，也可以点“重试启动”。你的待办和专注记录不会被清除。");
    hint.setTextSize(12);
    hint.setTextColor(0xff7e8996);
    hint.setPadding(0, 10, 0, 14);
    root.addView(hint);

    ScrollView scroll = new ScrollView(this);
    TextView detail = new TextView(this);
    detail.setText(report);
    detail.setTextSize(10);
    detail.setTextColor(0xff3a424c);
    detail.setTextIsSelectable(true);
    detail.setTypeface(android.graphics.Typeface.MONOSPACE);
    scroll.addView(detail);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

    LinearLayout buttons = new LinearLayout(this);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setPadding(0, 18, 0, 0);

    TextView copy = new TextView(this);
    copy.setText("复制错误信息");
    copy.setTextSize(14);
    copy.setTextColor(0xffe0604f);
    copy.setGravity(Gravity.CENTER);
    copy.setPadding(0, 14, 0, 14);
    copy.setOnClickListener(v -> {
      android.content.ClipboardManager cm =
          (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      if (cm != null) {
        cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
      }
    });
    buttons.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

    TextView retry = new TextView(this);
    retry.setText("重试启动");
    retry.setTextSize(14);
    retry.setTextColor(0xff3fae8f);
    retry.setGravity(Gravity.CENTER);
    retry.setPadding(0, 14, 0, 14);
    retry.setOnClickListener(v -> {
      CrashLog.clear(this);
      recreate();
    });
    buttons.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(buttons);
    setContentView(root);
  }

  private void handleIntent(Intent intent) {
    if (intent == null) return;
    if (intent.getBooleanExtra("openTimer", false) || intent.getBooleanExtra("showTimer", false)) {
      page = "timer";
    }
    if (intent.getBooleanExtra("openTasks", false)) page = "tasks";
    if (intent.getBooleanExtra("openFuture", false)) page = "future";
    if (intent.getBooleanExtra("openStats", false)) page = "stats";
    if (intent.getBooleanExtra("openMe", false)) page = "me";
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (crashScreen) return;
    setIntent(intent);
    handleIntent(intent);
    render();
  }

  @Override protected void onResume() {
    super.onResume();
    if (crashScreen || store == null) return;
    IntentFilter filter = new IntentFilter("cn.local.focusmate.CHANGED");
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(changeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(changeReceiver, filter);
    changeReceiverOn = true;
    JSONObject activeTimer = store.timer();
    if (activeTimer != null && !activeTimer.optBoolean("paused")) {
      try {
        startForegroundService(new Intent(this, FocusService.class).setAction(FocusService.REFRESH));
      } catch (Exception ignored) { }
    }
    String current = Store.today();
    if (!current.equals(seenDay)) {
      if (date.equals(seenDay)) date = current;
      if (statAnchor.equals(seenDay)) statAnchor = current;
      if (calendarMonth.equals(seenDay)) calendarMonth = current;
      seenDay = current;
      render();
    } else if (!firstResume) {
      render();
    }
    firstResume = false;
    if (store.lockActive()) {
      startActivity(new Intent(this, LockActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
  }

  @Override protected void onPause() {
    if (ticker != null) handler.removeCallbacks(ticker);
    if (changeReceiverOn) {
      try { unregisterReceiver(changeReceiver); } catch (Exception ignored) { }
      changeReceiverOn = false;
    }
    super.onPause();
  }

  @Override protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    if (crashScreen) return;
    out.putString("page", page);
    out.putString("date", date);
    out.putString("statAnchor", statAnchor);
  }

  @Override public void onBackPressed() {
    if (crashScreen) { super.onBackPressed(); return; }
    if (page.equals("calendar") || page.equals("future")) { page = "tasks"; render(); return; }
    if (!page.equals("tasks")) { page = "tasks"; render(); return; }
    if (archivedView) { archivedView = false; render(); return; }
    if (!query.isEmpty()) { query = ""; render(); return; }
    super.onBackPressed();
  }

  void go(String target) {
    page = target;
    render();
  }

  // ---------------------------------------------------------------- render

  void render() {
    int accent = store.settings().optInt("accent", Store.DEFAULT_ACCENT);
    boolean dark = store.settings().optBoolean("dark", false);
    ui = new Ui(this, accent, dark);
    ring = null;
    timerRoundView = null;
    timerTodayView = null;

    LinearLayout root = ui.column();
    ui.window(root);
    root.addView(buildToolbar(), new LinearLayout.LayoutParams(-1, -2));

    View content;
    if (page.equals("timer")) content = buildTimerPage();
    else if (page.equals("stats")) content = new StatsPage(this).build();
    else if (page.equals("me")) content = new MePage(this).build();
    else if (page.equals("calendar")) content = buildCalendarPage();
    else if (page.equals("future")) content = new FuturePage(this).build();
    else content = buildTasksPage();
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

    View line = new View(this);
    line.setBackgroundColor(ui.line);
    root.addView(line, new LinearLayout.LayoutParams(-1, 1));
    bottomBar = buildBottomBar();
    root.addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));

    setContentView(root);
    Ui.applyInsets(this, root, bottomBar, ui);
    applyKeepScreen();
    startTicker();
  }

  private void applyKeepScreen() {
    JSONObject timer = store.timer();
    boolean keep = timer != null && !timer.optBoolean("paused")
        && store.settings().optBoolean("keepScreen", false);
    if (keep) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  private LinearLayout buildToolbar() {
    LinearLayout bar = ui.row();
    bar.setPadding(ui.dp(18), ui.dp(10), ui.dp(10), ui.dp(6));
    LinearLayout titles = ui.column();
    String title;
    String subtitle;
    if (page.equals("timer")) {
      title = "专注";
      subtitle = store.timer() == null ? "准备开始一段专注" : "让这一段时间只做一件事";
    } else if (page.equals("stats")) {
      title = "统计";
      subtitle = "看见积累，也看见节奏";
    } else if (page.equals("me")) {
      title = "我的";
      subtitle = "全部数据只保存在本机";
    } else if (page.equals("calendar")) {
      title = "日历";
      subtitle = "点选日期，查看当天安排";
    } else if (page.equals("future")) {
      title = "未来";
      subtitle = "把重要的日子放在眼前";
    } else {
      title = archivedView ? "已归档" : "待办";
      subtitle = archivedView ? "归档的待办不会出现在今天" : Store.dateHuman(date);
    }
    titles.addView(ui.medium(title, 24, ui.ink));
    TextView sub = ui.text(subtitle, 11, ui.muted);
    sub.setPadding(0, ui.dp(1), 0, 0);
    titles.addView(sub);
    bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

    if (page.equals("tasks")) {
      bar.addView(ui.textBtn("打卡", this::checkinDialog));
      bar.addView(ui.iconBtn("+", () -> TaskEditor.open(this, ui, null, date, this::render)));
      View gap = new View(this);
      bar.addView(gap, new LinearLayout.LayoutParams(ui.dp(6), 1));
      bar.addView(ui.iconBtn("···", this::tasksMenu));
    } else if (page.equals("calendar")) {
      bar.addView(ui.textBtn("返回", () -> { page = "tasks"; render(); }));
    } else if (page.equals("future")) {
      bar.addView(ui.iconBtn("+", () -> new FuturePage(this).openEditor(null)));
    } else if (page.equals("timer")) {
      bar.addView(ui.textBtn("记录", () -> {
        statRange = 0;
        statAnchor = Store.today();
        page = "stats";
        render();
      }));
    }
    return bar;
  }

  private LinearLayout buildBottomBar() {
    LinearLayout bar = ui.row();
    bar.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6));
    bar.setBackgroundColor(ui.card);
    String[][] items = {{"tasks", "待办"}, {"timer", "计时"}, {"future", "未来"}, {"stats", "统计"}, {"me", "我的"}};
    String active = page.equals("calendar") ? "tasks" : page;
    for (String[] item : items) {
      boolean selected = active.equals(item[0]);
      TextView tab = ui.medium(item[1], 12, selected ? ui.accent : ui.muted);
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(0, ui.dp(10), 0, ui.dp(10));
      tab.setBackground(selected ? ui.rounded(ui.accentSoft, 13) : ui.ripple(0, 13));
      tab.setClickable(true);
      tab.setFocusable(true);
      tab.setOnClickListener(v -> { page = item[0]; render(); });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.leftMargin = ui.dp(3);
      p.rightMargin = ui.dp(3);
      bar.addView(tab, p);
    }
    return bar;
  }

  // ---------------------------------------------------------------- tasks

  private View buildTasksPage() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout body = ui.column();
    body.setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(26));
    scroll.addView(body);

    body.addView(dateRow(), new LinearLayout.LayoutParams(-1, -2));
    body.addView(groupChips(), new LinearLayout.LayoutParams(-1, -2));
    body.addView(progressRow(), new LinearLayout.LayoutParams(-1, -2));
    View checkinLine = checkinLine();
    if (checkinLine != null) body.addView(checkinLine, new LinearLayout.LayoutParams(-1, -2));

    JSONObject running = store.timer();
    if (running != null) {
      LinearLayout banner = ui.card(body);
      banner.setBackground(ui.rounded(ui.accentSoft, 16));
      banner.addView(ui.medium("正在专注 · " + running.optString("title"), 14, ui.accent));
      TextView hint = ui.text(phaseText(running) + " · 点击返回计时", 11, ui.muted);
      hint.setPadding(0, ui.dp(3), 0, 0);
      banner.addView(hint);
      banner.setOnClickListener(v -> { page = "timer"; render(); });
    }

    List<JSONObject> tasks = store.tasksFor(date, groupFilter, query, showDone, archivedView, importantOnly);
    List<JSONObject> pending = new ArrayList<>();
    List<JSONObject> finished = new ArrayList<>();
    for (JSONObject t : tasks) {
      if (!archivedView && store.done(t, date)) finished.add(t);
      else pending.add(t);
    }

    if (pending.isEmpty() && finished.isEmpty()) {
      emptyState(body);
    } else {
      for (JSONObject t : pending) body.addView(taskCard(t));
      if (!finished.isEmpty()) {
        LinearLayout header = ui.row();
        header.setPadding(0, ui.dp(4), 0, ui.dp(8));
        header.addView(ui.medium("已完成 " + finished.size(), 13, ui.muted));
        body.addView(header);
        for (JSONObject t : finished) body.addView(taskCard(t));
      }
    }
    return scroll;
  }

  private View dateRow() {
    LinearLayout row = ui.row();
    row.setPadding(0, ui.dp(2), 0, ui.dp(8));
    row.addView(ui.iconBtn("‹", () -> {
      date = LocalDate.parse(date).minusDays(1).toString();
      render();
    }));
    TextView center = ui.medium(Store.dateHuman(date) + (date.equals(Store.today()) ? " · 今天" : ""), 15, ui.ink);
    center.setGravity(Gravity.CENTER);
    center.setPadding(0, ui.dp(9), 0, ui.dp(9));
    center.setOnClickListener(v -> pickDate());
    row.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(ui.iconBtn("›", () -> {
      date = LocalDate.parse(date).plusDays(1).toString();
      render();
    }));
    row.addView(ui.textBtn("日历", () -> {
      calendarMonth = date;
      page = "calendar";
      render();
    }));
    return row;
  }

  private void pickDate() {
    LocalDate d = LocalDate.parse(date);
    DatePickerDialog picker = new DatePickerDialog(this, (v, y, m, day) -> {
      date = LocalDate.of(y, m + 1, day).toString();
      render();
    }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
    picker.show();
  }

  private View groupChips() {
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = ui.row();
    chips.setPadding(0, ui.dp(2), 0, ui.dp(6));
    chips.addView(ui.chip("全部", groupFilter.equals("全部"), () -> { groupFilter = "全部"; render(); }));
    for (String name : store.groupNames()) {
      chips.addView(ui.chip(name, groupFilter.equals(name), () -> { groupFilter = name; render(); }));
    }
    chips.addView(ui.chip("重要", importantOnly, () -> { importantOnly = !importantOnly; render(); }));
    scroll.addView(chips);
    return scroll;
  }

  private View checkinLine() {
    if (!date.equals(Store.today())) return null;
    JSONObject wake = store.checkin(date, "wake");
    JSONObject sleep = store.checkin(date, "sleep");
    if (wake == null && sleep == null) return null;
    LinearLayout row = ui.row();
    row.setPadding(0, 0, 0, ui.dp(8));
    StringBuilder text = new StringBuilder();
    if (wake != null) text.append("起床 ").append(wake.optString("time"));
    if (wake != null && sleep != null) text.append("   ·   ");
    if (sleep != null) text.append("睡眠 ").append(sleep.optString("time"));
    row.addView(ui.text(text.toString(), 12, ui.accent), new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(ui.textBtn("修改", this::checkinDialog));
    return row;
  }

  void checkinDialog() {
    String today = Store.today();
    LinearLayout panel = ui.column();
    panel.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    TextView dateView = ui.text(Store.dateHuman(today), 12, ui.muted);
    dateView.setPadding(0, 0, 0, ui.dp(8));
    panel.addView(dateView);
    panel.addView(checkinRow(today, "wake", "起床打卡"));
    panel.addView(checkinRow(today, "sleep", "睡眠打卡"));
    panel.addView(ui.text("记录后可在统计页查看平均起床时间和最近打卡。", 10, ui.muted));
    AlertDialog dialog = ui.dialog().setTitle("打卡")
        .setView(panel)
        .setNegativeButton("关闭", null)
        .create();
    dialog.setOnShowListener(v -> ui.style(dialog));
    dialog.show();
  }

  private View checkinRow(String day, String type, String label) {
    LinearLayout row = ui.row();
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, ui.dp(6), 0, ui.dp(6));
    LinearLayout col = ui.column();
    col.addView(ui.medium(label, 14, ui.ink));
    JSONObject item = store.checkin(day, type);
    TextView value = ui.text(item == null ? "还没有记录" : "已记录 " + item.optString("time"), 11,
        item == null ? ui.muted : ui.accent);
    value.setPadding(0, ui.dp(2), 0, 0);
    col.addView(value);
    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    TextView action = ui.outline(item == null ? "记录" : "修改", null);
    action.setOnClickListener(v -> {
      int hour = LocalTime.now().getHour();
      int minute = LocalTime.now().getMinute();
      if (item != null) {
        try {
          String[] parts = item.optString("time").split(":");
          hour = Integer.parseInt(parts[0]);
          minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) { }
      }
      TimePickerDialog picker = new TimePickerDialog(this, (view, h, m) -> {
        String time = String.format(Locale.US, "%02d:%02d", h, m);
        store.putCheckin(day, type, time);
        value.setText("已记录 " + time);
        value.setTextColor(ui.accent);
        action.setText("修改");
        render();
      }, hour, minute, true);
      picker.show();
    });
    row.addView(action);
    return row;
  }

  private View progressRow() {
    List<JSONObject> all = store.tasksFor(date, "全部", "", true, archivedView, false);
    int done = 0;
    for (JSONObject t : all) if (archivedView || store.done(t, date)) done++;
    long minutes = store.total(date, date, null) / 60000;
    LinearLayout row = ui.row();
    row.setPadding(0, ui.dp(2), 0, ui.dp(8));
    String text = archivedView
        ? "共 " + all.size() + " 项归档"
        : "完成 " + done + "/" + all.size() + " · 专注 " + minutes + " 分钟";
    TextView summary = ui.text(text, 12, ui.muted);
    row.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
    if (!date.equals(Store.today())) {
      row.addView(ui.textBtn("回到今天", () -> { date = Store.today(); render(); }));
    }
    row.addView(ui.textBtn("筛选", this::showFilterMenu));
    return row;
  }

  private View taskCard(JSONObject task) {
    boolean done = store.done(task, date);
    int count = store.count(task, date);
    int goal = store.goal(task);
    LinearLayout card = ui.card();
    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
    cardLp.bottomMargin = ui.dp(10);
    card.setLayoutParams(cardLp);
    LinearLayout top = ui.row();
    top.setGravity(Gravity.TOP);

    TextView check = ui.medium(done ? "✓" : "", 15, done ? Color.WHITE : ui.muted);
    check.setGravity(Gravity.CENTER);
    check.setBackground(done ? ui.rounded(ui.accent, 15)
        : ui.stroke(Color.TRANSPARENT, ui.line, 15, 1.4f));
    check.setOnClickListener(v -> {
      store.toggleDone(task, date);
      FocusWidget.refresh(this);
      render();
    });
    LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30));
    checkLp.topMargin = ui.dp(2);
    top.addView(check, checkLp);

    LinearLayout middle = ui.column();
    LinearLayout titleRow = ui.row();
    if (task.optInt("priority", 0) > 0) {
      boolean urgent = task.optInt("priority", 0) >= 2;
      TextView badge = ui.medium(urgent ? "紧急" : "重要", 9, Color.WHITE);
      badge.setGravity(Gravity.CENTER);
      badge.setBackground(ui.rounded(urgent ? ui.danger : 0xffe0a24a, 5));
      badge.setPadding(ui.dp(5), ui.dp(2), ui.dp(5), ui.dp(2));
      LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
      badgeLp.rightMargin = ui.dp(5);
      titleRow.addView(badge, badgeLp);
    }
    TextView title = ui.medium(task.optString("title"), 15, done ? ui.muted : ui.ink);
    if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    middle.addView(titleRow);

    LinearLayout metaRow = ui.row();
    metaRow.setPadding(0, ui.dp(3), 0, 0);
    TextView dot = ui.text("●", 8, store.groupColor(task.optString("group", "未分组")));
    dot.setPadding(0, 0, ui.dp(4), 0);
    metaRow.addView(dot);
    TextView meta = ui.text(taskMeta(task), 11, ui.muted);
    meta.setMaxLines(2);
    meta.setEllipsize(TextUtils.TruncateAt.END);
    metaRow.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
    middle.addView(metaRow);

    String note = task.optString("note").trim();
    if (!note.isEmpty()) {
      TextView noteView = ui.text(note.replace("\n", "  "), 11, ui.muted);
      noteView.setMaxLines(2);
      noteView.setEllipsize(TextUtils.TruncateAt.END);
      noteView.setPadding(0, ui.dp(3), 0, 0);
      middle.addView(noteView);
    }

    JSONArray subs = task.optJSONArray("subtasks");
    if (subs != null && subs.length() > 0) {
      int subDone = 0;
      for (int i = 0; i < subs.length(); i++) {
        JSONObject s = subs.optJSONObject(i);
        if (s != null && s.optBoolean("done")) subDone++;
      }
      LinearLayout subRow = ui.row();
      subRow.setPadding(0, ui.dp(6), 0, 0);
      TextView subText = ui.text("子任务 " + subDone + "/" + subs.length() + " ›", 11, ui.accent);
      subText.setOnClickListener(v -> showSubtasks(task));
      subRow.addView(subText, new LinearLayout.LayoutParams(0, -2, 1));
      middle.addView(subRow);
    }

    LinearLayout.LayoutParams middleLp = new LinearLayout.LayoutParams(0, -2, 1);
    middleLp.leftMargin = ui.dp(12);
    top.addView(middle, middleLp);
    card.addView(top);

    if (goal > 1 || count > 0) {
      Charts.Progress progress = new Charts.Progress(this, ui);
      progress.set(Math.min(1f, count / (float) goal), done ? ui.success : ui.accent);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.dp(5));
      p.topMargin = ui.dp(10);
      card.addView(progress, p);
    }

    LinearLayout actions = ui.row();
    actions.setPadding(0, ui.dp(9), 0, 0);
    String progressText;
    if ("count".equals(task.optString("mode"))) {
      progressText = (store.weekly(task) ? "本周打卡 " : "今日打卡 ") + count + "/" + goal + " 次";
    } else {
      progressText = "今日 " + count + "/" + goal + " 个番茄";
      int estimate = task.optInt("estimate", 0);
      if (estimate > 1) progressText += " · 预计 " + estimate;
    }
    TextView progressLabel = ui.text(progressText, 11, done ? ui.success : ui.muted);
    actions.addView(progressLabel, new LinearLayout.LayoutParams(0, -2, 1));

    TextView menu = ui.textBtn("···", () -> taskMenu(task));
    actions.addView(menu);
    if ("count".equals(task.optString("mode"))) {
      if (!done) {
        TextView add = ui.filled("+1", () -> {
          store.increment(task, date);
          FocusWidget.refresh(this);
          render();
        });
        add.setTextSize(12);
        add.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));
        actions.addView(add);
      }
    } else {
      TextView start = ui.filled(done ? "再来一次" : "开始", () -> startTask(task));
      start.setTextSize(12);
      start.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));
      actions.addView(start);
    }
    card.addView(actions);
    card.setOnLongClickListener(v -> { taskMenu(task); return true; });
    return card;
  }

  private String taskMeta(JSONObject task) {
    List<String> parts = new ArrayList<>();
    parts.add(task.optString("group", "未分组"));
    String start = task.optString("start");
    String end = task.optString("end");
    if (!start.isEmpty() && !end.isEmpty()) parts.add(start + "—" + end);
    String fixed = task.optString("date");
    if (!fixed.isEmpty()) {
      parts.add(Store.shortDate(fixed));
    } else {
      int mask = task.optInt("repeatMask", 127);
      if (mask == 127) parts.add("每天");
      else if (mask == 31) parts.add("工作日");
      else if (mask == 96) parts.add("周末");
      else if (mask != 0) parts.add("自定义重复");
    }
    if (store.weekly(task)) parts.add("每周目标");
    if (task.optInt("estimate", 0) > 0) parts.add("预计 " + task.optInt("estimate") + " 番茄");
    JSONArray reminders = Store.reminders(task);
    if (reminders.length() > 0) {
      List<String> times = new ArrayList<>();
      for (int i = 0; i < reminders.length(); i++) times.add(reminders.optString(i));
      parts.add("提醒 " + TextUtils.join(" / ", times));
    }
    if (task.optBoolean("timedStrict")) parts.add("定时学霸");
    String tags = task.optString("tags").trim();
    if (!tags.isEmpty()) parts.add(tags.replace(",", " / "));
    return TextUtils.join(" · ", parts);
  }

  private void emptyState(LinearLayout body) {
    LinearLayout card = ui.card(body);
    card.setGravity(Gravity.CENTER);
    ui.space(card, 18);
    TextView title = ui.medium(archivedView ? "归档是空的" : "给今天留下一件事", 17, ui.ink);
    title.setGravity(Gravity.CENTER);
    card.addView(title);
    TextView hint = ui.text(archivedView ? "完成的待办可以归档，保持列表清爽" : "新建待办，或导入 AI 生成的计划", 12, ui.muted);
    hint.setGravity(Gravity.CENTER);
    hint.setPadding(0, ui.dp(6), 0, ui.dp(14));
    card.addView(hint);
    card.addView(ui.filled("新建待办", () -> TaskEditor.open(this, ui, null, date, this::render)));
    card.addView(ui.textBtn("导入计划 JSON", () -> openImport(false)));
    ui.space(card, 16);
  }

  private void startTask(JSONObject task) {
    if (store.timer() != null) {
      page = "timer";
      render();
      return;
    }
    if (!date.equals(Store.today())) {
      ui.toast("新专注会计入今天，而不是正在查看的日期");
    }
    Intent i = new Intent(this, FocusService.class)
        .setAction(FocusService.START)
        .putExtra("taskId", task.optString("id"))
        .putExtra("title", task.optString("title"))
        .putExtra("group", task.optString("group", "未分组"))
        .putExtra("mode", task.optString("mode", "countdown"))
        .putExtra("minutes", task.optInt("minutes", 25))
        .putExtra("rest", task.optInt("restMinutes", 5));
    beginTimer(i);
  }

  private void beginTimer(Intent intent) {
    requestNotificationPermission();
    try {
      startForegroundService(intent);
    } catch (Exception e) {
      ui.toast("无法启动计时服务：" + e.getMessage());
      return;
    }
    page = "timer";
    handler.postDelayed(this::render, 160);
  }

  void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
    }
  }

  // ---------------------------------------------------------------- task menus

  private void showFilterMenu() {
    List<String> options = new ArrayList<>();
    options.add(showDone ? "隐藏已完成" : "显示已完成");
    options.add(importantOnly ? "查看全部优先级" : "只看重要");
    options.add("排序方式");
    options.add("管理待办集");
    options.add(archivedView ? "返回待办" : "查看归档");
    if (!query.isEmpty()) options.add("清除搜索");
    String[] items = options.toArray(new String[0]);
    AlertDialog.Builder b = ui.dialog().setTitle("筛选与排序").setItems(items, (d, which) -> {
      String choice = items[which];
      if (choice.equals("隐藏已完成") || choice.equals("显示已完成")) {
        showDone = !showDone;
      } else if (choice.equals("只看重要") || choice.equals("查看全部优先级")) {
        importantOnly = !importantOnly;
      } else if (choice.equals("排序方式")) {
        showSortDialog();
        return;
      } else if (choice.equals("管理待办集")) {
        manageGroups();
        return;
      } else if (choice.equals("查看归档") || choice.equals("返回待办")) {
        archivedView = !archivedView;
      } else if (choice.equals("清除搜索")) {
        query = "";
      }
      render();
    });
    ui.show(b);
  }

  private void showSortDialog() {
    final String[] labels = {"手动排序", "按优先级", "按时间", "按创建时间"};
    final String[] values = {"manual", "priority", "time", "created"};
    String current = store.settings().optString("sortMode", "manual");
    int selected = 0;
    for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
    ui.show(ui.dialog().setTitle("排序方式").setSingleChoiceItems(labels, selected, (d, which) -> {
      Store.put(store.settings(), "sortMode", values[which]);
      store.save();
      d.dismiss();
      render();
    }));
  }

  private void tasksMenu() {
    List<String> options = new ArrayList<>();
    options.add("搜索待办");
    options.add(showDone ? "隐藏已完成" : "显示已完成");
    options.add(importantOnly ? "查看全部优先级" : "只看重要");
    options.add("排序方式");
    options.add("管理待办集");
    options.add(archivedView ? "返回待办" : "查看归档");
    options.add("导入计划 JSON");
    options.add("未来倒计时");
    String[] items = options.toArray(new String[0]);
    ui.show(ui.dialog().setTitle("更多").setItems(items, (d, which) -> {
      String choice = items[which];
      if (choice.equals("搜索待办")) searchDialog();
      else if (choice.startsWith("隐藏") || choice.startsWith("显示")) { showDone = !showDone; render(); }
      else if (choice.startsWith("只看") || choice.startsWith("查看全部")) { importantOnly = !importantOnly; render(); }
      else if (choice.equals("排序方式")) showSortDialog();
      else if (choice.equals("管理待办集")) manageGroups();
      else if (choice.equals("查看归档") || choice.equals("返回待办")) { archivedView = !archivedView; render(); }
      else if (choice.equals("导入计划 JSON")) openImport(false);
      else if (choice.equals("未来倒计时")) { page = "future"; render(); }
    }));
  }

  private void searchDialog() {
    EditText input = new EditText(this);
    input.setText(query);
    input.setSingleLine(true);
    input.setHint("搜索标题、备注或标签");
    input.setTextColor(ui.ink);
    input.setHintTextColor(ui.muted);
    input.setBackground(ui.rounded(ui.bg, 10));
    input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
    AlertDialog.Builder b = ui.dialog().setTitle("搜索待办").setView(input)
        .setNegativeButton("取消", null)
        .setNeutralButton("清除", (d, w) -> { query = ""; render(); })
        .setPositiveButton("搜索", (d, w) -> { query = input.getText().toString().trim(); render(); });
    AlertDialog d = ui.show(b);
    input.requestFocus();
    d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
  }

  void manageGroups() {
    final List<String> names = store.groupNames();
    String[] items = new String[names.size() + 1];
    for (int i = 0; i < names.size(); i++) items[i] = names.get(i);
    items[names.size()] = "＋ 新建待办集";
    ui.show(ui.dialog().setTitle("待办集").setItems(items, (d, which) -> {
      if (which == names.size()) {
        promptGroupName();
        return;
      }
      String name = names.get(which);
      String[] actions = {"重命名", "删除", "取消"};
      ui.show(ui.dialog().setTitle(name).setItems(actions, (d2, a) -> {
        if (a == 0) renameGroup(name);
        else if (a == 1) deleteGroup(name);
      }));
    }));
  }

  private void promptGroupName() {
    EditText input = new EditText(this);
    input.setHint("待办集名称");
    input.setTextColor(ui.ink);
    input.setHintTextColor(ui.muted);
    input.setBackground(ui.rounded(ui.bg, 10));
    input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
    ui.show(ui.dialog().setTitle("新建待办集").setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("创建", (d, w) -> {
          String name = input.getText().toString().trim();
          if (name.isEmpty()) { ui.toast("名称不能为空"); return; }
          if (!store.addGroup(name)) { ui.toast("同名待办集已存在"); return; }
          groupFilter = name;
          render();
        }));
  }

  private void renameGroup(String name) {
    EditText input = new EditText(this);
    input.setText(name);
    input.setTextColor(ui.ink);
    input.setBackground(ui.rounded(ui.bg, 10));
    input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
    ui.show(ui.dialog().setTitle("重命名待办集").setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", (d, w) -> {
          String value = input.getText().toString().trim();
          if (value.isEmpty()) { ui.toast("名称不能为空"); return; }
          if (store.groupNames().contains(value) && !value.equals(name)) { ui.toast("名称已存在"); return; }
          store.renameGroup(name, value);
          if (groupFilter.equals(name)) groupFilter = value;
          render();
        }));
  }

  private void deleteGroup(String name) {
    if (name.equals("未分组")) { ui.toast("未分组不能删除"); return; }
    ui.show(ui.dialog().setTitle("删除待办集？")
        .setMessage("「" + name + "」中的待办会移到未分组，专注记录保留。")
        .setNegativeButton("取消", null)
        .setPositiveButton("删除", (d, w) -> {
          store.deleteGroup(name);
          if (groupFilter.equals(name)) groupFilter = "全部";
          render();
        }));
  }

  private void taskMenu(JSONObject task) {
    List<String> options = new ArrayList<>();
    if ("count".equals(task.optString("mode"))) options.add("打卡 +1");
    else options.add("开始专注");
    options.add("编辑待办");
    options.add("子任务");
    options.add("设置优先级");
    options.add("移动到待办集");
    options.add("上移");
    options.add("下移");
    options.add("复制待办");
    options.add(task.optBoolean("archived") ? "恢复到待办" : "归档");
    options.add("删除待办");
    String[] items = options.toArray(new String[0]);
    ui.show(ui.dialog().setTitle(task.optString("title")).setItems(items, (d, which) -> {
      String choice = items[which];
      if (choice.equals("开始专注")) startTask(task);
      else if (choice.equals("打卡 +1")) { store.increment(task, date); render(); }
      else if (choice.equals("编辑待办")) TaskEditor.open(this, ui, task, date, this::render);
      else if (choice.equals("子任务")) showSubtasks(task);
      else if (choice.equals("设置优先级")) pickPriority(task);
      else if (choice.equals("移动到待办集")) moveToGroup(task);
      else if (choice.equals("上移")) { store.moveOrder(task.optString("id"), -1); render(); }
      else if (choice.equals("下移")) { store.moveOrder(task.optString("id"), 1); render(); }
      else if (choice.equals("复制待办")) duplicate(task);
      else if (choice.equals("归档") || choice.equals("恢复到待办")) {
        Store.put(task, "archived", !task.optBoolean("archived"));
        store.save();
        ReminderReceiver.schedule(this);
        render();
      } else if (choice.equals("删除待办")) {
        ui.show(ui.dialog().setTitle("删除待办？")
            .setMessage("历史专注记录会保留，但待办不再出现。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (d2, w) -> {
              store.removeTask(task.optString("id"));
              ReminderReceiver.schedule(this);
              render();
            }));
      }
    }));
  }

  private void pickPriority(JSONObject task) {
    String[] labels = {"普通", "重要", "紧急"};
    ui.show(ui.dialog().setTitle("优先级").setSingleChoiceItems(labels, Math.min(2, task.optInt("priority", 0)), (d, which) -> {
      Store.put(task, "priority", which);
      store.save();
      d.dismiss();
      render();
    }));
  }

  private void moveToGroup(JSONObject task) {
    List<String> names = store.groupNames();
    ui.show(ui.dialog().setTitle("移动到待办集").setItems(names.toArray(new String[0]), (d, which) -> {
      Store.put(task, "group", names.get(which));
      store.save();
      render();
    }));
  }

  private void duplicate(JSONObject task) {
    JSONObject copy = Store.copy(task);
    Store.put(copy, "id", UUID.randomUUID().toString());
    Store.put(copy, "title", task.optString("title") + "（副本）");
    Store.put(copy, "counts", new JSONObject());
    Store.put(copy, "created", System.currentTimeMillis());
    Store.put(copy, "order", store.nextOrder());
    store.upsert(copy);
    ReminderReceiver.schedule(this);
    render();
  }

  void showSubtasks(JSONObject task) {
    JSONArray subs = task.optJSONArray("subtasks");
    if (subs == null) subs = new JSONArray();
    LinearLayout panel = ui.column();
    panel.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    final JSONArray items = subs;
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      CheckBox cb = new CheckBox(this);
      cb.setText(item.optString("title"));
      cb.setTextSize(14);
      cb.setTextColor(ui.ink);
      cb.setChecked(item.optBoolean("done"));
      cb.setOnCheckedChangeListener((v, checked) -> {
        Store.put(item, "done", checked);
        store.save();
        render();
      });
      panel.addView(cb);
    }
    if (items.length() == 0) panel.addView(ui.text("还没有子任务，可以在下面添加。", 13, ui.muted));
    LinearLayout addRow = ui.row();
    EditText add = ui.inputInline(addRow, "添加子任务", InputType.TYPE_CLASS_TEXT);
    addRow.addView(add, new LinearLayout.LayoutParams(0, ui.dp(42), 1));
    AlertDialog.Builder builder = ui.dialog().setTitle("子任务").setView(panel)
        .setNegativeButton("关闭", null)
        .setPositiveButton("添加", null);
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        String text = add.getText().toString().trim();
        if (text.isEmpty()) { dialog.dismiss(); return; }
        items.put(Store.obj("id", UUID.randomUUID().toString(), "title", text, "done", false));
        Store.put(task, "subtasks", items);
        store.save();
        dialog.dismiss();
        showSubtasks(task);
        render();
      });
    });
    dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
  }

  // ---------------------------------------------------------------- timer

  private View buildTimerPage() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout body = ui.column();
    body.setPadding(ui.dp(16), ui.dp(0), ui.dp(16), ui.dp(26));
    scroll.addView(body);
    JSONObject timer = store.timer();
    if (timer == null) buildIdleTimer(body);
    else buildRunningTimer(body, timer);
    return scroll;
  }

  private void buildIdleTimer(LinearLayout body) {
    JSONObject selected = idleTaskId.isEmpty() ? null : store.find(idleTaskId);
    if (selected != null && !store.due(selected, Store.today())) {
      selected = null;
      idleTaskId = "";
    }
    int minutes = idleMinutes;

    body.addView(mottoView(store.mottoForToday()));
    Charts.Ring preview = new Charts.Ring(this, ui);
    styleRing(preview);
    preview.set(0f, ClockMath.format(minutes * 60000L), selected == null ? "准备开始" : selected.optString("title"), false);
    body.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(270)));

    LinearLayout taskCard = ui.card(body);
    LinearLayout row = ui.row();
    LinearLayout col = ui.column();
    col.addView(ui.text("当前任务", 11, ui.muted));
    col.addView(ui.medium(selected == null ? "自由专注" : selected.optString("title"), 15, ui.ink));
    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(ui.outline("选择", this::chooseIdleTask));
    taskCard.addView(row);

    ui.label(body, "专注时长");
    HorizontalScrollView hs = new HorizontalScrollView(this);
    hs.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = ui.row();
    int[] presets = {15, 25, 45, 60};
    for (int preset : presets) {
      chips.addView(ui.chip(preset + " 分钟", minutes == preset, () -> {
        idleMinutes = preset;
        render();
      }));
    }
    chips.addView(ui.chip("自定义", false, this::customDuration));
    hs.addView(chips);
    body.addView(hs);

    ui.label(body, "计时方式");
    LinearLayout modeRow = ui.row();
    modeRow.addView(ui.chip("倒计时", "countdown".equals(idleMode), () -> { idleMode = "countdown"; render(); }));
    modeRow.addView(ui.chip("正计时", "stopwatch".equals(idleMode), () -> { idleMode = "stopwatch"; render(); }));
    body.addView(modeRow);

    ui.space(body, 12);
    TextView start = ui.filled("开始专注", this::startFree);
    start.setTextSize(16);
    start.setPadding(ui.dp(18), ui.dp(15), ui.dp(18), ui.dp(15));
    body.addView(start, new LinearLayout.LayoutParams(-1, -2));

    ui.space(body, 14);
    LinearLayout settings = ui.card(body);
    settings.addView(ui.medium("专注设置", 14, ui.ink));
    settingsSwitch(settings, "严格模式（不可暂停）", "strict", false);
    settingsSwitch(settings, "柔和白噪音", "noise", false);
    settingsSwitch(settings, "显示悬浮窗", "overlay", false);
    settingsSwitch(settings, "保持屏幕常亮", "keepScreen", false);

    LinearLayout today = ui.card(body);
    int mins = store.todayMinutes();
    today.addView(ui.medium("今天已专注 " + mins + " 分钟", 14, ui.ink));
    today.addView(ui.text("完成 " + store.todayPomodoros() + " 个番茄 · 连续 " + store.streak(Store.today()) + " 天", 12, ui.muted));
  }

  private void chooseIdleTask() {
    final List<JSONObject> choices = new ArrayList<>();
    choices.add(null);
    for (JSONObject t : store.tasksFor(Store.today(), "全部", "", false, false, false)) {
      if (!"count".equals(t.optString("mode"))) choices.add(t);
    }
    String[] labels = new String[choices.size()];
    labels[0] = "自由专注";
    for (int i = 1; i < choices.size(); i++) {
      JSONObject t = choices.get(i);
      labels[i] = t.optString("title") + " · " + t.optInt("minutes", 25) + " 分钟";
    }
    ui.show(ui.dialog().setTitle("选择任务").setItems(labels, (d, which) -> {
      JSONObject t = choices.get(which);
      if (t == null) {
        idleTaskId = "";
      } else {
        idleTaskId = t.optString("id");
        idleMinutes = t.optInt("minutes", 25);
        idleMode = t.optString("mode", "countdown");
      }
      render();
    }));
  }

  private void customDuration() {
    EditText input = new EditText(this);
    input.setText(String.valueOf(idleMinutes));
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    input.setTextColor(ui.ink);
    input.setBackground(ui.rounded(ui.bg, 10));
    input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
    ui.show(ui.dialog().setTitle("自定义专注分钟数").setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("确定", (d, w) -> {
          try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < 1 || value > 1440) throw new NumberFormatException();
            idleMinutes = value;
            render();
          } catch (Exception e) {
            ui.toast("请输入 1—1440 之间的分钟数");
          }
        }));
  }

  private void startFree() {
    requestNotificationPermission();
    JSONObject task = idleTaskId.isEmpty() ? null : store.find(idleTaskId);
    Intent i = new Intent(this, FocusService.class)
        .setAction(FocusService.START)
        .putExtra("taskId", task == null ? "" : task.optString("id"))
        .putExtra("title", task == null ? "自由专注" : task.optString("title"))
        .putExtra("group", task == null ? "专注" : task.optString("group", "未分组"))
        .putExtra("mode", idleMode)
        .putExtra("minutes", idleMinutes)
        .putExtra("rest", task == null ? store.settings().optInt("restMinutes", 5) : task.optInt("restMinutes", 5));
    beginTimer(i);
  }

  private void buildRunningTimer(LinearLayout body, JSONObject timer) {
    boolean rest = "rest".equals(timer.optString("phase"));
    boolean strict = timer.optBoolean("strict");
    boolean paused = timer.optBoolean("paused");

    TextView title = ui.medium(timer.optString("title"), 20, ui.ink);
    title.setGravity(Gravity.CENTER);
    title.setPadding(0, ui.dp(12), 0, ui.dp(2));
    body.addView(title);
    TextView phaseView = ui.text(rest ? "休息一下" : "正在专注", 12, rest ? ui.success : ui.accent);
    phaseView.setGravity(Gravity.CENTER);
    body.addView(phaseView);
    String motto = timer.optString("motto");
    body.addView(mottoView(motto.isEmpty() ? store.mottoForToday() : motto));

    ring = new Charts.Ring(this, ui);
    styleRing(ring);
    long elapsed = store.elapsed(timer);
    ring.set(initialFraction(timer, elapsed), initialTime(timer, elapsed), rest ? "休息中" : "专注中", rest);
    LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1, ui.dp(270));
    ringLp.topMargin = ui.dp(4);
    body.addView(ring, ringLp);

    timerRoundView = ui.text(roundText(timer), 12, ui.muted);
    timerRoundView.setGravity(Gravity.CENTER);
    LinearLayout roundRow = ui.row();
    roundRow.setGravity(Gravity.CENTER_VERTICAL);
    roundRow.addView(timerRoundView, new LinearLayout.LayoutParams(0, -2, 1));
    roundRow.addView(ui.textBtn("沉浸显示", () -> startActivity(new Intent(this, ImmersiveActivity.class))));
    body.addView(roundRow);
    ui.space(body, 8);

    LinearLayout actions = ui.row();
    if (rest) {
      actions.addView(ui.filled("跳过休息", () -> timerAction(FocusService.SKIP_REST)), new LinearLayout.LayoutParams(0, -2, 1));
      actions.addView(ui.outline("结束", () -> confirmFinish(timer)), new LinearLayout.LayoutParams(0, -2, 1));
    } else {
      if (!strict) {
        actions.addView(ui.filled(paused ? "继续专注" : "暂停", () -> timerAction(paused ? FocusService.RESUME : FocusService.PAUSE)),
            new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(ui.outline("完成", () -> confirmFinish(timer)), new LinearLayout.LayoutParams(0, -2, 1));
      } else {
        actions.addView(ui.filled("完成本次专注", () -> confirmFinish(timer)), new LinearLayout.LayoutParams(0, -2, 1));
      }
    }
    body.addView(actions);

    if (strict) {
      TextView strictHint = ui.text("严格模式中：本次计时不提供暂停", 11, ui.muted);
      strictHint.setGravity(Gravity.CENTER);
      strictHint.setPadding(0, ui.dp(8), 0, ui.dp(4));
      body.addView(strictHint);
    }
    if (!rest) {
      TextView abandon = ui.textBtn("放弃本次专注", this::abandonTimer);
      abandon.setTextColor(ui.muted);
      body.addView(abandon);
    }

    ui.space(body, 10);
    LinearLayout settings = ui.card(body);
    settingsSwitch(settings, "柔和白噪音", "noise", false);
    settingsSwitch(settings, "显示悬浮窗", "overlay", false);
    settingsSwitch(settings, "保持屏幕常亮", "keepScreen", false);

    timerTodayView = ui.text("今天已专注 " + store.todayMinutes() + " 分钟 · 完成 "
        + store.todayPomodoros() + " 个番茄", 12, ui.muted);
    timerTodayView.setGravity(Gravity.CENTER);
    body.addView(timerTodayView);
  }

  private void styleRing(Charts.Ring view) {
    view.setStyle(store.settings().optString("timerStyle", "normal"),
        store.settings().optBoolean("timerLarge", false));
  }

  private TextView mottoView(String text) {
    TextView motto = ui.text(text, 12, ui.muted);
    motto.setGravity(Gravity.CENTER);
    motto.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(2));
    return motto;
  }

  private String roundText(JSONObject t) {
    int rounds = t.optInt("roundsDone", 0);
    int per = Math.max(2, t.optInt("roundsBeforeLong", 4));
    int inCycle = rounds % per;
    StringBuilder dots = new StringBuilder();
    for (int i = 0; i < per; i++) dots.append(i < inCycle ? "●" : "○");
    return "已完成 " + rounds + " 个番茄  " + dots;
  }

  private float initialFraction(JSONObject t, long elapsed) {
    if ("countdown".equals(t.optString("mode"))) {
      long target = t.optLong("target");
      return target <= 0 ? 0 : Math.min(1f, elapsed / (float) target);
    }
    return (elapsed % 60000) / 60000f;
  }

  private String initialTime(JSONObject t, long elapsed) {
    if ("countdown".equals(t.optString("mode"))) {
      return ClockMath.format(ClockMath.remaining(t.optLong("target"), elapsed));
    }
    return ClockMath.format(elapsed);
  }

  private void confirmFinish(JSONObject timer) {
    boolean rest = "rest".equals(timer.optString("phase"));
    String title = rest ? "结束休息？" : "结束本次专注？";
    String message = rest ? "休息不会记入专注统计。" : "将按实际专注时长写入记录。";
    ui.show(ui.dialog().setTitle(title).setMessage(message)
        .setNegativeButton("继续", null)
        .setPositiveButton("结束", (d, w) -> timerAction(FocusService.FINISH)));
  }

  private void abandonTimer() {
    String[] reasons = {"被手机打断", "临时事务", "身体不适", "任务调整", "其他"};
    ui.show(ui.dialog().setTitle("这次为什么中断？").setItems(reasons, (d, which) ->
        timerActionWithExtra(FocusService.CANCEL, reasons[which])));
  }

  private void timerAction(String action) {
    startService(new Intent(this, FocusService.class).setAction(action));
    handler.postDelayed(this::render, 180);
  }

  private void timerActionWithExtra(String action, String reason) {
    startService(new Intent(this, FocusService.class).setAction(action).putExtra("reason", reason));
    handler.postDelayed(this::render, 180);
  }

  private void startTicker() {
    if (ticker != null) handler.removeCallbacks(ticker);
    ticker = null;
    if (!page.equals("timer") || store.timer() == null) return;
    ticker = new Runnable() {
      @Override public void run() {
        if (!page.equals("timer")) return;
        JSONObject t = store.timer();
        if (t == null) { render(); return; }
        if (ring != null) {
          long elapsed = store.elapsed(t);
          boolean rest = "rest".equals(t.optString("phase"));
          ring.set(initialFraction(t, elapsed), initialTime(t, elapsed), rest ? "休息中" : "专注中", rest);
        }
        if (timerRoundView != null) timerRoundView.setText(roundText(t));
        handler.postDelayed(this, 500);
      }
    };
    handler.post(ticker);
  }

  private String phaseText(JSONObject t) {
    if ("rest".equals(t.optString("phase"))) return "休息";
    return t.optBoolean("paused") ? "已暂停" : "专注中";
  }

  void settingsSwitch(LinearLayout parent, String title, String key, boolean fallback) {
    Switch sw = new Switch(this);
    sw.setText(title);
    sw.setTextSize(13);
    sw.setTextColor(ui.ink);
    sw.setPadding(0, ui.dp(8), 0, ui.dp(8));
    sw.setChecked(store.settings().optBoolean(key, fallback));
    int[][] states = {{android.R.attr.state_checked}, {}};
    sw.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{ui.accent, ui.muted}));
    sw.setTrackTintList(new android.content.res.ColorStateList(states, new int[]{ui.accentSoft, ui.line}));
    sw.setOnCheckedChangeListener((v, checked) -> {
      Store.put(store.settings(), key, checked);
      store.save();
      if (key.equals("keepScreen")) applyKeepScreen();
      if (key.equals("lockScheduleEnabled")) ScheduledLock.schedule(this);
      if (key.equals("overlay") && checked && !Settings.canDrawOverlays(this)) {
        ui.toast("请先在「我的」中授予悬浮窗权限");
      }
      if (store.timer() != null) {
        try { startService(new Intent(this, FocusService.class).setAction(FocusService.REFRESH)); } catch (Exception ignored) { }
      }
    });
    parent.addView(sw, new LinearLayout.LayoutParams(-1, -2));
  }

  // ---------------------------------------------------------------- calendar

  private View buildCalendarPage() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = ui.column();
    body.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(24));
    scroll.addView(body);
    YearMonth ym = YearMonth.from(LocalDate.parse(calendarMonth));

    LinearLayout nav = ui.row();
    nav.addView(ui.iconBtn("‹", () -> { calendarMonth = ym.minusMonths(1).atDay(1).toString(); render(); }));
    TextView month = ui.medium(ym.getYear() + " 年 " + ym.getMonthValue() + " 月", 17, ui.ink);
    month.setGravity(Gravity.CENTER);
    nav.addView(month, new LinearLayout.LayoutParams(0, -2, 1));
    nav.addView(ui.iconBtn("›", () -> { calendarMonth = ym.plusMonths(1).atDay(1).toString(); render(); }));
    body.addView(nav);

    java.util.Map<String, int[]> data = new java.util.HashMap<>();
    String start = ym.atDay(1).toString();
    String end = ym.atEndOfMonth().toString();
    int monthTotal = 0;
    int monthDone = 0;
    for (int d = 1; d <= ym.lengthOfMonth(); d++) {
      String iso = ym.atDay(d).toString();
      List<JSONObject> tasks = store.tasksFor(iso, "全部", "", true, false, false);
      int doneCount = 0;
      for (JSONObject t : tasks) if (store.done(t, iso)) doneCount++;
      data.put(iso, new int[]{tasks.size(), doneCount});
      monthTotal += tasks.size();
      monthDone += doneCount;
    }

    Charts.Month view = new Charts.Month(this, ui);
    view.setMonth(ym, date == null ? Store.today() : date, data);
    view.setOnDayClick(iso -> {
      date = iso;
      page = "tasks";
      render();
    });
    body.addView(view);

    LinearLayout legend = ui.row();
    legend.setPadding(0, ui.dp(8), 0, ui.dp(12));
    TextView dot1 = ui.text("●", 9, ui.accent);
    legend.addView(dot1);
    legend.addView(ui.text(" 全部完成   ", 11, ui.muted));
    TextView dot2 = ui.text("●", 9, 0xffe0a24a);
    legend.addView(dot2);
    legend.addView(ui.text(" 仍有未完成", 11, ui.muted));
    body.addView(legend);

    LinearLayout summary = ui.card(body);
    long minutes = store.total(start, end, null) / 60000;
    summary.addView(ui.medium("本月概览", 14, ui.ink));
    summary.addView(ui.text("待办 " + monthTotal + " 项 · 完成 " + monthDone + " 项", 12, ui.muted));
    summary.addView(ui.text("专注 " + Store.minutesText(minutes) + " · 连续 " + store.streak(Store.today()) + " 天", 12, ui.muted));

    TextView todayButton = ui.filled("回到今天", () -> {
      date = Store.today();
      calendarMonth = Store.today();
      page = "tasks";
      render();
    });
    body.addView(todayButton, new LinearLayout.LayoutParams(-1, -2));
    return scroll;
  }

  // ---------------------------------------------------------------- import / export

  void openImport(boolean backup) {
    if (store.timer() != null) {
      ui.toast("请先结束当前计时，再导入或恢复");
      return;
    }
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
        .addCategory(Intent.CATEGORY_OPENABLE)
        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
    startActivityForResult(i, backup ? 41 : 40);
  }

  void exportFile(String content, String name, String mime) {
    pendingExport = content;
    pendingExportMime = mime;
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(i, 42);
  }

  @Override protected void onActivityResult(int request, int result, Intent intent) {
    super.onActivityResult(request, result, intent);
    if (result != RESULT_OK || intent == null || intent.getData() == null) return;
    try {
      if (request == 42) {
        if (pendingExport == null) throw new IllegalStateException("导出内容已丢失，请重试");
        try (OutputStream out = getContentResolver().openOutputStream(intent.getData())) {
          if (out == null) throw new IllegalStateException("无法写入文件");
          out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
        }
        pendingExport = null;
        ui.toast("文件已保存");
        return;
      }
      if (request != 40 && request != 41) return;
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (InputStream in = getContentResolver().openInputStream(intent.getData())) {
        if (in == null) throw new IllegalStateException("无法读取文件");
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
          buffer.write(chunk, 0, n);
          if (buffer.size() > 5 * 1024 * 1024) throw new IllegalArgumentException("文件不能超过 5MB");
        }
      }
      JSONObject json = new JSONObject(buffer.toString("UTF-8"));
      if (request == 40) {
        ui.show(ui.dialog().setTitle("导入这份计划？")
            .setMessage("默认只导入每天的主计划，避免和低能量备用任务重复。选择「含备用任务」时，备用任务会标记为可选一并导入。\n\n会替换文件中同一天的已导入任务，保留手动待办和已有打卡；校验失败时不会改动数据。")
            .setNegativeButton("取消", null)
            .setNeutralButton("含备用任务", (d, w) -> applyImportedPlan(json, true))
            .setPositiveButton("导入主计划", (d, w) -> applyImportedPlan(json, false)));
      } else {
        ui.show(ui.dialog().setTitle("恢复这份备份？")
            .setMessage("会替换本机全部待办、专注记录和设置。建议先导出一份当前备份。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复", (d, w) -> {
              try {
                store.restore(json);
                ReminderReceiver.schedule(this);
                FocusWidget.refresh(this);
                render();
                ui.toast("备份已恢复");
              } catch (Exception e) {
                ui.toast("恢复失败：" + e.getMessage());
              }
            }));
      }
    } catch (Exception e) {
      ui.toast("文件处理失败：" + e.getMessage());
    }
  }

  private void applyImportedPlan(JSONObject json, boolean includeLow) {
    try {
      int count = store.importPlan(json, includeLow);
      JSONArray days = json.optJSONArray("days");
      if (days != null) {
        for (int i = 0; i < days.length(); i++) {
          JSONObject day = days.optJSONObject(i);
          if (day != null && !day.optString("date").isEmpty()) {
            date = day.optString("date");
            break;
          }
        }
      }
      groupFilter = "全部";
      query = "";
      archivedView = false;
      page = "tasks";
      ReminderReceiver.schedule(this);
      render();
      ui.toast("已导入 " + count + " 项任务" + (includeLow ? "（含备用）" : ""));
    } catch (Exception e) {
      ui.toast("导入失败：" + e.getMessage());
    }
  }

  // ---------------------------------------------------------------- records

  void showRecordMenu(JSONObject session) {
    String[] options = {"编辑记录", "删除记录"};
    ui.show(ui.dialog().setTitle(session.optString("title")).setItems(options, (d, which) -> {
      if (which == 0) editRecord(session);
      else {
        ui.show(ui.dialog().setTitle("删除这条记录？")
            .setMessage("相关统计会同步更新；待办打卡不会自动减少。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (d2, w) -> {
              JSONArray next = new JSONArray();
              for (int i = 0; i < store.sessions().length(); i++) {
                JSONObject item = store.sessions().optJSONObject(i);
                if (item != null && !item.optString("id").equals(session.optString("id"))) next.put(item);
              }
              Store.put(store.data, "sessions", next);
              store.save();
              FocusWidget.refresh(this);
              render();
            }));
      }
    }));
  }

  void editRecord(JSONObject original) {
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
    EditText name = ui.input(form, "记录名称", original == null ? "补记专注" : original.optString("title"), InputType.TYPE_CLASS_TEXT);
    EditText day = ui.input(form, "日期 YYYY-MM-DD", original == null ? date : original.optString("date"), InputType.TYPE_CLASS_TEXT);
    EditText time = ui.input(form, "开始时间 HH:MM", original == null ? "10:00"
        : java.time.Instant.ofEpochMilli(original.optLong("start")).atZone(ZoneId.systemDefault())
          .toLocalTime().withSecond(0).withNano(0).toString(), InputType.TYPE_CLASS_TEXT);
    EditText length = ui.input(form, "实际时长（分钟）", original == null ? "25"
        : String.valueOf(original.optLong("duration") / 60000), InputType.TYPE_CLASS_NUMBER);
    EditText group = ui.input(form, "待办集", original == null ? "补记" : original.optString("group", "补记"), InputType.TYPE_CLASS_TEXT);
    ui.label(form, "状态");
    Spinner status = ui.spinner(form, new String[]{"完成", "中断"}, original != null && !original.optBoolean("completed", true) ? 1 : 0);
    EditText reason = ui.input(form, "中断原因（仅中断时填写）", original == null ? "" : original.optString("reason"), InputType.TYPE_CLASS_TEXT);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(form);
    scroll.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,
        Math.round(getResources().getDisplayMetrics().heightPixels * 0.56f)));

    AlertDialog.Builder builder = ui.dialog().setTitle("专注记录").setView(scroll)
        .setNegativeButton("取消", null).setPositiveButton("保存", null);
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        try {
          String title = name.getText().toString().trim();
          if (title.isEmpty()) throw new IllegalArgumentException("名称不能为空");
          LocalDate dayValue = LocalDate.parse(day.getText().toString().trim());
          String timeText = time.getText().toString().trim();
          Store.parseTime(timeText);
          int mins = Integer.parseInt(length.getText().toString().trim());
          if (mins < 1 || mins > 1440) throw new IllegalArgumentException("时长应为 1—1440 分钟");
          long start = dayValue.atTime(LocalTime.parse(timeText)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
          JSONObject s = original == null
              ? Store.obj("id", UUID.randomUUID().toString(), "taskId", "", "manual", true)
              : Store.copy(original);
          Store.put(s, "title", title);
          Store.put(s, "group", group.getText().toString().trim().isEmpty() ? "补记" : group.getText().toString().trim());
          Store.put(s, "date", dayValue.toString());
          Store.put(s, "start", start);
          Store.put(s, "end", start + mins * 60000L);
          Store.put(s, "duration", mins * 60000L);
          Store.put(s, "completed", status.getSelectedItemPosition() == 0);
          Store.put(s, "reason", status.getSelectedItemPosition() == 0 ? "" : reason.getText().toString().trim());
          Store.put(s, "manual", true);
          if (original == null) store.sessions().put(s);
          else {
            for (int i = 0; i < store.sessions().length(); i++) {
              JSONObject item = store.sessions().optJSONObject(i);
              if (item != null && item.optString("id").equals(s.optString("id"))) store.sessions().put(i, s);
            }
          }
          store.save();
          FocusWidget.refresh(this);
          dialog.dismiss();
          render();
        } catch (Exception e) {
          String message = e.getMessage();
          if (message == null || message.isEmpty()) message = "请检查日期、时间和时长";
          ui.toast(message);
        }
      });
    });
    dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
  }

  void toast(String text) { ui.toast(text); }

  void pinWidget() {
    String[] names = {"专注计时（今日时长 / 开始暂停）", "未来倒计时（最近目标）", "专注热力图（近 12 周）", "快速锁机（四个常用时长）"};
    final Class<?>[] providers = {FocusWidget.class, FutureWidget.class, HeatmapWidget.class, QuickLockWidget.class};
    ui.show(ui.dialog().setTitle("添加桌面小组件").setItems(names, (d, which) -> pinWidget(providers[which], names[which])));
  }

  private void pinWidget(Class<?> provider, String name) {
    AppWidgetManager manager = AppWidgetManager.getInstance(this);
    if (manager.isRequestPinAppWidgetSupported()) {
      manager.requestPinAppWidget(new ComponentName(this, provider), null, null);
    } else {
      ui.toast("请在桌面长按空白处，手动添加「" + name + "」");
    }
  }

  void startLock(int minutes) {
    if (store.lockActive()) {
      ui.toast("锁机正在进行");
      startActivity(new Intent(this, LockActivity.class));
      return;
    }
    store.startLock(minutes);
    try {
      startForegroundService(new Intent(this, LockService.class).setAction(LockService.START));
    } catch (Exception e) {
      ui.toast("系统限制了后台服务，锁机仍会显示但可能提前结束");
    }
    startActivity(new Intent(this, LockActivity.class));
    ui.toast("锁机开始，共 " + minutes + " 分钟");
  }
}
