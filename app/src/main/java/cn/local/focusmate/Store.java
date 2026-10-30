package cn.local.focusmate;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Local-only JSON store. One process, one writer, AtomicFile on disk. */
public final class Store {
  public static final int DEFAULT_ACCENT = 0xffe0604f;
  private static final int[] PALETTE = {
      0xffe0604f, 0xff3e9dd6, 0xff3fae8f, 0xff8a78c8, 0xffee8c4a, 0xff5b8def, 0xffd06a9c, 0xff6a9a4a
  };

  private static Store instance;
  private static int dayStartHour;

  public static synchronized Store get(Context context) {
    if (instance == null) instance = new Store(context.getApplicationContext());
    return instance;
  }

  private final AtomicFile file;
  public JSONObject data;

  private Store(Context c) {
    file = new AtomicFile(new File(c.getFilesDir(), "focus-store.json"));
    JSONObject loaded = null;
    try {
      loaded = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
    } catch (Exception ignored) { }
    data = loaded == null ? fresh() : loaded;
    ensureDefaults();
  }

  // ---------- JSON helpers ----------

  public static JSONObject obj(Object... args) {
    JSONObject o = new JSONObject();
    try {
      for (int i = 0; i + 1 < args.length; i += 2) o.put((String) args[i], args[i + 1]);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return o;
  }

  public static void put(JSONObject o, String key, Object value) {
    try {
      o.put(key, value);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static JSONObject copy(JSONObject o) {
    try {
      return new JSONObject(o.toString());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  // ---------- lifecycle ----------

  private static JSONObject freshSettings() {
    return obj(
        "accent", DEFAULT_ACCENT,
        "dark", false,
        "strict", false,
        "autoRest", false,
        "autoNext", false,
        "sound", true,
        "vibrate", true,
        "noise", false,
        "noiseType", "brown",
        "keepScreen", false,
        "overlay", false,
        "guard", false,
        "guardAction", "home",
        "whitelist", new JSONArray(),
        "midnightHour", 0,
        "dailyGoalMinutes", 120,
        "dailyGoalPomodoros", 4,
        "focusMinutes", 25,
        "restMinutes", 5,
        "longRestMinutes", 15,
        "roundsBeforeLong", 4,
        "sortMode", "manual",
        "timerStyle", "normal",
        "timerLarge", false,
        "showDone", true,
        "created", today()
    );
  }

  private static JSONArray defaultGroups() {
    JSONArray a = new JSONArray();
    a.put(obj("name", "未分组", "color", 0xff8a929e));
    a.put(obj("name", "学习", "color", 0xffe0604f));
    a.put(obj("name", "工作", "color", 0xff3e9dd6));
    a.put(obj("name", "生活", "color", 0xff3fae8f));
    return a;
  }

  private static JSONObject fresh() {
    JSONObject root = obj(
        "version", 2,
        "tasks", new JSONArray(),
        "sessions", new JSONArray(),
        "groups", defaultGroups(),
        "settings", freshSettings(),
        "daily", new JSONObject(),
        "metrics", new JSONObject(),
        "future", new JSONArray(),
        "mottos", defaultMottos(),
        "checkins", new JSONArray()
    );
    return root;
  }

  private void ensureDefaults() {
    if (data.optJSONArray("tasks") == null) put(data, "tasks", new JSONArray());
    if (data.optJSONArray("sessions") == null) put(data, "sessions", new JSONArray());
    if (data.optJSONArray("groups") == null || data.optJSONArray("groups").length() == 0) {
      put(data, "groups", defaultGroups());
    }
    if (data.optJSONObject("settings") == null) put(data, "settings", freshSettings());
    if (data.optJSONObject("daily") == null) put(data, "daily", new JSONObject());
    if (data.optJSONObject("metrics") == null) put(data, "metrics", new JSONObject());
    if (data.optJSONArray("future") == null) put(data, "future", new JSONArray());
    if (data.optJSONArray("mottos") == null) put(data, "mottos", defaultMottos());
    if (data.optJSONArray("checkins") == null) put(data, "checkins", new JSONArray());
    JSONObject defs = freshSettings();
    JSONObject settings = settings();
    Iterator<String> keys = defs.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!settings.has(key)) {
        try { settings.put(key, defs.get(key)); } catch (JSONException ignored) { }
      }
    }
    try { data.put("version", 2); } catch (JSONException ignored) { }
    int dayStart = Math.max(0, Math.min(6, settings.optInt("midnightHour", 0)));
    try { settings.put("midnightHour", dayStart); } catch (JSONException ignored) { }
    setDayStartHour(dayStart);
  }

  public synchronized void save() {
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(stream);
    } catch (Exception e) {
      if (stream != null) file.failWrite(stream);
      throw new IllegalStateException("无法保存本地记录", e);
    }
  }

  public void clearAll() {
    data = fresh();
    setDayStartHour(0);
    save();
  }

  public static void setDayStartHour(int hour) {
    dayStartHour = Math.max(0, Math.min(6, hour));
  }

  // ---------- accessors ----------

  public JSONArray tasks() { return data.optJSONArray("tasks"); }
  public JSONArray sessions() { return data.optJSONArray("sessions"); }
  public JSONObject settings() { return data.optJSONObject("settings"); }
  public JSONArray groups() {
    JSONArray a = data.optJSONArray("groups");
    if (a == null) { a = defaultGroups(); put(data, "groups", a); }
    return a;
  }
  public JSONObject metrics() {
    JSONObject m = data.optJSONObject("metrics");
    if (m == null) { m = new JSONObject(); put(data, "metrics", m); }
    return m;
  }
  public JSONObject timer() { return data.optJSONObject("timer"); }
  public void setTimer(JSONObject timer) { put(data, "timer", timer); }
  public void clearTimer() { data.remove("timer"); }

  /** The user's current day, respecting the configured day-start hour (凌晨模式). */
  public static String today() {
    LocalDate d = LocalDate.now();
    if (LocalTime.now().getHour() < dayStartHour) d = d.minusDays(1);
    return d.toString();
  }

  public static String dateHuman(String iso) {
    LocalDate d = LocalDate.parse(iso);
    String[] week = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    String text = d.getMonthValue() + "月" + d.getDayOfMonth() + "日 " + week[d.getDayOfWeek().getValue() - 1];
    if (d.getYear() != LocalDate.now().getYear()) text = d.getYear() + "年" + text;
    return text;
  }

  public static String shortDate(String iso) {
    LocalDate d = LocalDate.parse(iso);
    return d.getMonthValue() + "/" + d.getDayOfMonth();
  }

  public static String weekday(String iso) {
    String[] week = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    return week[LocalDate.parse(iso).getDayOfWeek().getValue() - 1];
  }

  public static String startOfWeek(String iso) {
    return LocalDate.parse(iso).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
  }

  public static String endOfWeek(String iso) {
    return LocalDate.parse(iso).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString();
  }

  public static String startOfMonth(String iso) {
    return YearMonth.from(LocalDate.parse(iso)).atDay(1).toString();
  }

  public static String endOfMonth(String iso) {
    YearMonth ym = YearMonth.from(LocalDate.parse(iso));
    return ym.atEndOfMonth().toString();
  }

  public static String addMonth(String iso, int delta) {
    return YearMonth.from(LocalDate.parse(iso)).plusMonths(delta).atDay(1).toString();
  }

  public static int parseTime(String time) {
    if (time == null || !time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
      throw new IllegalArgumentException("时间格式应为 HH:MM");
    }
    return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3));
  }

  public static String minutesText(long minutes) {
    if (minutes < 60) return minutes + " 分钟";
    long h = minutes / 60;
    long m = minutes % 60;
    return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
  }

  public static String clockText(long millis) {
    long minutes = Math.max(0, millis) / 60000;
    if (minutes < 60) return minutes + " 分钟";
    return minutes / 60 + " 小时 " + minutes % 60 + " 分";
  }

  // ---------- groups ----------

  public int groupColor(String name) {
    JSONArray groups = groups();
    for (int i = 0; i < groups.length(); i++) {
      JSONObject g = groups.optJSONObject(i);
      if (g != null && g.optString("name").equals(name)) return g.optInt("color", PALETTE[i % PALETTE.length]);
    }
    return 0xff8a929e;
  }

  public List<String> groupNames() {
    List<String> names = new ArrayList<>();
    JSONArray groups = groups();
    for (int i = 0; i < groups.length(); i++) {
      JSONObject g = groups.optJSONObject(i);
      if (g != null && !g.optString("name").isEmpty()) names.add(g.optString("name"));
    }
    if (!names.contains("未分组")) names.add(0, "未分组");
    return names;
  }

  public boolean addGroup(String name) {
    String clean = name == null ? "" : name.trim();
    if (clean.isEmpty()) return false;
    if (groupNames().contains(clean)) return false;
    int color = PALETTE[groups().length() % PALETTE.length];
    groups().put(obj("name", clean, "color", color));
    save();
    return true;
  }

  public void ensureGroup(String name) {
    if (name == null || name.trim().isEmpty()) return;
    if (!groupNames().contains(name.trim())) {
      int color = PALETTE[groups().length() % PALETTE.length];
      groups().put(obj("name", name.trim(), "color", color));
    }
  }

  public void renameGroup(String oldName, String newName) {
    String clean = newName.trim();
    if (clean.isEmpty() || clean.equals(oldName)) return;
    for (int i = 0; i < groups().length(); i++) {
      JSONObject g = groups().optJSONObject(i);
      if (g != null && g.optString("name").equals(oldName)) put(g, "name", clean);
    }
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && t.optString("group", "未分组").equals(oldName)) put(t, "group", clean);
    }
    save();
  }

  public void deleteGroup(String name) {
    if ("未分组".equals(name)) return;
    JSONArray next = new JSONArray();
    for (int i = 0; i < groups().length(); i++) {
      JSONObject g = groups().optJSONObject(i);
      if (g != null && !g.optString("name").equals(name)) next.put(g);
    }
    put(data, "groups", next);
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && t.optString("group", "未分组").equals(name)) put(t, "group", "未分组");
    }
    save();
  }

  // ---------- lock session ----------

  public JSONObject lock() {
    JSONObject lock = data.optJSONObject("lock");
    if (lock != null && lock.optLong("endWall") <= System.currentTimeMillis()) return null;
    return lock;
  }

  /** True only when a stored lock exists and its end time has passed (not when it was cancelled). */
  public boolean lockExpired() {
    JSONObject lock = data.optJSONObject("lock");
    return lock != null && lock.optLong("endWall") <= System.currentTimeMillis();
  }

  public boolean lockActive() { return lock() != null; }

  public long lockRemaining() {
    JSONObject lock = lock();
    return lock == null ? 0 : Math.max(0, lock.optLong("endWall") - System.currentTimeMillis());
  }

  public void startLock(int minutes) {
    long now = System.currentTimeMillis();
    put(data, "lock", obj("startedWall", now, "endWall", now + minutes * 60000L,
        "duration", minutes * 60000L));
    save();
  }

  public void clearLock() {
    data.remove("lock");
    save();
  }

  // ---------- check-ins ----------

  public JSONArray checkins() {
    JSONArray a = data.optJSONArray("checkins");
    if (a == null) { a = new JSONArray(); put(data, "checkins", a); }
    return a;
  }

  public JSONObject checkin(String date, String type) {
    JSONArray a = checkins();
    JSONObject found = null;
    for (int i = 0; i < a.length(); i++) {
      JSONObject item = a.optJSONObject(i);
      if (item != null && date.equals(item.optString("date")) && type.equals(item.optString("type"))) found = item;
    }
    return found;
  }

  public void putCheckin(String date, String type, String time) {
    parseTime(time);
    JSONObject existing = checkin(date, type);
    if (existing == null) {
      checkins().put(obj("id", UUID.randomUUID().toString(), "date", date, "type", type,
          "time", time, "created", System.currentTimeMillis()));
    } else {
      put(existing, "time", time);
      put(existing, "created", System.currentTimeMillis());
    }
    save();
  }

  public List<JSONObject> checkinsBetween(String from, String to, String type) {
    List<JSONObject> list = new ArrayList<>();
    JSONArray a = checkins();
    for (int i = 0; i < a.length(); i++) {
      JSONObject item = a.optJSONObject(i);
      if (item == null || !type.equals(item.optString("type"))) continue;
      String day = item.optString("date");
      if (day.compareTo(from) < 0 || day.compareTo(to) > 0) continue;
      try { parseTime(item.optString("time")); } catch (Exception e) { continue; }
      list.add(item);
    }
    Collections.sort(list, (a1, b1) -> a1.optString("date").compareTo(b1.optString("date")));
    return list;
  }

  // ---------- mottos ----------

  private static JSONArray defaultMottos() {
    JSONArray a = new JSONArray();
    String[] texts = {
        "把注意力放回眼前这一件事。",
        "慢一点，也是在向前。",
        "今天的一小步，会连成很远的路。",
        "先做完，再做好。",
        "专注不是用力，而是不分心。",
        "给重要的事，留一段完整的时间。"
    };
    for (int i = 0; i < texts.length; i++) {
      a.put(obj("id", "m" + (i + 1), "text", texts[i], "builtin", true));
    }
    return a;
  }

  public JSONArray mottos() {
    JSONArray a = data.optJSONArray("mottos");
    if (a == null) { a = defaultMottos(); put(data, "mottos", a); }
    return a;
  }

  public String mottoForToday() {
    JSONArray a = mottos();
    if (a.length() == 0) return "把注意力放回眼前这一件事。";
    int index = Math.floorMod(today().hashCode(), a.length());
    JSONObject item = a.optJSONObject(index);
    return item == null ? "" : item.optString("text");
  }

  public String randomMotto() {
    JSONArray a = mottos();
    if (a.length() == 0) return "把注意力放回眼前这一件事。";
    JSONObject item = a.optJSONObject((int) (Math.random() * a.length()));
    return item == null ? "" : item.optString("text");
  }

  public boolean addMotto(String text) {
    String clean = text == null ? "" : text.trim();
    if (clean.isEmpty() || clean.length() > 60) return false;
    JSONArray a = mottos();
    for (int i = 0; i < a.length(); i++) {
      JSONObject item = a.optJSONObject(i);
      if (item != null && item.optString("text").equals(clean)) return false;
    }
    a.put(obj("id", UUID.randomUUID().toString(), "text", clean, "builtin", false));
    save();
    return true;
  }

  public void removeMotto(String id) {
    JSONArray next = new JSONArray();
    JSONArray a = mottos();
    for (int i = 0; i < a.length(); i++) {
      JSONObject item = a.optJSONObject(i);
      if (item != null && !id.equals(item.optString("id"))) next.put(item);
    }
    put(data, "mottos", next);
    save();
  }

  // ---------- future countdowns ----------

  public JSONArray future() {
    JSONArray a = data.optJSONArray("future");
    if (a == null) { a = new JSONArray(); put(data, "future", a); }
    return a;
  }

  public JSONObject findFuture(String id) {
    if (id == null) return null;
    JSONArray a = future();
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      if (f != null && id.equals(f.optString("id"))) return f;
    }
    return null;
  }

  public void upsertFuture(JSONObject item) {
    JSONArray a = future();
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      if (f != null && f.optString("id").equals(item.optString("id"))) {
        try { a.put(i, item); } catch (JSONException e) { throw new IllegalStateException(e); }
        save();
        return;
      }
    }
    a.put(item);
    save();
  }

  public void removeFuture(String id) {
    JSONArray a = future();
    JSONArray next = new JSONArray();
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      if (f != null && !id.equals(f.optString("id"))) next.put(f);
    }
    put(data, "future", next);
    save();
  }

  // ---------- task queries ----------

  public JSONObject find(String id) {
    if (id == null) return null;
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && id.equals(t.optString("id"))) return t;
    }
    return null;
  }

  public boolean due(JSONObject task, String date) {
    if (task.optBoolean("archived")) return false;
    String fixed = task.optString("date");
    if (!fixed.isEmpty()) return fixed.equals(date);
    int mask = task.optInt("repeatMask", 127);
    return (mask & (1 << (LocalDate.parse(date).getDayOfWeek().getValue() - 1))) != 0;
  }

  /** All reminder times for a task, including the legacy single "reminder" field. */
  public static JSONArray reminders(JSONObject task) {
    JSONArray arr = task.optJSONArray("reminders");
    if (arr != null) return arr;
    JSONArray legacy = new JSONArray();
    String one = task.optString("reminder");
    if (!one.isEmpty()) legacy.put(one);
    return legacy;
  }

  /** True for a count task whose target is measured per calendar week. */
  public boolean weekly(JSONObject task) {
    return "count".equals(task.optString("mode")) && "week".equals(task.optString("cycle", "day"));
  }

  /** Monday of the week containing {@code date}, formatted yyyy-MM-dd. */
  public static String weekStart(String date) {
    LocalDate d = LocalDate.parse(date);
    return d.minusDays(d.getDayOfWeek().getValue() - 1L).toString();
  }

  private int rawCount(JSONObject task, String date) {
    JSONObject counts = task.optJSONObject("counts");
    return counts == null ? 0 : counts.optInt(date, 0);
  }

  /** Progress for the date: weekly habits sum Monday through Sunday. */
  public int count(JSONObject task, String date) {
    if (!weekly(task)) return rawCount(task, date);
    LocalDate d = LocalDate.parse(weekStart(date));
    int total = 0;
    for (int i = 0; i < 7; i++) total += rawCount(task, d.plusDays(i).toString());
    return total;
  }

  public int goal(JSONObject task) { return Math.max(1, task.optInt("goal", 1)); }

  public boolean done(JSONObject task, String date) { return count(task, date) >= goal(task); }

  public void setCount(JSONObject task, String date, int value) {
    JSONObject counts = task.optJSONObject("counts");
    if (counts == null) {
      counts = new JSONObject();
      put(task, "counts", counts);
    }
    put(counts, date, Math.max(0, value));
    save();
  }

  public void increment(JSONObject task, String date) {
    setCount(task, date, rawCount(task, date) + 1);
  }

  /**
   * Marks completion for the date. For a weekly habit, completing adds only the
   * missing check-ins to the given day; un-completing resets that week's checks.
   */
  public void setDone(JSONObject task, String date, boolean complete) {
    if (!weekly(task)) {
      setCount(task, date, complete ? goal(task) : 0);
      return;
    }
    if (!complete) {
      clearWeek(task, date);
      return;
    }
    int missing = goal(task) - count(task, date);
    if (missing > 0) setCount(task, date, rawCount(task, date) + missing);
  }

  public void toggleDone(JSONObject task, String date) {
    boolean complete = done(task, date);
    if (weekly(task) && !complete) {
      increment(task, date);
      return;
    }
    setDone(task, date, !complete);
  }

  private void clearWeek(JSONObject task, String date) {
    JSONObject counts = task.optJSONObject("counts");
    if (counts == null) return;
    LocalDate d = LocalDate.parse(weekStart(date));
    for (int i = 0; i < 7; i++) counts.remove(d.plusDays(i).toString());
    save();
  }

  public int nextOrder() {
    int max = 0;
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null) max = Math.max(max, t.optInt("order", 0));
    }
    return max + 1;
  }

  public List<JSONObject> tasksFor(String date, String group, String query,
                                   boolean showDone, boolean archived, boolean importantOnly) {
    List<JSONObject> list = new ArrayList<>();
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t == null) continue;
      if (t.optBoolean("archived") != archived) continue;
      if (!archived && !due(t, date)) continue;
      if (group != null && !group.equals("全部") && !group.equals(t.optString("group", "未分组"))) continue;
      if (importantOnly && t.optInt("priority", 0) < 1) continue;
      if (!q.isEmpty()) {
        String hay = (t.optString("title") + " " + t.optString("note") + " " + t.optString("tags")).toLowerCase(Locale.ROOT);
        if (!hay.contains(q)) continue;
      }
      if (!showDone && done(t, date)) continue;
      list.add(t);
    }
    sortTasks(list, date);
    return list;
  }

  private void sortTasks(List<JSONObject> list, final String date) {
    final String mode = settings().optString("sortMode", "manual");
    Collections.sort(list, (a, b) -> {
      boolean da = done(a, date);
      boolean db = done(b, date);
      if (da != db) return da ? 1 : -1;
      int c = 0;
      if ("priority".equals(mode)) {
        c = Integer.compare(b.optInt("priority", 0), a.optInt("priority", 0));
      } else if ("time".equals(mode)) {
        String sa = a.optString("start");
        String sb = b.optString("start");
        c = (sa.isEmpty() ? "99:99" : sa).compareTo(sb.isEmpty() ? "99:99" : sb);
      } else if ("created".equals(mode)) {
        c = Long.compare(b.optLong("created", 0), a.optLong("created", 0));
      }
      if (c != 0) return c;
      c = Integer.compare(a.optInt("order", 0), b.optInt("order", 0));
      if (c != 0) return c;
      return Long.compare(a.optLong("created", 0), b.optLong("created", 0));
    });
  }

  public void upsert(JSONObject task) {
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && t.optString("id").equals(task.optString("id"))) {
        try {
          tasks().put(i, task);
        } catch (JSONException e) {
          throw new IllegalStateException(e);
        }
        save();
        return;
      }
    }
    tasks().put(task);
    save();
  }

  public void removeTask(String id) {
    JSONArray next = new JSONArray();
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && !id.equals(t.optString("id"))) next.put(t);
    }
    put(data, "tasks", next);
    save();
  }

  public void moveOrder(String id, int delta) {
    List<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null) list.add(t);
    }
    Collections.sort(list, (a, b) -> Integer.compare(a.optInt("order", 0), b.optInt("order", 0)));
    int index = -1;
    for (int i = 0; i < list.size(); i++) if (list.get(i).optString("id").equals(id)) index = i;
    if (index < 0) return;
    int target = index + delta;
    if (target < 0 || target >= list.size()) return;
    JSONObject other = list.get(target);
    int mine = list.get(index).optInt("order", 0);
    int theirs = other.optInt("order", 0);
    if (mine == theirs) {
      for (int i = 0; i < list.size(); i++) put(list.get(i), "order", i);
      mine = index;
      theirs = target;
    }
    put(list.get(index), "order", theirs);
    put(other, "order", mine);
    save();
  }

  public int completedSessions(String date) {
    int n = 0;
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s != null && s.optBoolean("completed") && date.equals(s.optString("date"))) n++;
    }
    return n;
  }

  // ---------- timer ----------

  public long elapsed(JSONObject timer) {
    return ClockMath.elapsed(timer.optLong("accumulated"), timer.optLong("anchorWall"),
        timer.optLong("anchorElapsed"), System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(),
        !timer.optBoolean("paused"));
  }

  public JSONObject record(JSONObject timer, boolean completed, String reason) {
    if (timer == null || !"focus".equals(timer.optString("phase"))) return null;
    String id = timer.optString("sessionId");
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject old = sessions().optJSONObject(i);
      if (old != null && id.equals(old.optString("id"))) return old;
    }
    long duration = elapsed(timer);
    if ("countdown".equals(timer.optString("mode"))) duration = Math.min(duration, timer.optLong("target"));
    if (duration < 1000 && !completed) return null;
    long started = timer.optLong("startedWall");
    if (started <= 0) started = System.currentTimeMillis() - duration;
    ZoneId zone = ZoneId.systemDefault();
    LocalDate day = Instant.ofEpochMilli(started).atZone(zone).toLocalDate();
    int cutoff = settings().optInt("midnightHour", 0);
    if (Instant.ofEpochMilli(started).atZone(zone).getHour() < cutoff) day = day.minusDays(1);
    JSONObject session = obj(
        "id", id,
        "taskId", timer.optString("taskId"),
        "title", timer.optString("title"),
        "group", timer.optString("group", "未分组"),
        "start", started,
        "end", System.currentTimeMillis(),
        "duration", duration,
        "date", day.toString(),
        "completed", completed,
        "reason", reason == null ? "" : reason,
        "manual", false,
        "phase", "focus",
        "strict", timer.optBoolean("strict")
    );
    sessions().put(session);
    if (completed) {
      JSONObject task = find(timer.optString("taskId"));
      if (task != null) {
        JSONObject counts = task.optJSONObject("counts");
        if (counts == null) { counts = new JSONObject(); put(task, "counts", counts); }
        put(counts, day.toString(), counts.optInt(day.toString(), 0) + 1);
      }
      put(metrics(), "focusCompleted", metrics().optInt("focusCompleted", 0) + 1);
      if (timer.optBoolean("strict")) {
        put(metrics(), "strictCompleted", metrics().optInt("strictCompleted", 0) + 1);
      }
    } else {
      put(metrics(), "focusAbandoned", metrics().optInt("focusAbandoned", 0) + 1);
    }
    save();
    return session;
  }

  // ---------- stats ----------

  public long total(String from, String to, String taskId) {
    long sum = 0;
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      if (taskId != null && !taskId.isEmpty() && !taskId.equals(s.optString("taskId"))) continue;
      sum += s.optLong("duration");
    }
    return sum;
  }

  public long abandonedTotal(String from, String to) {
    long sum = 0;
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (d.compareTo(from) >= 0 && d.compareTo(to) <= 0) sum += s.optLong("duration");
    }
    return sum;
  }

  public int sessionCount(String from, String to, boolean completed) {
    int n = 0;
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      if (s.optBoolean("completed") == completed) n++;
    }
    return n;
  }

  public int completedTaskCount(String from, String to) {
    int n = 0;
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t == null) continue;
      JSONObject counts = t.optJSONObject("counts");
      if (counts == null) continue;
      int goal = goal(t);
      if (weekly(t)) {
        java.util.TreeMap<String, Integer> days = new java.util.TreeMap<>();
        Iterator<String> keys = counts.keys();
        while (keys.hasNext()) {
          String d = keys.next();
          try {
            weekStart(d);
            days.put(d, counts.optInt(d));
          } catch (Exception ignored) { }
        }
        String week = "";
        int total = 0;
        boolean counted = false;
        for (Map.Entry<String, Integer> e : days.entrySet()) {
          String ws = weekStart(e.getKey());
          if (!ws.equals(week)) {
            week = ws;
            total = 0;
            counted = false;
          }
          if (counted) continue;
          total += e.getValue();
          if (total >= goal) {
            counted = true;
            String d = e.getKey();
            if (d.compareTo(from) >= 0 && d.compareTo(to) <= 0) n++;
          }
        }
      } else {
        Iterator<String> keys = counts.keys();
        while (keys.hasNext()) {
          String d = keys.next();
          if (d.compareTo(from) >= 0 && d.compareTo(to) <= 0 && counts.optInt(d) >= goal) n++;
        }
      }
    }
    return n;
  }

  public boolean hasSessionOn(String date) {
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s != null && s.optBoolean("completed") && date.equals(s.optString("date"))) return true;
    }
    return false;
  }

  public int streak(String end) {
    Set<String> days = new HashSet<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s != null && s.optBoolean("completed")) days.add(s.optString("date"));
    }
    LocalDate d = LocalDate.parse(end);
    int count = 0;
    if (!days.contains(d.toString())) d = d.minusDays(1);
    while (count < 3650 && days.contains(d.toString())) {
      count++;
      d = d.minusDays(1);
    }
    return count;
  }

  public String firstSessionDate() {
    String min = null;
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (min == null || d.compareTo(min) < 0) min = d;
    }
    return min;
  }

  public Map<String, Long> minutesByDay(String from, String to) {
    Map<String, Long> map = new LinkedHashMap<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      map.put(d, map.getOrDefault(d, 0L) + s.optLong("duration"));
    }
    return map;
  }

  public Map<String, Long> minutesByHour(String date) {
    Map<String, Long> map = new LinkedHashMap<>();
    for (int h = 0; h < 24; h++) map.put(String.valueOf(h), 0L);
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed") || !date.equals(s.optString("date"))) continue;
      int hour = Instant.ofEpochMilli(s.optLong("start")).atZone(ZoneId.systemDefault()).getHour();
      String key = String.valueOf(hour);
      map.put(key, map.getOrDefault(key, 0L) + s.optLong("duration"));
    }
    return map;
  }

  public Map<String, Long> minutesByGroup(String from, String to) {
    Map<String, Long> map = new LinkedHashMap<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      String g = s.optString("group", "未分组");
      map.put(g, map.getOrDefault(g, 0L) + s.optLong("duration"));
    }
    return map;
  }

  public Map<String, Integer> reasons(String from, String to) {
    Map<String, Integer> map = new LinkedHashMap<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || s.optBoolean("completed")) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      String reason = s.optString("reason", "其他");
      if (reason.isEmpty()) reason = "其他";
      map.put(reason, map.getOrDefault(reason, 0) + 1);
    }
    return map;
  }

  public List<JSONObject> sessionsBetween(String from, String to, int limit) {
    List<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null) continue;
      String d = s.optString("date");
      if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
      list.add(s);
    }
    Collections.sort(list, (a, b) -> Long.compare(b.optLong("start"), a.optLong("start")));
    if (limit > 0 && list.size() > limit) return new ArrayList<>(list.subList(0, limit));
    return list;
  }

  public HeatData heat(LocalDate end, int weeks) {
    LocalDate start = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1);
    long[] values = new long[weeks * 7];
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      LocalDate d = LocalDate.parse(s.optString("date"));
      long days = d.toEpochDay() - start.toEpochDay();
      if (days < 0 || days >= values.length) continue;
      values[(int) days] += s.optLong("duration");
    }
    return new HeatData(start, values);
  }

  public static class HeatData {
    public final LocalDate start;
    public final long[] values;
    public HeatData(LocalDate start, long[] values) {
      this.start = start;
      this.values = values;
    }
  }

  public int todayMinutes() { return (int) (total(today(), today(), null) / 60000); }
  public int todayPomodoros() { return completedSessions(today()); }

  // ---------- notes ----------

  public String note(String date) {
    return data.optJSONObject("daily").optString(date);
  }

  public void note(String date, String text) {
    JSONObject daily = data.optJSONObject("daily");
    put(daily, date, text);
    save();
  }

  // ---------- achievements ----------

  public JSONArray achievements() {
    long totalMinutes = total("0000-01-01", "9999-12-31", null) / 60000;
    int sessions = sessionCount("0000-01-01", "9999-12-31", true);
    int tasksDone = completedTaskCount("0000-01-01", "9999-12-31");
    int streak = streak(today());
    int strict = metrics().optInt("strictCompleted", 0);
    boolean early = false;
    boolean night = false;
    Map<String, Long> byDay = new HashMap<>();
    for (int i = 0; i < sessions().length(); i++) {
      JSONObject s = sessions().optJSONObject(i);
      if (s == null || !s.optBoolean("completed")) continue;
      LocalTime t = Instant.ofEpochMilli(s.optLong("start")).atZone(ZoneId.systemDefault()).toLocalTime();
      if (t.getHour() < 6) early = true;
      if (t.getHour() >= 23) night = true;
      byDay.put(s.optString("date"), byDay.getOrDefault(s.optString("date"), 0L) + 1);
    }
    boolean eight = false;
    for (long n : byDay.values()) if (n >= 8) eight = true;
    JSONArray a = new JSONArray();
    a.put(badge("seed", "🌱", "开始专注", "完成第一次专注", sessions >= 1));
    a.put(badge("h10", "⏳", "十小时", "累计专注 10 小时", totalMinutes >= 600));
    a.put(badge("h50", "📚", "五十小时", "累计专注 50 小时", totalMinutes >= 3000));
    a.put(badge("h100", "🏆", "百小时", "累计专注 100 小时", totalMinutes >= 6000));
    a.put(badge("h300", "💎", "三百小时", "累计专注 300 小时", totalMinutes >= 18000));
    a.put(badge("streak3", "🔥", "三日不断", "连续专注 3 天", streak >= 3));
    a.put(badge("streak7", "🔥", "七日不断", "连续专注 7 天", streak >= 7));
    a.put(badge("streak30", "🌟", "三十日", "连续专注 30 天", streak >= 30));
    a.put(badge("task10", "✅", "十项事成", "累计完成 10 项待办", tasksDone >= 10));
    a.put(badge("task100", "✅", "百项事成", "累计完成 100 项待办", tasksDone >= 100));
    a.put(badge("day8", "🍅", "番茄富翁", "单日完成 8 个番茄", eight));
    a.put(badge("strict", "🔒", "严于律己", "严格模式完成一次专注", strict >= 1));
    a.put(badge("early", "🌅", "早起的人", "在 6 点前完成专注", early));
    a.put(badge("night", "🌙", "夜猫子", "在 23 点后完成专注", night));
    return a;
  }

  private JSONObject badge(String id, String icon, String title, String desc, boolean unlocked) {
    return obj("id", id, "icon", icon, "title", title, "desc", desc, "unlocked", unlocked);
  }

  public int level() { return 1 + (int) (total("0000-01-01", "9999-12-31", null) / 60000 / 600); }

  public int levelProgress() {
    long minutes = total("0000-01-01", "9999-12-31", null) / 60000;
    return (int) (minutes % 600);
  }

  // ---------- backup / import ----------

  public String exportJson() {
    return obj("kind", "focusmate-backup", "version", 2, "data", data).toString();
  }

  public void restore(JSONObject root) {
    if (!"focusmate-backup".equals(root.optString("kind"))
        || (root.optInt("version") != 1 && root.optInt("version") != 2)) {
      throw new IllegalArgumentException("不是本 App 的备份文件");
    }
    JSONObject candidate = root.optJSONObject("data");
    if (candidate == null || candidate.optJSONArray("tasks") == null
        || candidate.optJSONArray("sessions") == null
        || candidate.optJSONObject("settings") == null) {
      throw new IllegalArgumentException("备份缺少必要字段");
    }
    candidate = copy(candidate);
    candidate.remove("timer");
    JSONObject old = data;
    data = candidate;
    try {
      ensureDefaults();
      save();
    } catch (Exception e) {
      data = old;
      throw e;
    }
  }

  public String exportCsv() {
    StringBuilder sb = new StringBuilder();
    sb.append('﻿').append("日期,开始时间,待办,待办集,时长(分钟),状态,中断原因\n");
    List<JSONObject> list = sessionsBetween("0000-01-01", "9999-12-31", 0);
    Collections.sort(list, (a, b) -> Long.compare(a.optLong("start"), b.optLong("start")));
    java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    for (JSONObject s : list) {
      LocalTime t = Instant.ofEpochMilli(s.optLong("start")).atZone(ZoneId.systemDefault()).toLocalTime();
      sb.append(csv(s.optString("date"))).append(',')
          .append(csv(t.format(fmt))).append(',')
          .append(csv(s.optString("title"))).append(',')
          .append(csv(s.optString("group"))).append(',')
          .append(s.optLong("duration") / 60000).append(',')
          .append(s.optBoolean("completed") ? "完成" : "中断").append(',')
          .append(csv(s.optString("reason"))).append('\n');
    }
    return sb.toString();
  }

  private static String csv(String v) {
    if (v == null) return "";
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  public int importPlan(JSONObject root) { return importPlan(root, false); }

  /**
   * Imports AI plan tasks. Main tasks are always imported. The plan's lowTasks are
   * alternative low-energy tasks, so they are only imported when explicitly asked for.
   */
  public int importPlan(JSONObject root, boolean includeLow) {
    if (!"daily-focus-plan".equals(root.optString("kind")) || root.optInt("version") != 1) {
      throw new IllegalArgumentException("需要 daily-focus-plan version 1 的计划");
    }
    JSONArray days = root.optJSONArray("days");
    if (days == null || days.length() == 0 || days.length() > 730) {
      throw new IllegalArgumentException("计划需包含 1—730 天");
    }
    Set<String> dates = new HashSet<>();
    JSONArray imported = new JSONArray();
    long now = System.currentTimeMillis();
    Map<String, String> groupMap = new HashMap<>();
    groupMap.put("math", "数学竞赛");
    groupMap.put("thesis", "毕业设计");
    groupMap.put("contest", "网信杯");
    groupMap.put("project", "导师项目");
    groupMap.put("dev", "开发学习");
    groupMap.put("life", "日常安排");
    groupMap.put("exercise", "运动");
    int order = nextOrder();
    for (int i = 0; i < days.length(); i++) {
      JSONObject day = days.optJSONObject(i);
      if (day == null) throw new IllegalArgumentException("日期格式错误");
      String date = day.optString("date");
      try {
        if (!LocalDate.parse(date).toString().equals(date)) throw new Exception();
      } catch (Exception e) {
        throw new IllegalArgumentException("日期无效：" + date);
      }
      if (!dates.add(date)) throw new IllegalArgumentException("日期重复：" + date);
      JSONArray main = day.optJSONArray("tasks");
      JSONArray low = day.optJSONArray("lowTasks");
      if ((main == null || main.length() == 0) && (low == null || low.length() == 0)) continue;
      if ((main == null ? 0 : main.length()) + (low == null ? 0 : low.length()) > 100) {
        throw new IllegalArgumentException(date + " 任务过多");
      }
      List<int[]> slots = new ArrayList<>();
      Set<String> ids = new HashSet<>();
      Set<String> mainKeys = new HashSet<>();
      if (main != null) {
        for (int j = 0; j < main.length(); j++) {
          JSONObject t = main.optJSONObject(j);
          if (t == null || t.optString("title").trim().isEmpty()) {
            throw new IllegalArgumentException("任务标题不能为空");
          }
          String start = t.optString("start");
          String end = t.optString("end");
          int a = parseTime(start);
          int b = parseTime(end);
          if (b <= a) throw new IllegalArgumentException("结束须晚于开始：" + t.optString("title"));
          for (int[] slot : slots) {
            if (a < slot[1] && b > slot[0]) throw new IllegalArgumentException(date + " 存在重叠时段");
          }
          slots.add(new int[]{a, b});
          String sourceId = t.optString("id", "task-" + j);
          if (!ids.add(sourceId)) throw new IllegalArgumentException(date + " 任务 ID 重复");
          mainKeys.add(t.optString("title").trim() + "|" + start + "|" + end);
          String category = t.optString("category", "life");
          String group = groupMap.containsKey(category) ? groupMap.get(category) : category;
          if (group == null || group.trim().isEmpty()) group = "未分组";
          ensureGroup(group);
          imported.put(planTask(date, sourceId, t, group, false, order++, now));
        }
      }
      if (includeLow && low != null) {
        for (int j = 0; j < low.length(); j++) {
          JSONObject t = low.optJSONObject(j);
          if (t == null || t.optString("title").trim().isEmpty()) continue;
          String start = t.optString("start");
          String end = t.optString("end");
          if (mainKeys.contains(t.optString("title").trim() + "|" + start + "|" + end)) continue;
          int a = parseTime(start);
          int b = parseTime(end);
          if (b <= a) throw new IllegalArgumentException("结束须晚于开始：" + t.optString("title"));
          String sourceId = "low:" + t.optString("id", "task-" + j);
          if (!ids.add(sourceId)) throw new IllegalArgumentException(date + " 任务 ID 重复");
          String category = t.optString("category", "life");
          String group = groupMap.containsKey(category) ? groupMap.get(category) : category;
          if (group == null || group.trim().isEmpty()) group = "未分组";
          ensureGroup(group);
          imported.put(planTask(date, sourceId, t, group, true, order++, now));
        }
      }
    }
    JSONArray next = new JSONArray();
    for (int i = 0; i < tasks().length(); i++) {
      JSONObject t = tasks().optJSONObject(i);
      if (t != null && !(t.optBoolean("imported") && dates.contains(t.optString("date")))) next.put(t);
    }
    for (int i = 0; i < imported.length(); i++) next.put(imported.optJSONObject(i));
    JSONObject before = data;
    JSONObject candidate = copy(data);
    put(candidate, "tasks", next);
    put(candidate, "importedPlan", root);
    data = candidate;
    try {
      save();
    } catch (Exception e) {
      data = before;
      throw e;
    }
    return imported.length();
  }

  private JSONObject planTask(String date, String sourceId, JSONObject source, String group,
                              boolean optional, int order, long now) {
    String start = source.optString("start");
    String end = source.optString("end");
    int a = parseTime(start);
    int b = parseTime(end);
    String id = "plan:" + date + ":" + sourceId;
    JSONObject old = find(id);
    JSONObject counts = old == null ? new JSONObject() : old.optJSONObject("counts");
    if (counts == null) counts = new JSONObject();
    String note = source.optString("output") + "\n" + source.optString("detail");
    String title = source.optString("title").trim();
    if (optional) title = "可选 · " + title;
    return obj(
        "id", id,
        "title", title,
        "group", group,
        "tags", source.optString("category", ""),
        "priority", source.optBoolean("fixed") ? 2 : 0,
        "mode", "countdown",
        "minutes", b - a,
        "restMinutes", settings().optInt("restMinutes", 5),
        "goal", 1,
        "estimate", Math.max(1, Math.round((b - a) / 25f)),
        "repeatMask", 127,
        "date", date,
        "start", start,
        "end", end,
        "reminder", "",
        "note", note.trim(),
        "subtasks", new JSONArray(),
        "counts", counts,
        "archived", false,
        "order", order,
        "created", now,
        "imported", true,
        "fixed", source.optBoolean("fixed"),
        "optional", optional,
        "source", source.optString("category", "")
    );
  }
}
