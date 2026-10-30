package cn.local.focusmate;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes the last startup/runtime crash so the next launch can show it. */
final class CrashLog {
  private CrashLog() { }

  static File file(Context c) {
    return new File(c.getFilesDir(), "last-crash.txt");
  }

  static void write(Context c, Throwable t) {
    try {
      FileOutputStream out = new FileOutputStream(file(c));
      out.write(text(t).getBytes(StandardCharsets.UTF_8));
      out.close();
    } catch (Throwable ignored) { }
  }

  static String text(Throwable t) {
    StringWriter buffer = new StringWriter();
    PrintWriter writer = new PrintWriter(buffer);
    writer.println("时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
    writer.println("设备：" + Build.MANUFACTURER + " " + Build.MODEL);
    writer.println("Android：" + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）");
    writer.println("应用：cn.local.focusmate 1.0.1");
    writer.println();
    t.printStackTrace(writer);
    writer.flush();
    return buffer.toString();
  }

  static String read(Context c) {
    File f = file(c);
    try {
      FileInputStream in = new FileInputStream(f);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
      in.close();
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (Throwable e) {
      return "无法读取崩溃记录：" + e;
    }
  }

  static void clear(Context c) {
    try { file(c).delete(); } catch (Throwable ignored) { }
  }
}
