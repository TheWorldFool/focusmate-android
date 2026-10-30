package cn.local.focusmate;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Nearest future countdown at a glance. */
public class FutureWidget extends AppWidgetProvider {
  public static void refresh(Context c) {
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    int[] ids = manager.getAppWidgetIds(new ComponentName(c, FutureWidget.class));
    if (ids != null && ids.length > 0) new FutureWidget().onUpdate(c, manager, ids);
  }

  @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    Store store = Store.get(c);
    JSONObject nearest = null;
    long nearestDays = Long.MAX_VALUE;
    JSONArray list = store.future();
    LocalDate today = LocalDate.parse(Store.today());
    for (int i = 0; i < list.length(); i++) {
      JSONObject item = list.optJSONObject(i);
      if (item == null || item.optString("title").isEmpty()) continue;
      try {
        long days = ChronoUnit.DAYS.between(today, LocalDate.parse(item.optString("target")));
        if (days < 0) continue;
        if (days < nearestDays) {
          nearestDays = days;
          nearest = item;
        }
      } catch (Exception ignored) { }
    }
    for (int id : ids) {
      RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_future);
      if (nearest == null) {
        views.setTextViewText(R.id.future_widget_title, "未来倒计时");
        views.setTextViewText(R.id.future_widget_days, "D-");
        views.setTextViewText(R.id.future_widget_name, "添加一个目标");
        views.setTextViewText(R.id.future_widget_sub, "把重要的日子放在眼前");
        views.setTextViewText(R.id.future_widget_date, "点击进入未来页");
      } else {
        String days = nearestDays == 0 ? "今天" : "D-" + nearestDays;
        views.setTextViewText(R.id.future_widget_title, "最近的目标");
        views.setTextViewText(R.id.future_widget_days, days);
        views.setTextViewText(R.id.future_widget_name, nearest.optString("title"));
        String note = nearest.optString("note").trim();
        views.setTextViewText(R.id.future_widget_sub, note.isEmpty()
            ? (nearestDays == 0 ? "就是今天" : "还有 " + nearestDays + " 天")
            : note);
        views.setTextViewText(R.id.future_widget_date,
            Store.dateHuman(nearest.optString("target")) + " · " + nearest.optString("target"));
      }
      views.setOnClickPendingIntent(R.id.future_widget_root, openIntent(c));
      manager.updateAppWidget(id, views);
    }
  }

  private PendingIntent openIntent(Context c) {
    Intent open = new Intent(c, MainActivity.class)
        .putExtra("openFuture", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(c, 210, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
}
