package cn.local.focusmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reusable launcher-app multi-select used for global, task and lock whitelists. */
public final class AppWhitelist {
  public interface Callback {
    /** {@code result} is null when the caller chose to follow the global whitelist. */
    void onPicked(JSONArray result);
  }

  private AppWhitelist() { }

  public static void pick(final Activity act, final Ui ui, String title,
                          JSONArray current, boolean followGlobal, final Callback callback) {
    Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> apps = act.getPackageManager().queryIntentActivities(launcher, 0);
    Collections.sort(apps, (a, b) -> a.loadLabel(act.getPackageManager()).toString()
        .compareToIgnoreCase(b.loadLabel(act.getPackageManager()).toString()));
    List<String> labels = new ArrayList<>();
    List<String> packages = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ResolveInfo info : apps) {
      String pkg = info.activityInfo.packageName;
      if (pkg.equals(act.getPackageName()) || !seen.add(pkg)) continue;
      labels.add(info.loadLabel(act.getPackageManager()).toString());
      packages.add(pkg);
    }
    Set<String> chosen = new HashSet<>();
    if (current != null) for (int i = 0; i < current.length(); i++) chosen.add(current.optString(i));
    final boolean[] checked = new boolean[packages.size()];
    for (int i = 0; i < checked.length; i++) checked[i] = chosen.contains(packages.get(i));

    AlertDialog.Builder builder = ui.dialog()
        .setTitle(title)
        .setMultiChoiceItems(labels.toArray(new String[0]), checked,
            (d, which, on) -> checked[which] = on)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", (d, w) -> {
          JSONArray result = new JSONArray();
          for (int i = 0; i < checked.length; i++) if (checked[i]) result.put(packages.get(i));
          callback.onPicked(result);
        });
    if (followGlobal) {
      builder.setNeutralButton("跟随全局", (d, w) -> callback.onPicked(null));
    }
    ui.show(builder);
  }
}
