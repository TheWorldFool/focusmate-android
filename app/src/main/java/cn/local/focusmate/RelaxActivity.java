package cn.local.focusmate;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 一分钟深呼吸小憩。 */
public class RelaxActivity extends Activity {
  private static final long SESSION = 60000;
  private static final long CYCLE = 14000;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private Ui ui;
  private BreathView breath;
  private TextView phaseView;
  private TextView remainView;
  private TextView hintView;
  private TextView actionView;
  private long startedAt;
  private boolean running;
  private boolean finished;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!running) return;
      long elapsed = SystemClock.elapsedRealtime() - startedAt;
      if (elapsed >= SESSION) {
        finished = true;
        running = false;
        breath.setScale(0.25f);
        phaseView.setText("完成");
        remainView.setText("这一分钟，只留给自己。");
        hintView.setText("感到放松了吗？");
        actionView.setText("完成");
        return;
      }
      long t = elapsed % CYCLE;
      float scale;
      String phase;
      if (t < 4000) {
        scale = 0.25f + 0.75f * (t / 4000f);
        phase = "吸气";
        hintView.setText("慢慢吸气，让身体舒展开");
      } else if (t < 8000) {
        scale = 1f;
        phase = "屏息";
        hintView.setText("轻轻停住，不用用力");
      } else {
        scale = 1f - 0.75f * ((t - 8000) / 6000f);
        phase = "呼气";
        hintView.setText("缓缓呼气，把肩膀放下来");
      }
      breath.setScale(scale);
      phaseView.setText(phase);
      long left = (SESSION - elapsed + 999) / 1000;
      remainView.setText("还剩 " + left + " 秒");
      handler.postDelayed(this, 50);
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    store = Store.get(this);
    ui = new Ui(this, store.settings().optInt("accent", Store.DEFAULT_ACCENT),
        store.settings().optBoolean("dark", false));
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    build();
    startedAt = SystemClock.elapsedRealtime();
    running = true;
    handler.post(tick);
  }

  private void build() {
    LinearLayout root = ui.column();
    ui.window(root);
    root.setPadding(ui.dp(22), 0, ui.dp(22), 0);

    LinearLayout head = ui.row();
    head.setPadding(0, ui.dp(10), 0, ui.dp(4));
    head.addView(ui.medium("小憩", 22, ui.ink), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.textBtn("关闭", this::finish));
    root.addView(head);

    TextView motto = ui.text("「" + store.mottoForToday() + "」", 12, ui.muted);
    motto.setGravity(Gravity.CENTER);
    motto.setPadding(ui.dp(6), ui.dp(2), ui.dp(6), 0);
    root.addView(motto);

    breath = new BreathView();
    root.addView(breath, new LinearLayout.LayoutParams(-1, 0, 1));

    phaseView = ui.medium("吸气", 26, ui.accent);
    phaseView.setGravity(Gravity.CENTER);
    root.addView(phaseView);
    remainView = ui.text("还剩 60 秒", 12, ui.muted);
    remainView.setGravity(Gravity.CENTER);
    remainView.setPadding(0, ui.dp(4), 0, 0);
    root.addView(remainView);
    hintView = ui.text("慢慢吸气，让身体舒展开", 12, ui.muted);
    hintView.setGravity(Gravity.CENTER);
    hintView.setPadding(0, ui.dp(6), 0, ui.dp(12));
    root.addView(hintView);

    actionView = ui.outline("提前结束", this::finish);
    LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
    actionLp.bottomMargin = ui.dp(18);
    root.addView(actionView, actionLp);

    setContentView(root);
    Ui.applyInsets(this, root, null, ui);
  }

  @Override protected void onResume() {
    super.onResume();
    if (!finished) {
      running = true;
      handler.removeCallbacks(tick);
      handler.post(tick);
    }
  }

  @Override protected void onPause() {
    running = false;
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override protected void onDestroy() {
    handler.removeCallbacks(tick);
    super.onDestroy();
  }

  private final class BreathView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float scale = 0.25f;

    BreathView() {
      super(RelaxActivity.this);
      fill.setColor(ui.accentSoft);
      ring.setStyle(Paint.Style.STROKE);
      ring.setStrokeWidth(ui.dp(2));
      ring.setColor(ui.accent);
      petal.setColor(Ui.mix(ui.accent, ui.bg, 0.72f));
      word.setColor(ui.muted);
      word.setTextAlign(Paint.Align.CENTER);
      word.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
      word.setTextSize(ui.dp(11));
    }

    void setScale(float value) {
      scale = Math.max(0.15f, Math.min(1f, value));
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float cx = getWidth() / 2f;
      float cy = getHeight() / 2f;
      float max = Math.min(getWidth(), getHeight()) * 0.34f;
      float radius = max * 0.62f * scale + max * 0.2f;
      for (int i = 0; i < 8; i++) {
        double a = Math.PI * 2 * i / 8 + Math.PI / 8;
        float px = cx + (float) Math.cos(a) * radius * 1.28f;
        float py = cy + (float) Math.sin(a) * radius * 1.28f;
        canvas.drawCircle(px, py, max * 0.16f * scale + ui.dp(4), petal);
      }
      canvas.drawCircle(cx, cy, radius, fill);
      canvas.drawCircle(cx, cy, radius, ring);
      canvas.drawCircle(cx, cy, radius * 0.62f, ring);
      word.setTextSize(ui.dp(11));
      canvas.drawText("跟着节奏呼吸", cx, cy + ui.dp(4), word);
    }
  }
}
