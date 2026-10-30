package cn.local.focusmate;

/** Pure timing math; monotonic while boot is unchanged, wall clock on reboot. */
public final class ClockMath {
  private ClockMath(){}
  public static long elapsed(long accumulated, long anchorWall, long anchorElapsed, long nowWall, long nowElapsed, boolean running) {
    if(!running)return Math.max(0,accumulated);
    long delta=nowElapsed>=anchorElapsed?nowElapsed-anchorElapsed:nowWall-anchorWall;
    return Math.max(0,accumulated)+Math.max(0,delta);
  }
  public static long remaining(long target,long elapsed){return Math.max(0,target-elapsed);}
  public static String format(long millis){long seconds=Math.max(0,millis/1000);return seconds>=3600?String.format(java.util.Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.US,"%02d:%02d",seconds/60,seconds%60);}
}
