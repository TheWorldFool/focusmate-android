package cn.local.focusmate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduledLockReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (intent != null && ScheduledLock.ACTION_START.equals(intent.getAction())) {
      ScheduledLock.fire(context);
    }
  }
}
