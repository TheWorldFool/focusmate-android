package cn.local.focusmate;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 未来倒计时：把重要的日子放在眼前。 */
public final class FuturePage {
  private final MainActivity act;
  private final Store store;
  private final Ui ui;

  public FuturePage(MainActivity act) {
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

    List<JSONObject> list = sorted();
    LinearLayout intro = ui.card(body);
    intro.setBackground(ui.rounded(ui.accentSoft, 16));
    intro.addView(ui.medium("把重要的日子放在眼前", 16, ui.accent));
    TextView hint = ui.text(list.isEmpty()
        ? "添加考试、截止日期或任何值得期待的日子。"
        : "最近的目标：" + list.get(0).optString("title") + " · " + daysText(list.get(0)), 12, ui.muted);
    hint.setPadding(0, ui.dp(3), 0, 0);
    intro.addView(hint);

    TextView add = ui.filled("添加未来倒计时", () -> openEditor(null));
    LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
    addLp.topMargin = ui.dp(10);
    body.addView(add, addLp);

    if (list.isEmpty()) {
      LinearLayout empty = ui.card(body);
      TextView title = ui.medium("还没有倒计时", 16, ui.ink);
      title.setGravity(Gravity.CENTER);
      empty.addView(title);
      TextView text = ui.text("设一个日期，让每天都有一个方向。", 12, ui.muted);
      text.setGravity(Gravity.CENTER);
      text.setPadding(0, ui.dp(4), 0, ui.dp(8));
      empty.addView(text);
      return scroll;
    }

    for (JSONObject item : list) {
      View card = card(item);
      LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
      cardLp.bottomMargin = ui.dp(10);
      body.addView(card, cardLp);
    }
    return scroll;
  }

  private List<JSONObject> sorted() {
    List<JSONObject> list = new ArrayList<>();
    JSONArray arr = store.future();
    for (int i = 0; i < arr.length(); i++) {
      JSONObject item = arr.optJSONObject(i);
      if (item != null && !item.optString("title").isEmpty()) list.add(item);
    }
    Collections.sort(list, (a, b) -> {
      long da = dayDiff(a);
      long db = dayDiff(b);
      boolean pastA = da < 0;
      boolean pastB = db < 0;
      if (pastA != pastB) return pastA ? 1 : -1;
      int c = a.optString("target").compareTo(b.optString("target"));
      return pastA ? -c : c;
    });
    return list;
  }

  private View card(JSONObject item) {
    long diff = dayDiff(item);
    boolean past = diff < 0;
    LinearLayout card = ui.card();
    card.setBackground(ui.rounded(past ? ui.card : ui.accentSoft, 16));

    LinearLayout row = ui.row();
    row.setGravity(Gravity.CENTER_VERTICAL);

    LinearLayout number = ui.column();
    number.setGravity(Gravity.CENTER);
    TextView big = ui.medium(diff == 0 ? "今天" : String.valueOf(Math.abs(diff)), diff == 0 ? 24 : 34,
        past ? ui.muted : ui.accent);
    big.setGravity(Gravity.CENTER);
    number.addView(big);
    if (diff != 0) {
      TextView unit = ui.text(past ? "天前" : "天", 11, past ? ui.muted : ui.accent);
      unit.setGravity(Gravity.CENTER);
      number.addView(unit);
    }
    LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(ui.dp(82), -2);
    numLp.rightMargin = ui.dp(12);
    row.addView(number, numLp);

    LinearLayout info = ui.column();
    info.addView(ui.medium(item.optString("title"), 17, past ? ui.muted : ui.ink));
    String note = item.optString("note");
    if (!note.isEmpty()) {
      TextView noteView = ui.text(note, 12, ui.muted);
      noteView.setMaxLines(3);
      noteView.setPadding(0, ui.dp(3), 0, 0);
      info.addView(noteView);
    }
    String target = item.optString("target");
    String dateLine = Store.dateHuman(target) + " · " + daysText(item);
    TextView dateView = ui.text(dateLine, 11, past ? ui.muted : ui.accent);
    dateView.setPadding(0, ui.dp(5), 0, 0);
    info.addView(dateView);
    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

    TextView edit = ui.text("›", 22, ui.muted);
    edit.setGravity(Gravity.CENTER);
    row.addView(edit);
    card.addView(row);

    card.setOnClickListener(v -> openEditor(item));
    return card;
  }

  private String daysText(JSONObject item) {
    long diff = dayDiff(item);
    if (diff == 0) return "就是今天";
    if (diff > 0) return "还有 " + diff + " 天";
    return "已过去 " + (-diff) + " 天";
  }

  private long dayDiff(JSONObject item) {
    try {
      return ChronoUnit.DAYS.between(LocalDate.parse(Store.today()), LocalDate.parse(item.optString("target")));
    } catch (Exception e) {
      return 0;
    }
  }

  void openEditor(JSONObject existing) {
    boolean creating = existing == null;
    JSONObject base = creating
        ? Store.obj("id", UUID.randomUUID().toString(), "title", "", "note", "",
            "target", LocalDate.parse(Store.today()).plusDays(30).toString(), "created", System.currentTimeMillis())
        : Store.copy(existing);

    LinearLayout form = ui.column();
    form.setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(6));
    EditText title = ui.input(form, "目标名称", base.optString("title"), InputType.TYPE_CLASS_TEXT);
    title.setSingleLine(true);
    EditText note = ui.input(form, "描述（可选）", base.optString("note"),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    note.setMinLines(2);
    note.setGravity(Gravity.TOP | Gravity.START);

    ui.label(form, "目标日期");
    final String[] target = {base.optString("target")};
    TextView dateButton = ui.outline("", null);
    Runnable updateDate = () -> dateButton.setText(Store.dateHuman(target[0]) + " · " + target[0]);
    updateDate.run();
    dateButton.setOnClickListener(v -> {
      LocalDate d;
      try { d = LocalDate.parse(target[0]); } catch (Exception e) { d = LocalDate.now(); }
      DatePickerDialog picker = new DatePickerDialog(act, (view, y, m, day) -> {
        target[0] = LocalDate.of(y, m + 1, day).toString();
        updateDate.run();
      }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
      picker.show();
    });
    form.addView(dateButton, new LinearLayout.LayoutParams(-1, -2));

    if (!creating) {
      TextView delete = ui.textBtn("删除这个倒计时", () -> {
        ui.show(ui.dialog().setTitle("删除「" + base.optString("title") + "」？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (d, w) -> {
              store.removeFuture(base.optString("id"));
              FutureWidget.refresh(act);
              act.render();
              ui.toast("已删除");
            }));
      });
      delete.setTextColor(ui.muted);
      form.addView(delete);
    }

    ScrollView scroll = new ScrollView(act);
    scroll.addView(form);
    AlertDialog.Builder builder = ui.dialog()
        .setTitle(creating ? "添加未来倒计时" : "编辑未来倒计时")
        .setView(scroll)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", null);
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(v -> {
      ui.style(dialog);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
        String name = title.getText().toString().trim();
        if (name.isEmpty()) {
          ui.toast("请填写目标名称");
          return;
        }
        if (name.length() > 20) {
          ui.toast("名称请控制在 20 个字以内");
          return;
        }
        Store.put(base, "title", name);
        Store.put(base, "note", note.getText().toString().trim());
        Store.put(base, "target", target[0]);
        store.upsertFuture(base);
        FutureWidget.refresh(act);
        dialog.dismiss();
        act.render();
        ui.toast(creating ? "已添加" : "已保存");
      });
    });
    dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
  }
}
