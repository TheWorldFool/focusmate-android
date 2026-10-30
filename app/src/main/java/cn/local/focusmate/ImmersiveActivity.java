package cn.local.focusmate;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.json.JSONObject;

/** Full-black landscape clock for the current timer. */
public class ImmersiveActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private ClockView clock;
  private TextViewHint hint;

  private final Runnable tick = new Runnable() {
    @Override public void run() {
      JSONObject timer = store.timer();
      clock.setTimer(timer);
      if (timer == null) {
        handler.postDelayed(() -> finish(), 1500);
        return;
      }
      handler.postDelayed(this, 500);
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    store = Store.get(this);
    if (store.timer() == null) {
      finish();
      return;
    }
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    hideSystemUi();

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    clock = new ClockView();
    root.addView(clock, new FrameLayout.LayoutParams(-1, -1));
    hint = new TextViewHint(this);
    FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-2, -2,
        android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
    hintLp.bottomMargin = 28;
    root.addView(hint, hintLp);
    root.setOnClickListener(v -> hint.showBriefly());
    setContentView(root);
  }

  private void hideSystemUi() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused) hideSystemUi();
  }

  @Override protected void onResume() {
    super.onResume();
    hideSystemUi();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override public void onBackPressed() {
    super.onBackPressed();
  }

  private final class ClockView extends View {
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONObject timer;

    ClockView() {
      super(ImmersiveActivity.this);
      timePaint.setColor(Color.WHITE);
      timePaint.setTextAlign(Paint.Align.CENTER);
      timePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
      labelPaint.setColor(0x99FFFFFF);
      labelPaint.setTextAlign(Paint.Align.CENTER);
      labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
      subPaint.setColor(0x66FFFFFF);
      subPaint.setTextAlign(Paint.Align.LEFT);
      subPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    void setTimer(JSONObject value) {
      timer = value;
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (timer == null) return;
      long elapsed = store.elapsed(timer);
      boolean rest = "rest".equals(timer.optString("phase"));
      boolean paused = timer.optBoolean("paused");
      String time = "countdown".equals(timer.optString("mode"))
          ? ClockMath.format(ClockMath.remaining(timer.optLong("target"), elapsed))
          : ClockMath.format(elapsed);
      long phaseSeconds = SystemClock.elapsedRealtime() / 1000;
      int move = (int) ((phaseSeconds / 35) % 5) - 2;
      int dx = (int) (getWidth() * 0.018f * move);
      int dy = (int) (getHeight() * 0.012f * ((phaseSeconds / 70) % 3 - 1));

      float size = Math.min(getHeight() * 0.42f, getWidth() * 0.23f);
      timePaint.setTextSize(size);
      labelPaint.setTextSize(size * 0.075f);
      float cx = getWidth() / 2f + dx;
      float cy = getHeight() / 2f - (timePaint.ascent() + timePaint.descent()) / 2f + dy;
      canvas.drawText(time, cx, cy, timePaint);
      String label = rest ? "休息中" : paused ? "已暂停" : "专注中";
      canvas.drawText(label, cx, getHeight() * 0.78f, labelPaint);

      subPaint.setTextSize(size * 0.065f);
      String title = timer.optString("title");
      if (title.length() > 30) title = title.substring(0, 30) + "…";
      canvas.drawText(title, getWidth() * 0.06f + dx / 2f, getHeight() * 0.16f, subPaint);
      String total = "今日 " + store.todayMinutes() + " 分钟";
      canvas.drawText(total, getWidth() * 0.06f + dx / 2f, getHeight() * 0.24f, subPaint);
    }
  }

  private final class TextViewHint extends android.widget.TextView {
    private final Runnable hide = () -> animate().alpha(0f).setDuration(400).withEndAction(() -> setVisibility(GONE)).start();

    TextViewHint(Activity a) {
      super(a);
      setText("返回键退出沉浸显示");
      setTextColor(0x88FFFFFF);
      setTextSize(11);
      setPadding(18, 6, 18, 6);
      setBackgroundColor(0x33000000);
      setAlpha(0f);
      setVisibility(GONE);
      setOnClickListener(v -> showBriefly());
    }

    void showBriefly() {
      handler.removeCallbacks(hide);
      setVisibility(VISIBLE);
      animate().alpha(1f).setDuration(200).start();
      handler.postDelayed(hide, 2600);
    }

    @Override protected void onDetachedFromWindow() {
      handler.removeCallbacks(hide);
      super.onDetachedFromWindow();
    }
  }
}
