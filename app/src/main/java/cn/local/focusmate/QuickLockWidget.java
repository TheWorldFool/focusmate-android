package cn.local.focusmate;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;

/** Four one-tap lock durations. */
public class QuickLockWidget extends AppWidgetProvider {
  private static final int[] FALLBACK = {15, 30, 45, 60};
  private static final int[] BUTTON_IDS = {
      R.id.quick_lock_1, R.id.quick_lock_2, R.id.quick_lock_3, R.id.quick_lock_4
  };

  public static void refresh(Context c) {
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    int[] ids = manager.getAppWidgetIds(new ComponentName(c, QuickLockWidget.class));
    if (ids != null && ids.length > 0) new QuickLockWidget().onUpdate(c, manager, ids);
  }

  @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    int[] values = durations(Store.get(c));
    for (int id : ids) {
      RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_quick_lock);
      for (int i = 0; i < BUTTON_IDS.length; i++) {
        int minutes = values[i];
        views.setTextViewText(BUTTON_IDS[i], minutes + "分");
        Intent start = new Intent(c, LockActivity.class)
            .putExtra("startMinutes", minutes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        views.setOnClickPendingIntent(BUTTON_IDS[i], PendingIntent.getActivity(c, 220 + i, start,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
      }
      Intent open = new Intent(c, MainActivity.class)
          .putExtra("openMe", true)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      views.setOnClickPendingIntent(R.id.quick_lock_root, PendingIntent.getActivity(c, 221, open,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
      manager.updateAppWidget(id, views);
    }
  }

  private int[] durations(Store store) {
    int[] result = FALLBACK.clone();
    JSONArray list = store.settings().optJSONArray("lockQuickMinutes");
    if (list != null) {
      for (int i = 0; i < result.length && i < list.length(); i++) {
        int value = list.optInt(i, result[i]);
        if (value >= 2 && value <= 720) result[i] = value;
      }
    }
    return result;
  }
}
