package cn.local.focusmate;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/** Twelve-week focus heatmap rendered into a bitmap for RemoteViews. */
public class HeatmapWidget extends AppWidgetProvider {
  private static final int WEEKS = 12;

  public static void refresh(Context c) {
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    int[] ids = manager.getAppWidgetIds(new ComponentName(c, HeatmapWidget.class));
    if (ids != null && ids.length > 0) new HeatmapWidget().onUpdate(c, manager, ids);
  }

  @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    Store store = Store.get(c);
    Map<String, Long> minutes = minutesByDay(store);
    LocalDate today = LocalDate.parse(Store.today());
    LocalDate first = today.minusWeeks(WEEKS - 1).with(DayOfWeek.MONDAY);
    String from = first.toString();
    String to = today.toString();
    int activeDays = 0;
    for (Map.Entry<String, Long> e : minutes.entrySet()) {
      String d = e.getKey();
      if (e.getValue() > 0 && d.compareTo(from) >= 0 && d.compareTo(to) <= 0) activeDays++;
    }
    Bitmap bitmap = draw(c, store, minutes, first, today);
    String sub = "近 " + WEEKS + " 周 · " + activeDays + " 天有专注";
    for (int id : ids) {
      RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_heat);
      views.setImageViewBitmap(R.id.heat_widget_image, bitmap);
      views.setTextViewText(R.id.heat_widget_sub, sub);
      views.setOnClickPendingIntent(R.id.heat_widget_root, openIntent(c));
      manager.updateAppWidget(id, views);
    }
  }

  private Map<String, Long> minutesByDay(Store store) {
    Map<String, Long> map = new HashMap<>();
    JSONArray sessions = store.sessions();
    for (int i = 0; i < sessions.length(); i++) {
      JSONObject s = sessions.optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      String date = s.optString("date");
      if (date.isEmpty()) continue;
      Long current = map.get(date);
      map.put(date, (current == null ? 0 : current) + s.optLong("duration"));
    }
    return map;
  }

  private Bitmap draw(Context c, Store store, Map<String, Long> minutes, LocalDate first, LocalDate today) {
    int cell = 16;
    int gap = 4;
    int padding = 4;
    int width = WEEKS * (cell + gap) - gap + padding * 2;
    int height = 7 * (cell + gap) - gap + padding * 2;
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF rect = new RectF();
    int accent = store.settings().optInt("accent", Store.DEFAULT_ACCENT);
    for (int week = 0; week < WEEKS; week++) {
      for (int row = 0; row < 7; row++) {
        LocalDate day = first.plusDays(week * 7L + row);
        float left = padding + week * (cell + gap);
        float top = padding + row * (cell + gap);
        rect.set(left, top, left + cell, top + cell);
        if (day.isAfter(today)) {
          paint.setColor(Color.WHITE);
          paint.setAlpha(12);
        } else {
          long value = minutes.getOrDefault(day.toString(), 0L) / 60000;
          if (value <= 0) {
            paint.setColor(Color.WHITE);
            paint.setAlpha(30);
          } else {
            paint.setColor(accent);
            paint.setAlpha(value < 30 ? 80 : value < 60 ? 130 : value < 120 ? 190 : 255);
          }
        }
        canvas.drawRoundRect(rect, 4, 4, paint);
      }
    }
    return bitmap;
  }

  private PendingIntent openIntent(Context c) {
    Intent open = new Intent(c, MainActivity.class)
        .putExtra("openStats", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(c, 211, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
}
