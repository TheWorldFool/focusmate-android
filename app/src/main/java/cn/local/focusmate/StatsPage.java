package cn.local.focusmate;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 统计与复盘页。 */
public final class StatsPage {
  private final MainActivity act;
  private final Store store;
  private final Ui ui;
  private final String[] rangeNames = {"日", "周", "月", "全部"};

  public StatsPage(MainActivity act) {
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

    LinearLayout ranges = ui.row();
    for (int i = 0; i < rangeNames.length; i++) {
      final int index = i;
      TextView chip = ui.chip(rangeNames[i], act.statRange == i, () -> {
        act.statRange = index;
        act.render();
      });
      ranges.addView(chip);
    }
    body.addView(ranges);
    ui.space(body, 8);

    body.addView(navRow());
    ui.space(body, 4);

    final String from = from();
    final String to = to();
    long minutes = store.total(from, to, null) / 60000;
    int pomodoros = store.sessionCount(from, to, true);
    int tasksDone = store.completedTaskCount(from, to);
    int interrupted = store.sessionCount(from, to, false);
    int streak = store.streak(Store.today());

    LinearLayout summary = ui.card(body);
    summary.addView(ui.text("累计专注", 12, ui.muted));
    summary.addView(ui.medium(Store.minutesText(minutes), 30, ui.ink));
    TextView range = ui.text(rangeLabel() + " · " + (from.equals(to) ? Store.dateHuman(from) : Store.dateHuman(from) + " 至 " + Store.dateHuman(to)), 11, ui.muted);
    range.setPadding(0, ui.dp(2), 0, ui.dp(12));
    summary.addView(range);
    LinearLayout stats = ui.row();
    stats.addView(statBox("番茄", String.valueOf(pomodoros)));
    stats.addView(statBox("完成任务", String.valueOf(tasksDone)));
    stats.addView(statBox("中断", String.valueOf(interrupted)));
    stats.addView(statBox("连续天数", String.valueOf(streak)));
    summary.addView(stats);

    checkinCard(body, from, to);
    usageCard(body);

    LinearLayout chartCard = ui.card(body);
    chartCard.addView(ui.medium(barTitle(), 14, ui.ink));
    Charts.Bars bars = new Charts.Bars(act, ui);
    long[] values;
    String[] labels;
    if (act.statRange == 0) {
      Map<String, Long> hours = store.minutesByHour(from);
      values = new long[24];
      labels = new String[24];
      for (int h = 0; h < 24; h++) {
        values[h] = hours.getOrDefault(String.valueOf(h), 0L) / 60000;
        labels[h] = h % 3 == 0 ? String.valueOf(h) : "";
      }
    } else if (act.statRange == 1) {
      values = new long[7];
      labels = new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
      LocalDate d = LocalDate.parse(from);
      Map<String, Long> days = store.minutesByDay(from, to);
      for (int i = 0; i < 7; i++) values[i] = days.getOrDefault(d.plusDays(i).toString(), 0L) / 60000;
    } else if (act.statRange == 2) {
      int days = YearMonth.from(LocalDate.parse(from)).lengthOfMonth();
      values = new long[days];
      labels = new String[days];
      Map<String, Long> byDay = store.minutesByDay(from, to);
      for (int i = 1; i <= days; i++) {
        values[i - 1] = byDay.getOrDefault(LocalDate.parse(from).withDayOfMonth(i).toString(), 0L) / 60000;
        labels[i - 1] = i == 1 || i % 5 == 0 ? String.valueOf(i) : "";
      }
    } else {
      Map<String, Long> months = minutesByMonth(from, to);
      List<Map.Entry<String, Long>> entries = new ArrayList<>(months.entrySet());
      int start = Math.max(0, entries.size() - 24);
      int size = entries.size() - start;
      values = new long[size];
      labels = new String[size];
      for (int i = 0; i < size; i++) {
        Map.Entry<String, Long> e = entries.get(start + i);
        values[i] = e.getValue() / 60000;
        String[] parts = e.getKey().split("-");
        labels[i] = (i == 0 || i == size - 1 || parts[1].equals("01")) ? parts[1] + "月" : "";
      }
    }
    bars.setData(values, labels);
    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, ui.dp(150));
    barLp.topMargin = ui.dp(10);
    chartCard.addView(bars, barLp);

    if (act.statRange == 1) {
      String weekStart = Store.startOfWeek(act.statAnchor);
      List<JSONObject> weekSessions = store.sessionsBetween(weekStart, Store.endOfWeek(weekStart), 0);
      LinearLayout timelineCard = ui.card(body);
      timelineCard.addView(ui.medium("本周时间轴", 14, ui.ink));
      timelineCard.addView(ui.text(weekSessions.isEmpty()
          ? "这一周还没有专注记录。"
          : "每行一天，色块为专注时段；红色表示中断。", 10, ui.muted));
      Charts.WeekTimeline timeline = new Charts.WeekTimeline(act, ui);
      timeline.setData(weekStart, weekSessions);
      LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(-1, ui.dp(188));
      tlLp.topMargin = ui.dp(8);
      timelineCard.addView(timeline, tlLp);
    }

    LinearLayout heatCard = ui.card(body);
    heatCard.addView(ui.medium("专注热力图", 14, ui.ink));
    Charts.Heat heat = new Charts.Heat(act, ui);
    Store.HeatData hd = store.heat(LocalDate.parse(to), 12);
    heat.setData(hd.start, hd.values);
    LinearLayout.LayoutParams heatLp = new LinearLayout.LayoutParams(-1, ui.dp(126));
    heatLp.topMargin = ui.dp(8);
    heatCard.addView(heat, heatLp);

    Map<String, Long> groups = store.minutesByGroup(from, to);
    if (!groups.isEmpty()) {
      List<Map.Entry<String, Long>> sorted = new ArrayList<>(groups.entrySet());
      Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));
      List<Charts.Distribution.Item> items = new ArrayList<>();
      long other = 0;
      for (int i = 0; i < sorted.size(); i++) {
        Map.Entry<String, Long> e = sorted.get(i);
        if (i < 6) items.add(new Charts.Distribution.Item(e.getKey(), e.getValue() / 60000, store.groupColor(e.getKey())));
        else other += e.getValue();
      }
      if (other > 0) items.add(new Charts.Distribution.Item("其他", other / 60000, ui.muted));
      LinearLayout distCard = ui.card(body);
      distCard.addView(ui.medium("待办集分布", 14, ui.ink));
      Charts.Distribution dist = new Charts.Distribution(act, ui);
      dist.setItems(items);
      distCard.addView(dist);
    }

    Map<String, Integer> reasons = store.reasons(from, to);
    if (!reasons.isEmpty()) {
      LinearLayout reasonCard = ui.card(body);
      reasonCard.addView(ui.medium("中断原因", 14, ui.ink));
      long abandonedMinutes = store.abandonedTotal(from, to) / 60000;
      if (abandonedMinutes > 0) {
        reasonCard.addView(ui.text("中断总时长 " + Store.minutesText(abandonedMinutes), 11, ui.muted));
      }
      for (Map.Entry<String, Integer> e : reasons.entrySet()) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(7), 0, ui.dp(2));
        row.addView(ui.text(e.getKey(), 13, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.text(e.getValue() + " 次", 12, ui.muted));
        reasonCard.addView(row);
      }
    }

    LinearLayout history = ui.card(body);
    LinearLayout historyHead = ui.row();
    historyHead.addView(ui.medium("专注记录", 14, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    historyHead.addView(ui.textBtn("补记", () -> act.editRecord(null)));
    history.addView(historyHead);
    List<JSONObject> sessions = store.sessionsBetween(from, to, 40);
    if (sessions.isEmpty()) {
      history.addView(ui.text("这段时间还没有专注记录。", 12, ui.muted));
    } else {
      String lastDate = "";
      for (JSONObject s : sessions) {
        String day = s.optString("date");
        if (!day.equals(lastDate)) {
          lastDate = day;
          TextView label = ui.text(Store.dateHuman(day), 11, ui.muted);
          label.setPadding(0, ui.dp(10), 0, ui.dp(2));
          history.addView(label);
        }
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));
        LinearLayout col = ui.column();
        String title = s.optString("title");
        if (s.optBoolean("manual")) title += " · 补记";
        col.addView(ui.text(title, 14, ui.ink));
        String time = Instant.ofEpochMilli(s.optLong("start")).atZone(ZoneId.systemDefault())
            .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        String status = s.optBoolean("completed") ? "完成" : "中断" + (s.optString("reason").isEmpty() ? "" : " · " + s.optString("reason"));
        col.addView(ui.text(time + " · " + Store.clockText(s.optLong("duration")) + " · " + status
            + " · " + s.optString("group", "未分组"), 11, ui.muted));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.text("›", 16, ui.muted));
        row.setOnClickListener(v -> act.showRecordMenu(s));
        history.addView(row);
        View line = new View(act);
        line.setBackgroundColor(ui.line);
        history.addView(line, new LinearLayout.LayoutParams(-1, 1));
      }
    }

    LinearLayout noteCard = ui.card(body);
    noteCard.addView(ui.medium("当天复盘", 14, ui.ink));
    String noteDay = act.statAnchor;
    TextView noteLabel = ui.text(Store.dateHuman(noteDay), 11, ui.muted);
    noteLabel.setPadding(0, ui.dp(2), 0, ui.dp(6));
    noteCard.addView(noteLabel);
    EditText note = ui.input(noteCard, null, store.note(noteDay), android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    note.setHint("今天有什么进展？明天先做什么？");
    note.setMinLines(3);
    note.setGravity(Gravity.TOP | Gravity.START);
    TextView saveNote = ui.outline("保存复盘", () -> {
      store.note(noteDay, note.getText().toString());
      ui.toast("已保存到本机");
    });
    noteCard.addView(saveNote, new LinearLayout.LayoutParams(-1, -2));

    return scroll;
  }

  private void checkinCard(LinearLayout body, String from, String to) {
    List<JSONObject> wake = store.checkinsBetween(from, to, "wake");
    List<JSONObject> sleep = store.checkinsBetween(from, to, "sleep");
    LinearLayout card = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.medium("起床 / 睡眠打卡", 14, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.textBtn("打卡", act::checkinDialog));
    card.addView(head);
    if (wake.isEmpty() && sleep.isEmpty()) {
      card.addView(ui.text("这段时间还没有打卡记录。", 12, ui.muted));
      return;
    }
    if (!wake.isEmpty()) {
      card.addView(ui.text("平均起床 " + averageTime(wake) + " · 已打卡 " + wake.size() + " 天", 12, ui.ink));
    }
    if (!sleep.isEmpty()) {
      card.addView(ui.text("平均入睡 " + averageTime(sleep) + " · 已打卡 " + sleep.size() + " 天", 12, ui.ink));
    }
    if (!wake.isEmpty()) addDistribution(card, "起床打卡分布", wake, false);
    if (!sleep.isEmpty()) addDistribution(card, "睡眠打卡分布", sleep, true);
    List<JSONObject> recent = wake.size() > 7 ? wake.subList(wake.size() - 7, wake.size()) : wake;
    if (!recent.isEmpty()) {
      StringBuilder line = new StringBuilder("最近：");
      for (int i = 0; i < recent.size(); i++) {
        JSONObject item = recent.get(i);
        if (i > 0) line.append("   ");
        String day = item.optString("date");
        line.append(day.length() >= 10 ? day.substring(5).replace("-", "/") : day)
            .append(" ").append(item.optString("time"));
      }
      TextView recentView = ui.text(line.toString(), 10, ui.muted);
      recentView.setPadding(0, ui.dp(5), 0, 0);
      card.addView(recentView);
    }
  }

  private void addDistribution(LinearLayout card, String title, List<JSONObject> items, boolean sleep) {
    TextView label = ui.text(title, 12, ui.ink);
    label.setPadding(0, ui.dp(9), 0, 0);
    card.addView(label);
    Charts.Bars bars = new Charts.Bars(act, ui);
    bars.setEmptyText("这段时间还没有打卡记录");
    String[] labels = sleep
        ? new String[]{"21前", "21", "22", "23", "0", "1", "2", "3+"}
        : new String[]{"5前", "5", "6", "7", "8", "9", "10", "11+"};
    bars.setData(distribution(items, sleep), labels);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.dp(118));
    lp.topMargin = ui.dp(4);
    card.addView(bars, lp);
  }

  private long[] distribution(List<JSONObject> items, boolean sleep) {
    long[] buckets = new long[8];
    for (JSONObject item : items) {
      try {
        int minutes = Store.parseTime(item.optString("time"));
        int index;
        if (sleep) {
          int adjusted = minutes / 60;
          if (adjusted < 12) adjusted += 24;
          index = adjusted < 21 ? 0 : Math.min(7, 1 + adjusted - 21);
        } else {
          index = minutes < 5 * 60 ? 0 : Math.min(7, 1 + (minutes - 5 * 60) / 60);
        }
        buckets[index]++;
      } catch (Exception ignored) { }
    }
    return buckets;
  }

  private void usageCard(LinearLayout body) {
    if (!usageAllowed()) {
      LinearLayout card = ui.card(body);
      card.addView(ui.medium("应用使用时长", 14, ui.ink));
      card.addView(ui.text("查看近 7 天各应用的前台运行时间，帮助你发现时间去了哪里。需要系统的「使用情况访问权限」，数据只在本机读取，不会上传。", 11, ui.muted));
      TextView grant = ui.outline("去授予使用情况访问权限", () -> {
        try {
          act.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
              Uri.parse("package:" + act.getPackageName())));
        } catch (Exception e) {
          try { act.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
          catch (Exception ignored) { ui.toast("当前系统没有对应的设置页"); }
        }
      });
      card.addView(grant, new LinearLayout.LayoutParams(-1, -2));
      return;
    }
    UsageStatsManager manager = (UsageStatsManager) act.getSystemService(Context.USAGE_STATS_SERVICE);
    if (manager == null) return;
    long end = System.currentTimeMillis();
    long start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    List<UsageStats> stats;
    try {
      stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
    } catch (Exception e) {
      stats = null;
    }
    Map<String, Long> totals = new HashMap<>();
    if (stats != null) {
      for (UsageStats item : stats) {
        if (item == null || item.getTotalTimeInForeground() <= 0) continue;
        String pkg = item.getPackageName();
        if (pkg == null || pkg.equals("com.android.systemui")) continue;
        totals.put(pkg, totals.getOrDefault(pkg, 0L) + item.getTotalTimeInForeground());
      }
    }
    List<Map.Entry<String, Long>> sorted = new ArrayList<>(totals.entrySet());
    Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));
    long total = 0;
    for (Map.Entry<String, Long> entry : sorted) total += entry.getValue();

    LinearLayout card = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.medium("应用使用时长", 14, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.text("近 7 天", 10, ui.muted));
    card.addView(head);
    if (sorted.isEmpty()) {
      card.addView(ui.text("系统还没有记录到可用的使用数据。", 12, ui.muted));
      return;
    }
    int shown = Math.min(8, sorted.size());
    for (int i = 0; i < shown; i++) {
      Map.Entry<String, Long> entry = sorted.get(i);
      LinearLayout top = ui.row();
      top.setPadding(0, ui.dp(8), 0, ui.dp(2));
      top.addView(ui.text(appLabel(entry.getKey()), 12, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
      top.addView(ui.text(Store.clockText(entry.getValue()), 11, ui.muted));
      card.addView(top);
      Charts.Progress bar = new Charts.Progress(act, ui);
      bar.set(total <= 0 ? 0 : entry.getValue() / (float) total, ui.accent);
      card.addView(bar, new LinearLayout.LayoutParams(-1, ui.dp(5)));
    }
    card.addView(ui.text("共 " + sorted.size() + " 个应用 · 仅读取系统记录，不上传", 10, ui.muted));
  }

  private boolean usageAllowed() {
    try {
      AppOpsManager ops = (AppOpsManager) act.getSystemService(Context.APP_OPS_SERVICE);
      if (ops == null) return false;
      int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), act.getPackageName());
      return mode == AppOpsManager.MODE_ALLOWED;
    } catch (Exception e) {
      return false;
    }
  }

  private String appLabel(String pkg) {
    try {
      PackageManager pm = act.getPackageManager();
      ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
      CharSequence label = pm.getApplicationLabel(info);
      return label == null ? pkg : label.toString();
    } catch (Exception e) {
      return pkg;
    }
  }

  private String averageTime(List<JSONObject> items) {
    int sum = 0;
    int n = 0;
    for (JSONObject item : items) {
      try {
        sum += Store.parseTime(item.optString("time"));
        n++;
      } catch (Exception ignored) { }
    }
    if (n == 0) return "--:--";
    int avg = sum / n;
    return String.format(java.util.Locale.US, "%02d:%02d", avg / 60, avg % 60);
  }

  private View statBox(String label, String value) {
    LinearLayout box = ui.column();
    box.setPadding(0, ui.dp(4), ui.dp(4), 0);
    box.addView(ui.medium(value, 20, ui.ink));
    box.addView(ui.text(label, 10, ui.muted));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    box.setLayoutParams(lp);
    return box;
  }

  private View navRow() {
    LinearLayout row = ui.row();
    if (act.statRange != 3) {
      row.addView(ui.iconBtn("‹", () -> shift(-1)));
    }
    TextView label = ui.medium(navLabel(), 14, ui.ink);
    label.setGravity(Gravity.CENTER);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    if (act.statRange != 3) {
      row.addView(ui.iconBtn("›", () -> shift(1)));
    } else {
      TextView all = ui.text("全部记录", 11, ui.muted);
      all.setGravity(Gravity.CENTER);
      row.addView(all, new LinearLayout.LayoutParams(0, -2, 1));
      label.setVisibility(View.GONE);
    }
    return row;
  }

  private String navLabel() {
    LocalDate d = LocalDate.parse(act.statAnchor);
    if (act.statRange == 0) return Store.dateHuman(d.toString());
    if (act.statRange == 1) return Store.shortDate(Store.startOfWeek(d.toString())) + " — " + Store.shortDate(Store.endOfWeek(d.toString()));
    return d.getYear() + " 年 " + d.getMonthValue() + " 月";
  }

  private String rangeLabel() {
    return rangeNames[Math.min(3, Math.max(0, act.statRange))];
  }

  private String barTitle() {
    if (act.statRange == 0) return "今日专注分布（小时）";
    if (act.statRange == 1) return "本周每日专注";
    if (act.statRange == 2) return "本月每日专注";
    return "每月专注";
  }

  private void shift(int direction) {
    LocalDate d = LocalDate.parse(act.statAnchor);
    if (act.statRange == 0) act.statAnchor = d.plusDays(direction).toString();
    else if (act.statRange == 1) act.statAnchor = d.plusWeeks(direction).toString();
    else {
      YearMonth ym = YearMonth.from(d).plusMonths(direction);
      act.statAnchor = ym.atDay(Math.min(d.getDayOfMonth(), ym.lengthOfMonth())).toString();
    }
    act.render();
  }

  private String from() {
    String anchor = act.statAnchor;
    if (act.statRange == 0) return anchor;
    if (act.statRange == 1) return Store.startOfWeek(anchor);
    if (act.statRange == 2) return Store.startOfMonth(anchor);
    String first = store.firstSessionDate();
    if (first == null) return anchor;
    return first;
  }

  private String to() {
    String anchor = act.statAnchor;
    if (act.statRange == 0) return anchor;
    if (act.statRange == 1) return Store.endOfWeek(anchor);
    if (act.statRange == 2) return Store.endOfMonth(anchor);
    return anchor;
  }

  private Map<String, Long> minutesByMonth(String from, String to) {
    Map<String, Long> map = new TreeMap<>();
    for (JSONObject s : store.sessionsBetween(from, to, 0)) {
      if (!s.optBoolean("completed")) continue;
      String month = s.optString("date");
      if (month.length() < 7) continue;
      month = month.substring(0, 7);
      map.put(month, map.getOrDefault(month, 0L) + s.optLong("duration"));
    }
    return map;
  }
}
