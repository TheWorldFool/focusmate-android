package cn.local.focusmate;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Full create/edit sheet for a task. */
public final class TaskEditor {
  private final MainActivity act;
  private final Store store;
  private final Ui ui;
  private final boolean creating;
  private final JSONObject base;
  private final String defaultDate;
  private final Runnable onSaved;
  private final JSONArray subtasks;
  private final String[] repeatOptions = {"不重复", "每天", "工作日", "周末", "自定义"};
  private final String[] priorityOptions = {"普通", "重要", "紧急"};
  private final String[] modeOptions = {"倒计时", "正计时", "计次打卡"};
  private final String[] cycleOptions = {"每天", "每周"};

  private LinearLayout subList;
  private LinearLayout weekRow;
  private Spinner repeatSpinner;
  private LinearLayout reminderList;
  private CheckBox timedStrictBox;
  private final JSONArray reminders = new JSONArray();
  private JSONArray ownWhitelist;
  private TextView ownWhitelistButton;
  private String planStart;
  private String planEnd;
  private TextView planStartButton;
  private TextView planEndButton;
  private AlertDialog dialog;

  private TaskEditor(MainActivity act, Ui ui, JSONObject existing, String defaultDate, Runnable onSaved) {
    this.act = act;
    this.ui = ui;
    this.store = act.store;
    this.creating = existing == null;
    this.defaultDate = defaultDate;
    this.onSaved = onSaved;
    if (existing == null) {
      base = Store.obj(
          "id", UUID.randomUUID().toString(),
          "title", "",
          "group", "未分组",
          "tags", "",
          "priority", 0,
          "mode", "countdown",
          "minutes", store.settings().optInt("focusMinutes", 25),
          "restMinutes", store.settings().optInt("restMinutes", 5),
          "goal", 1,
          "cycle", "day",
          "estimate", 1,
          "repeatMask", 0,
          "date", defaultDate,
          "reminder", "",
          "note", "",
          "subtasks", new JSONArray(),
          "counts", new JSONObject(),
          "archived", false,
          "order", store.nextOrder(),
          "created", System.currentTimeMillis()
      );
    } else {
      base = Store.copy(existing);
    }
    JSONArray saved = Store.reminders(base);
    for (int i = 0; i < saved.length(); i++) {
      String time = saved.optString(i);
      if (!time.isEmpty()) reminders.put(time);
    }
    sortReminders();
    JSONArray savedWhitelist = base.optJSONArray("whitelist");
    if (savedWhitelist != null) {
      try {
        ownWhitelist = new JSONArray(savedWhitelist.toString());
      } catch (Exception ignored) { }
    }
    planStart = base.optString("start");
    planEnd = base.optString("end");
    JSONArray subs = base.optJSONArray("subtasks");
    try {
      subtasks = subs == null ? new JSONArray() : new JSONArray(subs.toString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void open(MainActivity act, Ui ui, JSONObject existing, String date, Runnable onSaved) {
    new TaskEditor(act, ui, existing, date, onSaved).show();
  }

  private void show() {
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(6));

    final EditText title = ui.input(form, "待办名称", base.optString("title"),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    title.setSingleLine(true);

    ui.label(form, "待办集");
    List<String> groups = store.groupNames();
    String currentGroup = base.optString("group", "未分组");
    int groupIndex = groups.indexOf(currentGroup);
    if (groupIndex < 0) groupIndex = 0;
    Spinner groupSpinner = ui.spinner(form, groups.toArray(new String[0]), groupIndex);

    EditText tags = ui.input(form, "标签（用逗号分隔）", base.optString("tags"), InputType.TYPE_CLASS_TEXT);
    tags.setSingleLine(true);

    LinearLayout first = twoColumns(form);
    LinearLayout left = (LinearLayout) first.getChildAt(0);
    LinearLayout right = (LinearLayout) first.getChildAt(1);
    ui.label(left, "优先级");
    Spinner priority = ui.spinner(left, priorityOptions, Math.min(2, Math.max(0, base.optInt("priority", 0))));
    ui.label(right, "计时方式");
    int modeIndex = "stopwatch".equals(base.optString("mode")) ? 1 : "count".equals(base.optString("mode")) ? 2 : 0;
    Spinner mode = ui.spinner(right, modeOptions, modeIndex);

    LinearLayout second = twoColumns(form);
    left = (LinearLayout) second.getChildAt(0);
    right = (LinearLayout) second.getChildAt(1);
    final EditText minutes = ui.input(left, "专注时长（分钟）", String.valueOf(base.optInt("minutes", 25)), InputType.TYPE_CLASS_NUMBER);
    final EditText rest = ui.input(right, "休息时长（分钟）", String.valueOf(base.optInt("restMinutes", 5)), InputType.TYPE_CLASS_NUMBER);

    LinearLayout third = twoColumns(form);
    left = (LinearLayout) third.getChildAt(0);
    right = (LinearLayout) third.getChildAt(1);
    final TextView goalLabel = ui.label(left, "每日目标次数");
    final EditText goal = ui.input(left, null, String.valueOf(base.optInt("goal", 1)), InputType.TYPE_CLASS_NUMBER);
    final EditText estimate = ui.input(right, "预计番茄数", String.valueOf(base.optInt("estimate", 1)), InputType.TYPE_CLASS_NUMBER);

    LinearLayout cycleBox = ui.column();
    ui.label(cycleBox, "打卡周期");
    final Spinner cycle = ui.spinner(cycleBox, cycleOptions, "week".equals(base.optString("cycle")) ? 1 : 0);
    form.addView(cycleBox);
    final Runnable updateCycleUi = () -> {
      boolean countMode = mode.getSelectedItemPosition() == 2;
      boolean weekly = countMode && cycle.getSelectedItemPosition() == 1;
      cycleBox.setVisibility(countMode ? View.VISIBLE : View.GONE);
      goalLabel.setText(weekly ? "每周目标次数"
          : countMode ? "每日目标次数" : "每日目标番茄数");
    };
    mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        updateCycleUi.run();
      }
      @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    });
    cycle.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        updateCycleUi.run();
      }
      @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    });
    updateCycleUi.run();

    ui.label(form, "重复");
    int mask = base.optInt("repeatMask", 127);
    String fixedDate = base.optString("date");
    int initialRepeat = !fixedDate.isEmpty() ? 0 : mask == 127 ? 1 : mask == 31 ? 2 : mask == 96 ? 3 : 4;
    repeatSpinner = ui.spinner(form, repeatOptions, initialRepeat);

    weekRow = ui.row();
    final CheckBox[] week = new CheckBox[7];
    final String[] weekNames = {"一", "二", "三", "四", "五", "六", "日"};
    int defaultDay = java.time.LocalDate.parse(defaultDate).getDayOfWeek().getValue() - 1;
    for (int i = 0; i < 7; i++) {
      CheckBox cb = new CheckBox(act);
      cb.setText(weekNames[i]);
      cb.setTextSize(12);
      cb.setTextColor(ui.ink);
      cb.setPadding(0, 0, 0, 0);
      cb.setButtonTintList(new ColorStateList(
          new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
          new int[]{ui.accent, ui.muted}));
      boolean checked = mask != 0 ? (mask & (1 << i)) != 0 : i == defaultDay;
      cb.setChecked(checked);
      week[i] = cb;
      weekRow.addView(cb, new LinearLayout.LayoutParams(0, -2, 1));
    }
    form.addView(weekRow);
    weekRow.setVisibility(initialRepeat == 4 ? View.VISIBLE : View.GONE);
    repeatSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        weekRow.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
      }
      @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    });

    ui.label(form, "计划时段（可选）");
    LinearLayout planRow = ui.row();
    planStartButton = ui.outline("", this::pickPlanStart);
    planEndButton = ui.outline("", this::pickPlanEnd);
    LinearLayout.LayoutParams planStartLp = new LinearLayout.LayoutParams(0, -2, 1);
    planStartLp.rightMargin = ui.dp(8);
    planRow.addView(planStartButton, planStartLp);
    planRow.addView(planEndButton, new LinearLayout.LayoutParams(0, -2, 1));
    form.addView(planRow);
    form.addView(ui.textBtn("清除时段", () -> {
      planStart = "";
      planEnd = "";
      updatePlanUi();
    }));
    updatePlanUi();

    ui.label(form, "到点提醒（可添加多个）");
    reminderList = ui.column();
    form.addView(reminderList);
    form.addView(ui.outline("＋ 添加提醒", this::addReminder));
    timedStrictBox = new CheckBox(act);
    timedStrictBox.setText("定时学霸：到点自动开始严格专注");
    timedStrictBox.setTextSize(12);
    timedStrictBox.setTextColor(ui.ink);
    timedStrictBox.setChecked(base.optBoolean("timedStrict"));
    timedStrictBox.setButtonTintList(new ColorStateList(
        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
        new int[]{ui.accent, ui.muted}));
    form.addView(timedStrictBox);
    form.addView(ui.text("开启后，提醒到点且当前没有计时，会自动开始这个待办的番茄钟，并强制启用学霸模式与严格模式。", 10, ui.muted));

    ui.label(form, "独立白名单");
    ownWhitelistButton = ui.outline("", this::pickOwnWhitelist);
    form.addView(ownWhitelistButton);
    form.addView(ui.text("只在这个待办的专注计时中生效；跟随全局时使用学霸模式的应用白名单。", 10, ui.muted));
    updateOwnWhitelistUi();
    updateReminderUi();

    final EditText note = ui.input(form, "备注 / 完成标准", base.optString("note"),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    note.setMinLines(3);
    note.setGravity(Gravity.TOP | Gravity.START);

    ui.label(form, "子任务");
    subList = ui.column();
    form.addView(subList);
    renderSubtasks();

    LinearLayout addRow = ui.row();
    final EditText addInput = ui.inputInline(addRow, "添加一个子任务", InputType.TYPE_CLASS_TEXT);
    addRow.addView(addInput, new LinearLayout.LayoutParams(0, ui.dp(42), 1));
    TextView add = ui.textBtn("添加", () -> {
      String text = addInput.getText().toString().trim();
      if (text.isEmpty()) return;
      subtasks.put(Store.obj("id", UUID.randomUUID().toString(), "title", text, "done", false));
      addInput.setText("");
      renderSubtasks();
    });
    addRow.addView(add);
    form.addView(addRow);

    ScrollView scroll = new ScrollView(act);
    scroll.setFillViewport(false);
    scroll.addView(form);
    int height = Math.round(act.getResources().getDisplayMetrics().heightPixels * 0.64f);
    scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

    AlertDialog.Builder builder = ui.dialog()
        .setTitle(creating ? "新建待办" : "编辑待办")
        .setView(scroll)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", null);
    dialog = builder.create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        try {
          save(title, groupSpinner, tags, priority, mode, cycle, minutes, rest, goal, estimate, note);
        } catch (Exception e) {
          String message = e.getMessage();
          if (e instanceof NumberFormatException || message == null || message.isEmpty()) {
            message = "请检查数字与时间格式";
          }
          ui.toast(message);
        }
      });
    });
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
  }

  private LinearLayout twoColumns(LinearLayout form) {
    LinearLayout row = ui.row();
    row.setGravity(Gravity.TOP);
    LinearLayout left = ui.column();
    LinearLayout right = ui.column();
    LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, -2, 1);
    lpLeft.rightMargin = ui.dp(8);
    LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, -2, 1);
    row.addView(left, lpLeft);
    row.addView(right, lpRight);
    form.addView(row);
    return row;
  }

  private void addReminder() {
    int hour = 20;
    int minute = 0;
    if (reminders.length() > 0) {
      try {
        String[] parts = reminders.optString(reminders.length() - 1).split(":");
        hour = Integer.parseInt(parts[0]);
        minute = Integer.parseInt(parts[1]);
      } catch (Exception ignored) { }
    }
    TimePickerDialog picker = new TimePickerDialog(act, (view, h, m) -> {
      String value = String.format(Locale.US, "%02d:%02d", h, m);
      for (int i = 0; i < reminders.length(); i++) {
        if (value.equals(reminders.optString(i))) {
          updateReminderUi();
          return;
        }
      }
      reminders.put(value);
      sortReminders();
      updateReminderUi();
    }, hour, minute, true);
    picker.show();
  }

  private void sortReminders() {
    List<String> times = new ArrayList<>();
    for (int i = 0; i < reminders.length(); i++) {
      String time = reminders.optString(i);
      if (!time.isEmpty()) times.add(time);
    }
    Collections.sort(times);
    while (reminders.length() > 0) reminders.remove(0);
    for (String time : times) reminders.put(time);
  }

  private void updateReminderUi() {
    if (reminderList == null) return;
    reminderList.removeAllViews();
    if (reminders.length() == 0) {
      reminderList.addView(ui.text("没有提醒，点击下方添加", 11, ui.muted));
    } else {
      for (int i = 0; i < reminders.length(); i++) {
        final int index = i;
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.text("提醒 " + reminders.optString(i), 13, ui.ink),
            new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.textBtn("×", () -> {
          reminders.remove(index);
          updateReminderUi();
        }));
        reminderList.addView(row);
      }
    }
    if (timedStrictBox != null) timedStrictBox.setEnabled(reminders.length() > 0);
  }

  private void pickOwnWhitelist() {
    AppWhitelist.pick(act, ui, "这个待办允许使用的应用", ownWhitelist, true, result -> {
      ownWhitelist = result;
      updateOwnWhitelistUi();
    });
  }

  private void updateOwnWhitelistUi() {
    if (ownWhitelistButton == null) return;
    ownWhitelistButton.setText(ownWhitelist == null
        ? "跟随全局白名单" : "独立白名单（" + ownWhitelist.length() + "）");
  }

  private void pickPlanStart() {
    int[] hm = planClock(planStart, 9, 0);
    TimePickerDialog picker = new TimePickerDialog(act, (view, h, m) -> {
      planStart = String.format(Locale.US, "%02d:%02d", h, m);
      updatePlanUi();
    }, hm[0], hm[1], true);
    picker.show();
  }

  private void pickPlanEnd() {
    int[] hm;
    if (!planEnd.isEmpty()) {
      hm = planClock(planEnd, 10, 0);
    } else if (!planStart.isEmpty()) {
      int[] start = planClock(planStart, 9, 0);
      int total = Math.min(23 * 60 + 59, start[0] * 60 + start[1] + 25);
      hm = new int[]{total / 60, total % 60};
    } else {
      hm = new int[]{10, 0};
    }
    TimePickerDialog picker = new TimePickerDialog(act, (view, h, m) -> {
      planEnd = String.format(Locale.US, "%02d:%02d", h, m);
      updatePlanUi();
    }, hm[0], hm[1], true);
    picker.show();
  }

  private int[] planClock(String value, int fallbackHour, int fallbackMinute) {
    try {
      String[] parts = value.split(":");
      int hour = Integer.parseInt(parts[0]);
      int minute = Integer.parseInt(parts[1]);
      if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new NumberFormatException();
      return new int[]{hour, minute};
    } catch (Exception e) {
      return new int[]{fallbackHour, fallbackMinute};
    }
  }

  private void updatePlanUi() {
    if (planStartButton != null) {
      planStartButton.setText(planStart.isEmpty() ? "开始时间" : "开始 " + planStart);
    }
    if (planEndButton != null) {
      planEndButton.setText(planEnd.isEmpty() ? "结束时间" : "结束 " + planEnd);
    }
  }

  private void renderSubtasks() {
    subList.removeAllViews();
    if (subtasks.length() == 0) {
      TextView empty = ui.text("还没有子任务", 12, ui.muted);
      empty.setPadding(0, ui.dp(2), 0, ui.dp(4));
      subList.addView(empty);
      return;
    }
    for (int i = 0; i < subtasks.length(); i++) {
      final int index = i;
      JSONObject item = subtasks.optJSONObject(i);
      if (item == null) continue;
      LinearLayout row = ui.row();
      CheckBox cb = new CheckBox(act);
      cb.setText(item.optString("title"));
      cb.setTextSize(14);
      cb.setTextColor(ui.ink);
      cb.setChecked(item.optBoolean("done"));
      cb.setPadding(0, ui.dp(4), 0, ui.dp(4));
      cb.setButtonTintList(new ColorStateList(
          new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
          new int[]{ui.accent, ui.muted}));
      cb.setOnCheckedChangeListener((v, checked) -> Store.put(item, "done", checked));
      row.addView(cb, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(ui.textBtn("×", () -> {
        subtasks.remove(index);
        renderSubtasks();
      }));
      subList.addView(row);
    }
  }

  private void save(EditText title, Spinner groupSpinner, EditText tags, Spinner priority,
                    Spinner mode, Spinner cycle, EditText minutes, EditText rest, EditText goal,
                    EditText estimate, EditText note) {
    String name = title.getText().toString().trim();
    if (name.isEmpty()) throw new IllegalArgumentException("请填写待办名称");
    int min = Integer.parseInt(minutes.getText().toString().trim());
    int restMin = Integer.parseInt(rest.getText().toString().trim());
    int goalCount = Integer.parseInt(goal.getText().toString().trim());
    int estimateCount = Integer.parseInt(estimate.getText().toString().trim());
    if (min < 1 || min > 1440) throw new IllegalArgumentException("专注时长应在 1—1440 分钟");
    if (restMin < 0 || restMin > 120) throw new IllegalArgumentException("休息时长应在 0—120 分钟");
    if (goalCount < 1 || goalCount > 999) throw new IllegalArgumentException("目标次数应在 1—999");
    if (estimateCount < 0 || estimateCount > 999) throw new IllegalArgumentException("预计番茄数应为 0—999");
    sortReminders();
    for (int i = 0; i < reminders.length(); i++) Store.parseTime(reminders.optString(i));
    if (planStart.isEmpty() != planEnd.isEmpty()) {
      throw new IllegalArgumentException("开始和结束时间需要同时填写");
    }
    if (!planStart.isEmpty()) {
      int planBegin = Store.parseTime(planStart);
      int planFinish = Store.parseTime(planEnd);
      if (planFinish <= planBegin) throw new IllegalArgumentException("结束时间应晚于开始时间");
    }

    int repeat = repeatSpinner.getSelectedItemPosition();
    int mask;
    String singleDate = "";
    if (repeat == 0) {
      mask = 0;
      singleDate = base.optString("date").isEmpty() ? defaultDate : base.optString("date");
    } else if (repeat == 1) {
      mask = 127;
    } else if (repeat == 2) {
      mask = 31;
    } else if (repeat == 3) {
      mask = 96;
    } else {
      mask = 0;
      for (int i = 0; i < 7; i++) {
        CheckBox cb = (CheckBox) weekRow.getChildAt(i);
        if (cb.isChecked()) mask |= 1 << i;
      }
      if (mask == 0) throw new IllegalArgumentException("自定义重复至少选择一天");
    }
    if (mode.getSelectedItemPosition() == 2 && cycle.getSelectedItemPosition() == 1 && repeat == 0) {
      mask = 127;
      singleDate = "";
    }

    JSONObject t = creating ? base : Store.copy(base);
    Store.put(t, "title", name);
    Store.put(t, "group", groupSpinner.getSelectedItem().toString());
    Store.put(t, "tags", tags.getText().toString().trim());
    Store.put(t, "priority", priority.getSelectedItemPosition());
    Store.put(t, "mode", new String[]{"countdown", "stopwatch", "count"}[mode.getSelectedItemPosition()]);
    Store.put(t, "minutes", min);
    Store.put(t, "restMinutes", restMin);
    Store.put(t, "goal", goalCount);
    Store.put(t, "cycle", mode.getSelectedItemPosition() == 2 && cycle.getSelectedItemPosition() == 1 ? "week" : "day");
    Store.put(t, "estimate", estimateCount);
    Store.put(t, "repeatMask", mask);
    Store.put(t, "date", singleDate);
    Store.put(t, "start", planStart);
    Store.put(t, "end", planEnd);
    Store.put(t, "reminders", reminders);
    Store.put(t, "reminder", reminders.length() > 0 ? reminders.optString(0) : "");
    Store.put(t, "timedStrict", timedStrictBox.isChecked() && reminders.length() > 0);
    if (ownWhitelist == null) t.remove("whitelist");
    else Store.put(t, "whitelist", ownWhitelist);
    Store.put(t, "note", note.getText().toString());
    Store.put(t, "subtasks", subtasks);
    if (creating) Store.put(t, "counts", new JSONObject());
    store.upsert(t);
    ReminderReceiver.schedule(act);
    if (reminders.length() > 0) act.requestNotificationPermission();
    if (onSaved != null) onSaved.run();
    dialog.dismiss();
    ui.toast(creating ? "已添加待办" : "已保存修改");
  }
}
