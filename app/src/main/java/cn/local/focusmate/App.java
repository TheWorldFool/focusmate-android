package cn.local.focusmate;

import android.app.Application;

/** Installs a last-resort crash logger so startup failures can be reported. */
public class App extends Application {
  @Override public void onCreate() {
    super.onCreate();
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      CrashLog.write(this, throwable);
      if (previous != null) previous.uncaughtException(thread, throwable);
    });
  }
}
