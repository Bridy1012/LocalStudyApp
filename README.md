# 拾光学习 LocalStudyApp

拾光学习是一款本地优先的 Android 学习计划与专注记录应用。它把科目目录、今日任务、截止待办、重复习惯、学习计时、课程表、日历提醒和学习统计整合在一个完全离线的应用里。

应用不申请网络权限，科目、计划和专注记录只保存在设备本地，并支持通过 JSON 文件完整备份与恢复。

## 功能列表

### 学习计划

- 科目、章节、小节完整增删改查
- 学科目录文本自动解析、分层预览与同名科目追加
- 章节展开/收起，小节默认“未安排”
- 小节长按批量管理，支持全选、延后、跳过、完成和删除
- 首页待办、每日任务、重复任务混排
- 单次任务自动顺延与欠债高亮
- 重复任务每日/每周/每月规则
- 重复任务历史完成记录
- 过期未完成待办持续提醒

### 专注计时

- 正计时与倒计时
- 开始、暂停、继续和结束后写入学习记录
- 前台服务通知栏控制
- 正计时 3 小时自动结束并保存
- 科目默认计时规则

### 课程表与日历

- 月历日程、每日任务和截止待办汇总
- 日历日程独立提醒，不进入首页待办
- 周课程表与多学期存档
- 自动生成上午、下午、晚上节次时间
- 课程颜色、教室、教师、备注和周次设置
- 单节停课、调课、停课周和调休补课
- 教务系统课表文本批量导入
- 每日课程汇总和每节课前提醒

### 统计与数据

- 连续学习天数、累计时长和本月时长
- 近 7 天/30 天学习柱状图
- 科目学习占比饼图
- 图表点击下钻和日期滑动切换
- 本周学习周报
- 学习记录补记、编辑和删除
- JSON 全量导出与覆盖导入
- 分档清空数据与二次确认

### 外观

- 浅色、深色和跟随系统
- 六套主题颜色
- 字体大小调节
- 弹性复选框与主题色勾选动画

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| 平台 | Android SDK 35 |
| 最低版本 | Android 8.0 / API 26 |
| 本地数据库 | Room 2.6.1 + SQLite |
| 图表 | MPAndroidChart 3.1.0 |
| 基础组件 | AndroidX AppCompat、Activity、Lifecycle |
| 后台能力 | Foreground Service、AlarmManager、BroadcastReceiver |
| 数据交换 | Gson / JSON |
| 构建 | Gradle 9.6 / Android Gradle Plugin 8.7.3 |

## 截图

当前截图为占位图，可替换 `docs/screenshots/` 下的文件。

| 今日计划 | 科目目录 |
| --- | --- |
| ![今日计划占位](docs/screenshots/home.svg) | ![科目目录占位](docs/screenshots/directory.svg) |

| 日历与课表 | 学习统计 |
| --- | --- |
| ![日历占位](docs/screenshots/calendar.svg) | ![统计占位](docs/screenshots/stats.svg) |

## 运行步骤

### Android Studio

1. 使用 Android Studio 打开项目根目录。
2. 安装 Android SDK Platform 35 与 Build Tools 35.0.0。
3. 将 Gradle JDK 设置为 JDK 17。
4. 等待 Gradle Sync 完成。
5. 选择 `app` 配置并运行到 Android 8.0 及以上设备。

### 命令行

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

首次启动请允许通知权限，用于学习计时状态、课程提醒和每日提醒。

## 数据说明

- 数据完全存储在本地 SQLite 数据库。
- 卸载应用前请在“设置 → 数据管理”导出 JSON 备份。
- 导入备份采用覆盖模式。
- 删除科目不会删除历史学习记录。
- 计时结束不会自动勾选任务，任务完成状态需要手动确认。

## 项目结构

```text
app/src/main/java/com/localstudy/app/
├── data/       Room 实体、DAO、数据库与备份
├── service/    计时、课程提醒与每日提醒
├── ui/         今日、目录、日历、统计、设置页面
└── util/       日期、课表、对话框和 UI 工具
```
