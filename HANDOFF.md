# 交接说明（给下一个 AI 开发者）

> 项目：**余额簿 BalanceBook** —— 基于 KernelSU-Style-UI-Kit 的离线记账 + 借款管理 Android App。
> 当前版本：**1.5.5 (versionCode 155)**。本文件描述现状、构建方式、关键设计、已知坑与建议下一步。

---

## 1. 项目在哪、怎么跑

- 源码根目录：仓库根（本文件所在目录）。
- 技术栈：Kotlin 2.4.20 / Jetpack Compose（BOM 2026.09.00）/ Miuix 0.9.4 / AGP 9.4.1 / Gradle 9.7.1。
- `compileSdk = 37`（Miuix 0.9.4 的 AAR 元数据强制要求），`targetSdk = 36`，`minSdk = 31`。
- 包名：`com.example.kernelsustyleuikit`，应用名：余额簿。

### 环境（原开发机）

| 工具 | 路径 |
| --- | --- |
| JDK 21 | `C:\tools\jdk-21.0.12.1+1`（设置 `JAVA_HOME`） |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`（platform 37、build-tools 36.0.0） |
| Gradle | wrapper 9.7.1 |

构建前在仓库根创建 `local.properties`（模板见 `local.properties.example`，该文件已被 gitignore）：

```properties
sdk.dir=C\:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk
```

### 常用命令

```powershell
$env:JAVA_HOME="C:\tools\jdk-21.0.12.1+1"

# 单测（86 个 JVM 测试）
.\gradlew.bat :app:testDebugUnitTest

# 真机/日常使用请构建 release（非 debuggable），debug 会明显卡顿
.\gradlew.bat :app:assembleRelease
# 产物：app/build/outputs/apk/release/KernelSUStyleUIKit_v1.5.5_155-release.apk
```

> **离线说明**：`gradle/wrapper/gradle-wrapper.properties` 提交的是官方 distributionUrl。
> 如果本机无法联网下载 Gradle，可执行 `scripts/use-local-gradle.ps1 -Zip ..\gradle-9.7.1-bin.zip`
> 把 wrapper 临时指到本地 zip（该改动不要提交）。原开发机上的 zip 在
> `C:\Users\Administrator\Documents\deepseek-harness\default-workspace\gradle-9.7.1-bin.zip`。

> **签名**：未配置 keystore 时 `apksign` 插件回退用 debug 签名；上架前需在
> `sign.example.properties` 基础上配置自己的 `KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`。

---

## 2. 功能现状

四个主入口（`MainActivity` 的 HorizontalPager）：**首页 / 账单 / 借款 / 设置**。
二级页面：新增/编辑账单、新增/编辑借款、借款详情、Onboarding、模板主题页（ColorPalette）。

- 账单：支出/收入、分类、备注、日期；月度统计、分类汇总、按天分组；预算与超支。
- 借款：
  - 三种计息：按年利率 / 固定总利息 / 固定每期利息（后者只允许先息后本）。
  - 还款间隔按天（1–365，默认 30），到期日 = 起借日 + N×i 天；旧 v1 按月借款兼容 `MONTHS`。
  - 先息后本 / 等额本息，末期吸收分差。
  - **每笔借款独立开关「按分期提醒」**：关闭后不判逾期、不发通知，仅按总待收跟踪。
  - **自由还款**：详情页「记一笔还款」可输入任意金额/日期/备注；支持部分还款、跨多期、多还、提前还。
  - 还款记录可逐笔删除；支持撤销最后一笔、清空收款记录重算、删除借款。
  - 已收款后锁定本金/利息/期数/间隔/起借日/还款方式，只可改好友、备注、是否计入余额、提醒开关。
- 设置：额度、初始余额、借款提醒总开关、通知权限、导入/导出 JSON、示例数据、清空记录、主题页。
- 提醒：`loan_reminders` 高优先级渠道，每天 9:00 AlarmManager 检查 3 天内到期/逾期；
  BootReceiver 重排；同一借款同期一天最多一次；点击打开借款页。
- 完全离线：APK 无 `INTERNET` 权限（依赖库自带权限用 `tools:node="remove"` 移除）。

---

## 3. 关键代码结构与设计

```
app/src/main/java/com/example/kernelsustyleuikit/
├── finance/                       # 纯 Kotlin 领域层，无 Android 依赖，可 JVM 单测
│   ├── Models.kt                  # Transaction / Loan / LoanPayment / AppSettings / FinanceState
│   ├── LoanCalculator.kt          # 还款计划、覆盖期数、下一期剩余、状态、校验 + FinanceCalculator
│   ├── Money.kt                   # 元↔分、格式化、日期
│   └── BalanceBookCodec.kt        # version 1/2 JSON 编解码与迁移
├── data/BalanceBookRepository.kt  # 单例 StateFlow 仓库，原子写入 / 导入导出 / 示例数据
├── reminder/                      # AlarmManager、通知渠道、Boot 重排
└── ui/
    ├── MainActivity.kt            # 四页 Pager + miuix NavDisplay + 主题/开关接线
    ├── navigation3/Routes.kt      # Main / AddTransaction / AddLoan / LoanDetail / ColorPalette
    └── screen/finance/            # 首页、账单、借款列表、设置、账单编辑、借款编辑、详情、Onboarding
```

### 借款计算（必须保持一致）

- 金额全部 `Long` 分；输入用 `BigDecimal` 转分。
- 周期利率：`DAYS: 年利率/100/365×N`；`MONTHS: 年利率/100/12`。
- 先息后本：每期利息，末期本金+利息；固定总利息均摊、末期吸收余数。
- 等额本息：`pmt = round(P·r·(1+r)^n / ((1+r)^n−1))`，末期用剩余本金抹平；
  固定总利息 + 等额本息 = 把「本金+总利息」均分。
- 自由还款后的进度（`LoanCalculator`）：
  - `paidCents = Σ payments.amount`。
  - `paidCount`（覆盖期数）= 累计已还 ≥ 累计应还的期数；旧数据显式 `period` 也计入，取较大值。
  - `nextInstallment` = 第一个未覆盖期；`nextRemainingCents` = 该期累计应还 − 已还（0..该期金额）。
  - `settled` = 覆盖全部期数或 `outstanding <= 0`（允许还多）。
  - 关闭 `reminderEnabled` 时状态固定为 NORMAL（不判逾期）。
- 余额 = 初始余额 + 收入 − 支出 − 计入余额且起借日 ≤ 今天的本金 + 已收还款。

### JSON（`filesDir/balance_book.json`）

- 导出 `version: 2`；导入兼容 v1/v2，缺字段有默认值；损坏文件导入失败且不改原数据。
- 借款新增字段（向后兼容，仍是 v2）：
  - `loans[].reminderEnabled`（默认 true）
  - `payments[].id`、`payments[].note`
  - `payments[].period = 0` 表示自由还款；旧数据为 1-based 期数。
- 写入：临时文件 → fsync → `.bak` → rename；读取失败重命名 `.corrupt-<ts>`。

### UI 关键点（容易改坏的）

1. **根导航必须包在 Miuix `Scaffold` 里**：
   `BalanceBookApp` 用 `top.yukonga.miuix.kmp.basic.Scaffold { NavDisplay(...) }`，
   它提供 `popupHost`。去掉后主题页「强调色」「页面切换手势」等下拉菜单只出现遮罩、不显示内容。
2. **悬浮底栏 / 液态玻璃**：`MainScreen` 在「悬浮底栏 + 液态玻璃」开启时给 pager 挂
   `Modifier.layerBackdrop(backdrop)`；否则玻璃采样空纹理会变黑。
   常规 NavigationBar 的模糊层只在 `!enableFloatingBottomBar` 时挂，避免两层全屏纹理掉帧。
3. 主题页开关（`enable_blur` / `enable_floating_bottom_bar` / `enable_floating_bottom_bar_blur`）
   由 `rememberUiPrefs()` 监听 SharedPreferences 后提供给 CompositionLocal；不要写死。
4. 页面骨架统一 `FinancePage(title, onBack, actions, extraBottomPadding)`，内容用 `LazyListScope`。

---

## 4. 测试

- JVM 单测 86 个：`app/src/test/java/com/example/kernelsustyleuikit/finance/`
  - `LoanCalculatorTest`：8.1–8.2 全部验收数字、legacy MONTHS、零利率、末期限差、非法组合、边界。
  - `LoanProgressTest`：进度/撤销、状态流转、部分还款、多还结清、关闭提醒不判逾期、末期含本金。
  - `BalanceBookCodecTest`：v1 迁移、v2 往返、导出→清空→导入、损坏 JSON、缺字段、自由还款多笔。
  - `FinanceCalculatorTest`：预算/超支/跨月、未来借款不扣余额、还款回补、汇总。
  - `MoneyTest`。
- 实机（OnePlus 25053RT47C，Android 16，1280×2772@120Hz）已验证：
  主题菜单、悬浮底栏/玻璃开关、通知、v1 导入、导出、自由还款/部分还款/关闭提醒。
  release 构建 90th 帧耗时约 9ms，debug 约 22ms —— **真机不要用 debug**。

---

## 5. 已知限制 / 建议下一步

1. **R8 未开启**（`app/build.gradle.kts` 中 release `isMinifyEnabled = false`）。
   若要开启，请先补 `kotlinx.serialization` / `miuix-nav` / Compose 的 keep 规则并回归测试。
2. 主题页的「界面缩放」（`page_scale`）当前只写 pref，`MainActivity` 没有像原模板那样
   用 `LocalDensity` 应用缩放；需要的话参考模板旧 `MainActivity` 的 `Density(...)` 逻辑补上。
3. 模板自带的 Home/Settings/About/Permission 示例页仍保留在源码但未挂到导航，可在确认无用后删除。
4. `FinanceState.settings` 里的 `blur/floatingNav/liquidGlass/navBadges` 是旧数据兼容字段，
   实际外观开关走 SharedPreferences；如需统一需重构。
5. 通知的「每天 9:00」使用 `setInexactRepeating`，Doze 下可能延后；未申请精确闹钟权限。
6. 现有 UI 没有 Compose UI 测试；只有 JVM 单测与手工真机验证。
7. 备份使用 `allowBackup=true`；如需更强隐私可评估关闭系统备份。

---

## 6. 交付物

- 本仓库（含 git 历史，tag `v1.5.5`）。
- 源码压缩包：`余额簿-源码-1.5.5.zip`（`git archive` 导出，不含 `.git`、`build/`、`local.properties`）。
- 可安装 APK：`余额簿-1.5.5.apk`（release，debug 签名）。
- 详细需求文档：仓库内 `README.md`；本文件 `HANDOFF.md`。

接手时建议顺序：`README.md` → `HANDOFF.md` → `finance/LoanCalculator.kt` 与 `BalanceBookCodec.kt`
→ `ui/screen/finance/LoanDetailPage.kt` → 跑 `testDebugUnitTest` → 构建 release 装机。
