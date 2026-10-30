import cn.local.focusmate.ClockMath;
public class ClockMathTest {
  static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
  public static void main(String[]args){
    check(ClockMath.elapsed(1000,10000,5000,20000,15000,true)==11000,"running monotonic");
    check(ClockMath.elapsed(1000,10000,5000,999999,15000,true)==11000,"wall clock changes do not alter active timer");
    check(ClockMath.elapsed(2000,10000,5000,20000,15000,false)==2000,"paused does not advance");
    check(ClockMath.elapsed(2000,10000,5000,20000,1000,true)==12000,"reboot wall fallback");
    check(ClockMath.remaining(1500,2000)==0,"remaining never negative");
    check(ClockMath.format(1500000).equals("25:00"),"minutes format");
    check(ClockMath.format(3661000).equals("01:01:01"),"hours format");
    System.out.println("PASS: clock, pause, reboot fallback, wall clock jump, boundaries");
  }
}
