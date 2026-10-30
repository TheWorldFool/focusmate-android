package cn.local.focusmate;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

public class FocusWidget extends AppWidgetProvider {
  public static void refresh(Context c) {
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    int[] ids = manager.getAppWidgetIds(new ComponentName(c, FocusWidget.class));
    if (ids != null && ids.length > 0) new FocusWidget().onUpdate(c, manager, ids);
    FutureWidget.refresh(c);
    HeatmapWidget.refresh(c);
    QuickLockWidget.refresh(c);
  }

  @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    Store store = Store.get(c);
    JSONObject timer = store.timer();
    boolean running = timer != null;
    for (int id : ids) {
      RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget);
      if (running) {
        boolean rest = "rest".equals(timer.optString("phase"));
        boolean paused = timer.optBoolean("paused");
        long elapsed = store.elapsed(timer);
        String time = "countdown".equals(timer.optString("mode"))
            ? ClockMath.format(ClockMath.remaining(timer.optLong("target"), elapsed))
            : ClockMath.format(elapsed);
        views.setTextViewText(R.id.widget_title, timer.optString("title"));
        views.setTextViewText(R.id.widget_time, time);
        views.setTextViewText(R.id.widget_sub,
            (paused ? "已暂停" : rest ? "休息中" : "专注中") + " · 今日 " + store.todayMinutes() + " 分钟");
        boolean canPause = !paused && (!timer.optBoolean("strict") || rest);
        views.setTextViewText(R.id.widget_action, paused ? "继续" : canPause ? "暂停" : "打开");
        Intent action = new Intent(c, FocusService.class);
        if (paused) action.setAction(FocusService.RESUME);
        else if (canPause) action.setAction(FocusService.PAUSE);
        if (paused || canPause) {
          views.setOnClickPendingIntent(R.id.widget_action,
              PendingIntent.getService(c, 21, action, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
          views.setOnClickPendingIntent(R.id.widget_action, openIntent(c, true));
        }
      } else {
        views.setTextViewText(R.id.widget_title, "专注清单");
        views.setTextViewText(R.id.widget_time, "今日 " + store.todayMinutes() + " 分钟");
        views.setTextViewText(R.id.widget_sub,
            store.todayPomodoros() + " 个番茄 · 连续 " + store.streak(Store.today()) + " 天");
        views.setTextViewText(R.id.widget_action, "开始专注");
        views.setOnClickPendingIntent(R.id.widget_action, openIntent(c, true));
      }
      views.setOnClickPendingIntent(R.id.widget_root, openIntent(c, running));
      manager.updateAppWidget(id, views);
    }
  }

  private PendingIntent openIntent(Context c, boolean timer) {
    Intent open = new Intent(c, MainActivity.class)
        .putExtra(timer ? "openTimer" : "openTasks", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(c, timer ? 22 : 23, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
}
