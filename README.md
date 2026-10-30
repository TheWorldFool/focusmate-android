# 专注清单 · Focusmate Android

本地 Android 待办、番茄计时与专注统计应用。支持导入 JSON 日程，把每天要做的任务、计划时段和完成标准带进手机。

原生 Java + Android View 实现，不依赖 AndroidX 或第三方 SDK，不申请网络权限。所有个人数据保存在本机。

## 安装

支持 **Android 8.0 及以上**，当前版本 **1.0.1**。

- [下载 APK](https://github.com/TheWorldFool/focusmate-android/releases/download/v1.0.1/focusmate-1.0.1.apk)
- [查看版本说明](https://github.com/TheWorldFool/focusmate-android/releases/tag/v1.0.1)
- 包名：`cn.local.focusmate`
- APK SHA-256：`0b0fe721348491e38038cd85eae3c6c85800602a9ac449090da09178b4ebce2b`

将 APK 保存到手机并安装。已有同签名版本时可直接覆盖升级，保留本机数据。

## 功能

- **待办**：单日或重复任务、计次打卡、分组、优先级、标签、子任务、备注、时段、提醒、归档与搜索。
- **计时**：倒计时、正计时、暂停与恢复、长短休息、严格模式、前台服务、通知操作、程序合成白噪音、悬浮窗与沉浸时钟。
- **专注保护**：自愿锁机、定时锁机、学霸模式、全局和任务白名单。
- **统计**：日/周/月汇总、柱状图、热力图、时间轴、历史记录、手动补记、每日复盘与目标。
- **日历与小组件**：按日期查看待办、未来倒计时、专注控制、热力图和快速锁机。
- **数据**：JSON 日程导入、完整本地备份与恢复、CSV 导出、深色模式及主题色。

账号、云同步和自习室不在实现范围内。

## 导入每日计划

1. 下载或自行生成 [示例 JSON](examples/plan.json)，替换成自己的日期和任务。
2. 保存到手机，在 App 中进入 **我的 → 数据与计划 → 导入 AI 计划 JSON**。
3. 选择文件，点击 **导入主计划**。
4. 在待办页切换日期；打开任务查看完成标准和具体步骤。

```json
{
  "kind": "daily-focus-plan",
  "version": 1,
  "days": [
    {
      "date": "2027-01-04",
      "tasks": [
        {
          "id": "reading",
          "start": "09:00",
          "end": "09:45",
          "category": "thesis",
          "title": "阅读一篇资料",
          "output": "留下三条要点和一个疑问",
          "detail": "先看摘要，再阅读关键段落，最后用自己的话整理。"
        }
      ]
    }
  ]
}
```

- `date` 使用 `YYYY-MM-DD`，时间使用 `HH:MM`，结束晚于开始，同一天主任务不能重叠。
- `id` 同日唯一；`title` 为任务名，`output` 为完成标准，`detail` 为操作说明。
- `category` 支持 `math`、`thesis`、`contest`、`project`、`dev`、`life`，对应不同待办分组。
- `fixed: true` 会标为高优先级。JSON 不会自动授予权限、设置提醒或启用严格模式。
- 同日期的已导入任务会被替换；手动待办保留，已有打卡按任务 ID 保留。
- `lowTasks` 是可选备用任务数组。选择“含备用任务”会把它们与主任务一起导入，并不会切换成另一份日程。
- 每日 `summary`、`sleep` 等扩展信息不会单独显示；需要查看的内容应写进任务 `detail`。

## 权限与数据

通知用于计时与提醒；精确闹钟用于准点提醒；悬浮窗用于计时浮窗；无障碍服务用于主动启用的学霸模式与锁机；使用情况访问用于本机应用时长统计。按实际使用的功能授权即可。

数据写入应用私有目录 `files/focus-store.json`。备份和导出使用系统文件选择器。应用不联网、不上传数据；更换手机或卸载前请自行导出备份。

## 从源码构建

当前脚本支持 **Windows、Python 3.9+、JDK 18**。将 JDK 的 `bin` 加入 `PATH`，或设置 `JAVA_HOME`。

```powershell
python download-toolchain.py
python build-apk.py
```

下载脚本从 Google 官方 Android 仓库取得 API 35、Build Tools 35.0.0 与 Platform Tools，核对仓库中的 SHA-1 后解压到 `.toolchain/`。使用 Android SDK 须遵守其许可条款。

构建不需要 Gradle：`aapt2 → javac → d8 → zipalign → apksigner`。每次使用独立中间目录，输出 `专注清单-本地版.apk` 和 `build-result.json`。

第一次本地构建会生成自己的 `local-debug.keystore`。这是开发签名，请妥善保留；自行生成的签名与 Releases 的签名不同，无法覆盖安装 Releases 版本。仓库不包含发布签名私钥。

## 验证

计时逻辑测试：

```powershell
javac -encoding UTF-8 -d build/test app/src/main/java/cn/local/focusmate/ClockMath.java tests/ClockMathTest.java
java -cp build/test ClockMathTest
```

在专用模拟器安装 APK 后，可运行页面检查；可选计时检查会创建并结束一条测试专注记录：

```powershell
python -X utf8 tests/smoke_android.py --serial 127.0.0.1:16384
python -X utf8 tests/smoke_android.py --serial 127.0.0.1:16384 --running-timer
```

1.0.1 已在 Android 15 / API 35 模拟器验证：覆盖升级、连续冷启动、五个主页面、待办编辑、空闲计时、暂停/继续、沉浸显示、后台返回、进程重启恢复和结束计时。计时数学测试及 APK v2/v3 签名校验通过。

不同厂商系统的权限、长时间后台运行、无障碍、定时任务和小组件仍需实机验证。

## 1.0.1 修复

- 创建窗口视图后再获取系统栏控制器，修复 Android 11+ 冷启动时的空指针异常。
- 旧崩溃日志不再阻挡修复版启动，保留待办和专注数据。
- 空闲计时页不再反复重绘。
- 隔离每次构建的 class/dex 中间文件。

## 项目说明

本项目是独立实现的学习与个人效率工具，功能设计参考了番茄 ToDo 的公开可见功能。与原应用及其开发者无关联，不包含其安装包、源码或素材，也不声称已经完整复刻其所有行为。
