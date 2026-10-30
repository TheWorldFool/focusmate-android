package cn.local.focusmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Small view toolkit so the whole app keeps one consistent look without AppCompat. */
public final class Ui {
  public final Activity a;
  public final int accent, accentSoft, ink, muted, line, bg, card, danger, success;
  public final boolean dark;
  private final float density;

  public Ui(Activity a, int accent, boolean dark) {
    this.a = a;
    this.accent = accent;
    this.dark = dark;
    density = a.getResources().getDisplayMetrics().density;
    ink = dark ? Color.rgb(232, 237, 243) : Color.rgb(40, 46, 54);
    muted = dark ? Color.rgb(148, 160, 173) : Color.rgb(126, 137, 150);
    line = dark ? Color.rgb(48, 57, 69) : Color.rgb(231, 235, 240);
    bg = dark ? Color.rgb(16, 20, 26) : Color.rgb(245, 247, 250);
    card = dark ? Color.rgb(27, 33, 42) : Color.WHITE;
    accentSoft = mix(accent, card, dark ? 0.74f : 0.87f);
    danger = Color.rgb(214, 84, 79);
    success = Color.rgb(56, 158, 116);
  }

  public int dp(float v) { return Math.round(v * density); }

  public static int mix(int a, int b, float t) {
    float u = 1f - t;
    return Color.rgb(
        Math.round(Color.red(a) * u + Color.red(b) * t),
        Math.round(Color.green(a) * u + Color.green(b) * t),
        Math.round(Color.blue(a) * u + Color.blue(b) * t));
  }

  public TextView text(String s, float size, int color) {
    TextView t = new TextView(a);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(3), 1f);
    return t;
  }

  public TextView medium(String s, float size, int color) {
    TextView t = text(s, size, color);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  public TextView bold(String s, float size, int color) {
    TextView t = text(s, size, color);
    t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    return t;
  }

  public LinearLayout column() {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public LinearLayout row() {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
  public LinearLayout.LayoutParams weight(int w) { return new LinearLayout.LayoutParams(w, 0, 1); }

  public GradientDrawable rounded(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  public GradientDrawable stroke(int fill, int strokeColor, float radius, float width) {
    GradientDrawable d = rounded(fill, radius);
    d.setStroke(dp(width), strokeColor);
    return d;
  }

  public Drawable ripple(int color, float radius) {
    Drawable base = color == 0 ? null : rounded(color, radius);
    return new RippleDrawable(ColorStateList.valueOf(dark ? 0x33ffffff : 0x22808080), base, null);
  }

  public LinearLayout card() {
    LinearLayout l = column();
    l.setPadding(dp(16), dp(15), dp(16), dp(15));
    l.setBackground(rounded(card, 18));
    return l;
  }

  public LinearLayout card(LinearLayout parent) {
    LinearLayout l = card();
    LinearLayout.LayoutParams p = lp(-1, -2);
    p.bottomMargin = dp(12);
    parent.addView(l, p);
    return l;
  }

  public void space(LinearLayout parent, int height) {
    View v = new View(a);
    parent.addView(v, lp(1, dp(height)));
  }

  public void divider(LinearLayout parent) {
    View v = new View(a);
    v.setBackgroundColor(line);
    LinearLayout.LayoutParams p = lp(-1, Math.max(1, dp(0.7f)));
    p.topMargin = dp(6);
    p.bottomMargin = dp(6);
    parent.addView(v, p);
  }

  public TextView textBtn(String label, Runnable action) {
    TextView b = text(label, 14, accent);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(10), dp(8), dp(10), dp(8));
    b.setBackground(ripple(0, 12));
    b.setClickable(true);
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public TextView iconBtn(String label, Runnable action) {
    TextView b = medium(label, 17, ink);
    b.setGravity(Gravity.CENTER);
    b.setBackground(ripple(card, 19));
    b.setClickable(true);
    b.setFocusable(true);
    b.setContentDescription(label);
    b.setOnClickListener(v -> action.run());
    b.setLayoutParams(lp(dp(38), dp(38)));
    return b;
  }

  public TextView filled(String label, Runnable action) {
    TextView b = medium(label, 15, Color.WHITE);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(18), dp(12), dp(18), dp(12));
    b.setBackground(ripple(accent, 14));
    b.setClickable(true);
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public TextView outline(String label, Runnable action) {
    TextView b = medium(label, 14, accent);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(16), dp(11), dp(16), dp(11));
    b.setBackground(ripple(0, 14));
    b.setClickable(true);
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public TextView chip(String label, boolean selected, Runnable action) {
    TextView b = text(label, 13, selected ? Color.WHITE : muted);
    if (selected) b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(13), dp(8), dp(13), dp(8));
    b.setBackground(selected ? ripple(accent, 19) : ripple(card, 19));
    if (!selected) b.setBackground(stroke(dark ? Color.TRANSPARENT : Color.WHITE, line, 19, 1));
    b.setClickable(true);
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = lp(-2, -2);
    p.rightMargin = dp(7);
    b.setLayoutParams(p);
    return b;
  }

  public TextView label(LinearLayout parent, String label) {
    TextView t = text(label, 12, muted);
    LinearLayout.LayoutParams p = lp(-1, -2);
    p.topMargin = dp(8);
    parent.addView(t, p);
    return t;
  }

  public EditText input(LinearLayout parent, String label, String value, int inputType) {
    if (label != null && !label.isEmpty()) label(parent, label);
    EditText e = new EditText(a);
    e.setText(value == null ? "" : value);
    e.setTextSize(15);
    e.setTextColor(ink);
    e.setHintTextColor(muted);
    e.setInputType(inputType);
    e.setBackground(rounded(dark ? Color.rgb(21, 26, 33) : Color.rgb(246, 248, 251), 11));
    e.setPadding(dp(12), dp(10), dp(12), dp(10));
    LinearLayout.LayoutParams p = lp(-1, -2);
    p.topMargin = dp(5);
    p.bottomMargin = dp(10);
    parent.addView(e, p);
    return e;
  }

  public EditText inputInline(LinearLayout parent, String hint, int inputType) {
    EditText e = new EditText(a);
    e.setHint(hint);
    e.setTextSize(14);
    e.setTextColor(ink);
    e.setHintTextColor(muted);
    e.setInputType(inputType);
    e.setBackground(rounded(dark ? Color.rgb(21, 26, 33) : Color.rgb(246, 248, 251), 10));
    e.setPadding(dp(11), dp(8), dp(11), dp(8));
    return e;
  }

  public Spinner spinner(LinearLayout parent, String[] options, int selected) {
    Spinner s = new Spinner(a);
    s.setAdapter(new ThemedAdapter(a, options));
    if (selected >= 0 && selected < options.length) s.setSelection(selected);
    LinearLayout.LayoutParams p = lp(-1, dp(46));
    p.topMargin = dp(4);
    p.bottomMargin = dp(8);
    parent.addView(s, p);
    return s;
  }

  private final class ThemedAdapter extends ArrayAdapter<String> {
    ThemedAdapter(Context c, String[] items) { super(c, android.R.layout.simple_spinner_item, items); }
    @Override public View getView(int position, View convert, ViewGroup parent) {
      TextView t = convert instanceof TextView ? (TextView) convert : new TextView(a);
      t.setText(getItem(position));
      t.setTextSize(15);
      t.setTextColor(ink);
      t.setPadding(dp(4), dp(9), dp(4), dp(9));
      return t;
    }
    @Override public View getDropDownView(int position, View convert, ViewGroup parent) {
      TextView t = convert instanceof TextView ? (TextView) convert : new TextView(a);
      t.setText(getItem(position));
      t.setTextSize(15);
      t.setTextColor(ink);
      t.setBackgroundColor(card);
      t.setPadding(dp(14), dp(12), dp(14), dp(12));
      return t;
    }
  }

  public AlertDialog.Builder dialog() {
    int theme = dark ? android.R.style.Theme_Material_Dialog_Alert
        : android.R.style.Theme_Material_Light_Dialog_Alert;
    return new AlertDialog.Builder(a, theme);
  }

  public AlertDialog show(AlertDialog.Builder builder) {
    AlertDialog d = builder.create();
    d.setOnShowListener(v -> style(d));
    d.show();
    return d;
  }

  public void style(AlertDialog d) {
    Window w = d.getWindow();
    if (w != null) {
      w.setBackgroundDrawable(rounded(card, 20));
      int width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.92f);
      w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
    try {
      TextView title = d.findViewById(a.getResources().getIdentifier("alertTitle", "id", "android"));
      if (title != null) title.setTextColor(ink);
      TextView message = d.findViewById(android.R.id.message);
      if (message != null) message.setTextColor(ink);
      TextView pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
      if (pos != null) pos.setTextColor(accent);
      TextView neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
      if (neg != null) neg.setTextColor(muted);
      TextView neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
      if (neu != null) neu.setTextColor(muted);
    } catch (Exception ignored) { }
  }

  public void toast(String message) {
    Toast.makeText(a, message, Toast.LENGTH_LONG).show();
  }

  public void window(LinearLayout root) {
    Window w = a.getWindow();
    w.setStatusBarColor(Color.TRANSPARENT);
    w.setNavigationBarColor(Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 30) {
      w.setDecorFitsSystemWindows(false);
      // window() also runs before setContentView on a cold Activity launch.
      // PhoneWindow.getInsetsController() dereferences its decor view directly.
      w.getDecorView();
      WindowInsetsController c = w.getInsetsController();
      if (c != null) {
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        c.setSystemBarsAppearance(dark ? 0 : mask, mask);
      }
    } else {
      int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
      if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      w.getDecorView().setSystemUiVisibility(flags);
    }
    root.setBackgroundColor(bg);
  }

  public static void applyInsets(Activity a, LinearLayout root, LinearLayout bottomBar, Ui ui) {
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      int top;
      int bottom;
      if (Build.VERSION.SDK_INT >= 30) {
        android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        top = bars.top;
        bottom = bars.bottom;
      } else {
        top = insets.getSystemWindowInsetTop();
        bottom = insets.getSystemWindowInsetBottom();
      }
      v.setPadding(0, top, 0, 0);
      if (bottomBar != null) bottomBar.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6) + bottom);
      return insets;
    });
    root.requestApplyInsets();
  }
}
