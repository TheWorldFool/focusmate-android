package cn.local.focusmate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hand-drawn charts (no external chart library, no AppCompat). */
public final class Charts {
  private Charts() { }

  public static class Ring extends View {
    private final Ui ui;
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;
    private String time = "25:00";
    private String label = "准备开始";
    private boolean rest;
    private String style = "normal";
    private boolean large;

    public Ring(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      arc.setStyle(Paint.Style.STROKE);
      arc.setStrokeCap(Paint.Cap.ROUND);
      track.setStyle(Paint.Style.STROKE);
      track.setColor(ui.line);
      big.setColor(ui.ink);
      big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
      big.setTextAlign(Paint.Align.CENTER);
      small.setColor(ui.muted);
      small.setTextAlign(Paint.Align.CENTER);
      panel.setColor(0xff23262d);
    }

    public void set(float fraction, String time, String label, boolean rest) {
      this.fraction = Math.max(0, Math.min(1, fraction));
      this.time = time;
      this.label = label;
      this.rest = rest;
      invalidate();
    }

    public void setStyle(String style, boolean large) {
      this.style = style == null || style.isEmpty() ? "normal" : style;
      this.large = large;
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w = getWidth();
      float h = getHeight();
      float cx = w / 2f;
      float cy = h / 2f;
      boolean flip = "flip".equals(style);
      boolean electronic = "electronic".equals(style);
      float stroke = flip ? ui.dp(3) : ui.dp(9);
      float radius = Math.min(w, h) / 2f - stroke * 1.4f - ui.dp(4);
      arc.setStrokeWidth(stroke);
      track.setStrokeWidth(stroke);
      canvas.drawCircle(cx, cy, radius, track);
      arc.setColor(rest ? ui.success : ui.accent);
      RectF box = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
      canvas.drawArc(box, -90f, 360f * fraction, false, arc);

      if (flip) {
        drawFlip(canvas, cx, cy, w, h);
      } else if (electronic) {
        drawElectronic(canvas, cx, cy, w, h);
      } else {
        float size = Math.min(w, h) * (large ? 0.245f : 0.205f);
        big.setColor(ui.ink);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        big.setTextSize(size);
        Paint.FontMetrics fm = big.getFontMetrics();
        canvas.drawText(time, cx, cy - (fm.ascent + fm.descent) / 2f - ui.dp(4), big);
        small.setColor(ui.muted);
        small.setTextSize(ui.dp(13));
        canvas.drawText(label, cx, cy + (large ? ui.dp(46) : ui.dp(38)), small);
      }
    }

    private void drawElectronic(Canvas canvas, float cx, float cy, float w, float h) {
      float size = Math.min(w, h) * (large ? 0.315f : 0.265f);
      big.setTypeface(Typeface.create("monospace", Typeface.BOLD));
      big.setTextSize(size);
      big.setColor(rest ? ui.success : ui.accent);
      Paint.FontMetrics fm = big.getFontMetrics();
      canvas.drawText(time, cx, cy - (fm.ascent + fm.descent) / 2f - ui.dp(2), big);
      small.setColor(ui.muted);
      small.setTextSize(ui.dp(13));
      canvas.drawText(label, cx, cy + size * 0.72f, small);
    }

    private void drawFlip(Canvas canvas, float cx, float cy, float w, float h) {
      int count = Math.max(1, time.length());
      float cardW = Math.min(ui.dp(74), w * 0.84f / count);
      float gap = ui.dp(5);
      float total = count * cardW + (count - 1) * gap;
      float left = cx - total / 2f;
      float cardH = Math.min(ui.dp(92), h * 0.36f);
      float top = cy - cardH / 2f - ui.dp(6);
      big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      big.setTextSize(cardH * 0.62f);
      big.setColor(0xfff4f1e8);
      Paint.FontMetrics fm = big.getFontMetrics();
      for (int i = 0; i < count; i++) {
        char ch = time.charAt(i);
        float x = left + i * (cardW + gap);
        if (ch == ':') {
          panel.setColor(rest ? ui.success : ui.accent);
          float dot = Math.max(ui.dp(3), cardH * 0.055f);
          canvas.drawCircle(x + cardW / 2f, cy - cardH * 0.16f, dot, panel);
          canvas.drawCircle(x + cardW / 2f, cy + cardH * 0.16f, dot, panel);
          continue;
        }
        RectF card = new RectF(x, top, x + cardW, top + cardH);
        panel.setColor(0xff23262d);
        canvas.drawRoundRect(card, ui.dp(8), ui.dp(8), panel);
        canvas.drawText(String.valueOf(ch), x + cardW / 2f,
            top + cardH / 2f - (fm.ascent + fm.descent) / 2f, big);
      }
      small.setColor(ui.muted);
      small.setTextSize(ui.dp(13));
      canvas.drawText(label, cx, top + cardH + ui.dp(20), small);
    }
  }

  public static class Bars extends View {
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values = new long[0];
    private String[] labels = new String[0];
    private String emptyText = "这段时间还没有专注记录";

    public Bars(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      labelPaint.setColor(ui.muted);
      labelPaint.setTextAlign(Paint.Align.CENTER);
      emptyPaint.setColor(ui.muted);
      emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(long[] values, String[] labels) {
      this.values = values == null ? new long[0] : values;
      this.labels = labels == null ? new String[0] : labels;
      invalidate();
    }

    public void setEmptyText(String text) {
      this.emptyText = text == null ? "" : text;
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      int n = values.length;
      if (n == 0) return;
      float left = ui.dp(4);
      float right = getWidth() - ui.dp(4);
      float top = ui.dp(16);
      float bottom = getHeight() - ui.dp(22);
      long max = 1;
      long sum = 0;
      for (long v : values) { max = Math.max(max, v); sum += v; }
      if (sum <= 0) {
        emptyPaint.setTextSize(ui.dp(13));
        canvas.drawText(emptyText, getWidth() / 2f, getHeight() / 2f, emptyPaint);
        return;
      }
      float slot = (right - left) / n;
      float gap = Math.min(ui.dp(4), slot * 0.28f);
      float bw = Math.max(ui.dp(3), slot - gap);
      for (int i = 0; i < n; i++) {
        float bh = (bottom - top) * values[i] / (float) max;
        if (values[i] > 0 && bh < ui.dp(3)) bh = ui.dp(3);
        paint.setColor(ui.accent);
        float x = left + i * slot + (slot - bw) / 2f;
        RectF r = new RectF(x, bottom - bh, x + bw, bottom);
        canvas.drawRoundRect(r, ui.dp(3), ui.dp(3), paint);
      }
      labelPaint.setTextSize(ui.dp(9));
      int step = n <= 8 ? 1 : n <= 16 ? 2 : Math.max(1, n / 8);
      for (int i = 0; i < n; i += step) {
        if (i < labels.length) {
          float x = left + i * slot + slot / 2f;
          canvas.drawText(labels[i], x, getHeight() - ui.dp(6), labelPaint);
        }
      }
    }
  }

  public static class Heat extends View {
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values = new long[84];
    private LocalDate start = LocalDate.now().minusDays(83);

    public Heat(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      textPaint.setColor(ui.muted);
      textPaint.setTextSize(ui.dp(9));
      textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(LocalDate start, long[] values) {
      this.start = start;
      this.values = values == null ? new long[84] : values;
      invalidate();
    }

    private int colorFor(long minutes) {
      if (minutes <= 0) return ui.dark ? Color.rgb(34, 41, 50) : Color.rgb(235, 239, 243);
      if (minutes < 30) return Ui.mix(ui.accent, ui.bg, ui.dark ? 0.55f : 0.68f);
      if (minutes < 60) return Ui.mix(ui.accent, ui.bg, ui.dark ? 0.38f : 0.50f);
      if (minutes < 120) return Ui.mix(ui.accent, ui.bg, ui.dark ? 0.20f : 0.30f);
      if (minutes < 180) return Ui.mix(ui.accent, ui.bg, ui.dark ? 0.08f : 0.13f);
      return ui.accent;
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      int cols = 12;
      int rows = 7;
      float availW = getWidth() - ui.dp(6);
      float availH = getHeight() - ui.dp(16);
      float cell = Math.min(availW / cols, availH / rows);
      float gap = Math.max(ui.dp(2), cell * 0.14f);
      float ox = (getWidth() - cell * cols) / 2f;
      float oy = (getHeight() - cell * rows - ui.dp(12)) / 2f;
      for (int i = 0; i < 84; i++) {
        long v = i < values.length ? values[i] : 0;
        paint.setColor(colorFor(v));
        float x = ox + (i / 7) * cell;
        float y = oy + (i % 7) * cell;
        canvas.drawRoundRect(new RectF(x + gap / 2, y + gap / 2, x + cell - gap / 2, y + cell - gap / 2),
            gap / 2, gap / 2, paint);
      }
      textPaint.setTextSize(ui.dp(9));
      canvas.drawText("12 周专注热力图", getWidth() / 2f, getHeight() - ui.dp(2), textPaint);
    }
  }

  public static class Progress extends View {
    private final Ui ui;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;
    private int fillColor;
    private int trackColor;

    public Progress(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      fillColor = ui.accent;
      trackColor = ui.line;
    }

    public void set(float fraction, int fillColor) {
      this.fraction = Math.max(0, Math.min(1, fraction));
      this.fillColor = fillColor;
      invalidate();
    }

    public void setTrack(int color) { trackColor = color; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float h = getHeight();
      float r = h / 2f;
      paint.setColor(trackColor);
      canvas.drawRoundRect(new RectF(0, 0, getWidth(), h), r, r, paint);
      if (fraction > 0) {
        paint.setColor(fillColor);
        canvas.drawRoundRect(new RectF(0, 0, Math.max(h, getWidth() * fraction), h), r, r, paint);
      }
    }
  }

  public static class Distribution extends View {
    public static class Item {
      public final String label;
      public final long minutes;
      public final int color;
      public Item(String label, long minutes, int color) {
        this.label = label;
        this.minutes = minutes;
        this.color = color;
      }
    }

    private final Ui ui;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Item> items = new ArrayList<>();
    private long max = 1;

    public Distribution(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      namePaint.setColor(ui.ink);
      valuePaint.setColor(ui.muted);
      valuePaint.setTextAlign(Paint.Align.RIGHT);
    }

    public void setItems(List<Item> data) {
      items.clear();
      items.addAll(data);
      max = 1;
      for (Item i : items) max = Math.max(max, i.minutes);
      requestLayout();
      invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      int w = MeasureSpec.getSize(widthSpec);
      int h = Math.max(ui.dp(30), items.size() * ui.dp(40) + ui.dp(4));
      setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      namePaint.setTextSize(ui.dp(12));
      valuePaint.setTextSize(ui.dp(11));
      int row = ui.dp(40);
      for (int i = 0; i < items.size(); i++) {
        Item item = items.get(i);
        float top = i * row;
        canvas.drawText(item.label, 0, top + ui.dp(13), namePaint);
        canvas.drawText(format(item.minutes), getWidth(), top + ui.dp(13), valuePaint);
        bar.setColor(ui.line);
        canvas.drawRoundRect(new RectF(0, top + ui.dp(20), getWidth(), top + ui.dp(27)), ui.dp(3), ui.dp(3), bar);
        bar.setColor(item.color);
        float w = getWidth() * Math.max(0.02f, item.minutes / (float) max);
        canvas.drawRoundRect(new RectF(0, top + ui.dp(20), w, top + ui.dp(27)), ui.dp(3), ui.dp(3), bar);
      }
    }

    private String format(long minutes) {
      if (minutes < 60) return minutes + " 分钟";
      return minutes / 60 + " 小时 " + minutes % 60 + " 分";
    }
  }

  public interface DayClick { void onDay(String isoDate); }

  public static class Month extends View {
    private final Ui ui;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private YearMonth month = YearMonth.now();
    private String selected = LocalDate.now().toString();
    private Map<String, int[]> data;
    private DayClick listener;
    private float cell;

    public Month(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      dayPaint.setTextAlign(Paint.Align.CENTER);
      weekPaint.setTextAlign(Paint.Align.CENTER);
      weekPaint.setColor(ui.muted);
      weekPaint.setTextSize(ui.dp(10));
    }

    public void setMonth(YearMonth month, String selected, Map<String, int[]> data) {
      this.month = month;
      this.selected = selected;
      this.data = data;
      invalidate();
    }

    public void setOnDayClick(DayClick listener) { this.listener = listener; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      int w = MeasureSpec.getSize(widthSpec);
      cell = w / 7f;
      int h = (int) (ui.dp(26) + cell * 6);
      setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      String[] week = {"一", "二", "三", "四", "五", "六", "日"};
      weekPaint.setTextSize(ui.dp(10));
      for (int i = 0; i < 7; i++) {
        canvas.drawText(week[i], cell * i + cell / 2f, ui.dp(14), weekPaint);
      }
      LocalDate first = month.atDay(1);
      int offset = first.getDayOfWeek().getValue() - 1;
      int days = month.lengthOfMonth();
      String today = Store.today();
      for (int d = 1; d <= days; d++) {
        int index = offset + d - 1;
        int col = index % 7;
        int row = index / 7;
        float cx = col * cell + cell / 2f;
        float cy = ui.dp(26) + row * cell + cell / 2f;
        String iso = month.atDay(d).toString();
        boolean isSelected = iso.equals(selected);
        boolean isToday = iso.equals(today);
        if (isSelected) {
          gridPaint.setColor(ui.accent);
          canvas.drawCircle(cx, cy, cell * 0.36f, gridPaint);
        } else if (isToday) {
          gridPaint.setStyle(Paint.Style.STROKE);
          gridPaint.setStrokeWidth(ui.dp(1.4f));
          gridPaint.setColor(ui.accent);
          canvas.drawCircle(cx, cy, cell * 0.36f, gridPaint);
          gridPaint.setStyle(Paint.Style.FILL);
        }
        dayPaint.setTextSize(ui.dp(12));
        dayPaint.setColor(isSelected ? Color.WHITE : ui.ink);
        canvas.drawText(String.valueOf(d), cx, cy + ui.dp(4), dayPaint);
        int[] counts = data == null ? null : data.get(iso);
        if (counts != null && counts[0] > 0) {
          dotPaint.setColor(counts[1] >= counts[0] ? ui.accent : 0xffe0a24a);
          float dy = cy + cell * 0.27f;
          int dots = Math.min(3, counts[0]);
          float startX = cx - (dots - 1) * ui.dp(3.2f);
          for (int k = 0; k < dots; k++) {
            canvas.drawCircle(startX + k * ui.dp(6.4f), dy, ui.dp(1.7f), dotPaint);
          }
        }
      }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_UP && listener != null) {
        int col = (int) (event.getX() / cell);
        int row = (int) ((event.getY() - ui.dp(26)) / cell);
        if (col >= 0 && col < 7 && row >= 0 && row < 6) {
          LocalDate first = month.atDay(1);
          int offset = first.getDayOfWeek().getValue() - 1;
          int day = row * 7 + col - offset + 1;
          if (day >= 1 && day <= month.lengthOfMonth()) {
            listener.onDay(month.atDay(day).toString());
            return true;
          }
        }
      }
      return true;
    }
  }

  /** Seven rows of 24 hours, with a colored bar for every focus session. */
  public static class WeekTimeline extends View {
    private final Ui ui;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String weekStart = "";
    private List<JSONObject> data = new ArrayList<>();

    public WeekTimeline(Context c, Ui ui) {
      super(c);
      this.ui = ui;
      grid.setColor(ui.line);
      grid.setStrokeWidth(Math.max(1, ui.dp(0.6f)));
      label.setColor(ui.muted);
      label.setTextSize(ui.dp(9));
      label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
      lane.setColor(ui.dark ? 0x18FFFFFF : 0x0A000000);
    }

    public void setData(String weekStart, List<JSONObject> sessions) {
      this.weekStart = weekStart == null ? "" : weekStart;
      data = sessions == null ? new ArrayList<>() : sessions;
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float left = ui.dp(28);
      float right = getWidth() - ui.dp(4);
      float top = ui.dp(18);
      float bottom = getHeight() - ui.dp(2);
      float rowH = (bottom - top) / 7f;
      for (int hour = 0; hour <= 24; hour += 6) {
        float x = left + (right - left) * hour / 24f;
        canvas.drawLine(x, top, x, bottom, grid);
        label.setTextAlign(hour == 0 ? Paint.Align.LEFT : hour == 24 ? Paint.Align.RIGHT : Paint.Align.CENTER);
        canvas.drawText(String.valueOf(hour), x, top - ui.dp(5), label);
      }
      String[] days = {"一", "二", "三", "四", "五", "六", "日"};
      for (int i = 0; i < 7; i++) {
        float y = top + rowH * i;
        canvas.drawRect(left, y + rowH * 0.18f, right, y + rowH * 0.82f, lane);
        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(days[i], ui.dp(2), y + rowH * 0.62f, label);
      }
      if (weekStart.isEmpty()) return;
      LocalDate start;
      try { start = LocalDate.parse(weekStart); } catch (Exception e) { return; }
      for (JSONObject s : data) {
        if (s == null) continue;
        long begin = s.optLong("start");
        if (begin <= 0) continue;
        java.time.ZonedDateTime dt = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault());
        long row = ChronoUnit.DAYS.between(start, dt.toLocalDate());
        if (row < 0 || row > 6) continue;
        int minute = dt.getHour() * 60 + dt.getMinute();
        long length = Math.max(1, s.optLong("duration") / 60000);
        int endMinute = (int) Math.min(24 * 60, minute + length);
        float x1 = left + (right - left) * minute / 1440f;
        float x2 = left + (right - left) * endMinute / 1440f;
        if (x2 - x1 < ui.dp(2)) x2 = x1 + ui.dp(2);
        float y = top + rowH * row;
        bar.setColor(s.optBoolean("completed") ? ui.accent : ui.danger);
        bar.setAlpha(s.optBoolean("completed") ? 210 : 120);
        RectF rect = new RectF(x1, y + rowH * 0.24f, Math.min(x2, right), y + rowH * 0.76f);
        canvas.drawRoundRect(rect, ui.dp(3), ui.dp(3), bar);
      }
    }
  }
}
