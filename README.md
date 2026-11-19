# 余额簿 BalanceBook（KernelSU-Style-UI-Kit 版）

离线记账 + 借款管理 Android App。UI 完全基于
[KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) 的 Miuix 主题、
Scaffold、TopAppBar、NavigationBar、Card、Dialog、TextField、Switch、Slider 与主题设置页，
没有复刻旧 App 的自定义 View / BottomNav / GradientCard。

- 包名：`com.example.kernelsustyleuikit`
- 版本：1.5.5 (155)
- `targetSdk = 36`，`minSdk = 31`，`compileSdk = 37`（Miuix 0.9.4 AAR 要求，targetSdk 仍为 36）
- 完全离线：APK 中**没有 INTERNET 权限**（依赖库自带的 INTERNET 已用 `tools:node="remove"` 移除），
  无登录、无服务器、无云同步。

## 本次借款功能重点

1. **三种计息方式**
   - `RATE`：按年利率（0–100%）。
   - `FIXED + TOTAL`：固定总利息。
   - `FIXED + PER_INSTALLMENT`：固定每期利息，只允许先息后本。
2. **按天还款间隔**：每隔 N 天还一次（1–365），共 M 期，第 i 期到期日 = 起借日 + N×i 天；
   新借款默认 N = 30，提供 7 / 14 / 15 / 30 / 60 / 90 快捷选项。
3. **旧 v1 按月兼容**：`termMonths` + `startDate.plusMonths(i)`，导入后 `intervalUnit = MONTHS`；
   无收款记录时可一键「转为按天」。
4. **文案统一「每期应还 / 本期应还」**：借款列表、详情、还款计划、通知正文都不再写死「每月应还」。

## 目录结构（新增部分）

```
app/src/main/java/com/example/kernelsustyleuikit/
├── finance/                       # 纯 Kotlin 领域层（无 Android 依赖，可 JVM 单测）
│   ├── Models.kt                  # Transaction / Loan / LoanPayment / AppSettings / FinanceState
│   ├── LoanCalculator.kt          # 还款计划、进度、状态、校验 + FinanceCalculator 余额/预算
│   ├── Money.kt                   # 元↔分、金额/日期/利率格式化
│   └── BalanceBookCodec.kt        # version 1/2 JSON 编解码与迁移
├── data/BalanceBookRepository.kt  # 单例仓库：StateFlow + 原子写入 + 导入导出 + 示例数据
├── reminder/                      # AlarmManager 每日 9:00、通知渠道、开机重排
└── ui/
    ├── MainActivity.kt            # 四页 Pager + miuix NavDisplay 路由
    ├── navigation3/Routes.kt      # Main / AddTransaction / AddLoan / LoanDetail / ColorPalette
    └── screen/finance/            # 首页、账单、借款、设置、账单编辑、借款编辑、详情、Onboarding
```

## 核心规则

- 金额一律 `Long` 分；输入用 `BigDecimal` 转分，拒绝超两位小数、NaN、Infinity。
- 周期利率：
  - `DAYS`：`annualRate / 100 / 365 × intervalDays`
  - `MONTHS`（旧数据）：`annualRate / 100 / 12`
- 先息后本：每期利息，末期本金 + 利息；固定总利息按 `fixed / n` 均摊、末期吸收余数。
- 等额本息：`pmt = round(P·r·(1+r)^n / ((1+r)^n − 1))`；最后一期用剩余本金抹平。
  固定总利息 + 等额本息把「本金 + 总利息」均分，末期吸收余数。
- 总应收 = 每期 `due` 之和；`paidPercent = paidCents × 100 / totalReceivable`（金额口径）。
- 余额 = 初始余额 + 收入 − 支出 − 计入余额且 `startDate <= today` 的借出本金 + 已收还款。
- 借款状态：已结清 / 逾期 / 今天到期 / 正常；进度按金额，期数进度单独展示「已还 x/y 期」。

## 本地 JSON

- 存储：`filesDir/balance_book.json`，UTF-8；临时文件 → fsync → `.bak` → rename 原子替换。
- 读取失败：重命名为 `.corrupt-<timestamp>` 并回退默认数据，不删除备份。
- 导出 `version: 2`；导入兼容 `version: 1` 与 `version: 2`，缺字段全部有默认值；
  损坏/非余额簿 JSON 导入失败且**不修改现有数据**。
- 旧 v1 迁移：`termMonths` → `installmentCount`；缺 `intervalUnit` → `MONTHS`、`intervalDays = 30`；
  缺 `interestMode` → `RATE`；缺 `fixedInterestCents` → `0`。

## 提醒

- 渠道 `loan_reminders`，IMPORTANCE_HIGH，Android 13+ 运行时请求 `POST_NOTIFICATIONS`。
- 每天 9:00 由 `AlarmManager.setInexactRepeating` 触发；开机 / 应用更新后由 `BootReceiver` 重排。
- 提醒所有未结清且 `nextDueDate <= today + 3 天`（含逾期）的借款；
  标题如「逾期 1 笔 / 2 笔即将到期」，正文「好友 · 本期应还 ¥xx · 已逾期 N 天」。
- 同一借款同一期一天最多通知一次；点击通知打开借款页。
- 关闭提醒开关会取消闹钟。

## 单元测试

`app/src/test/java/com/example/kernelsustyleuikit/finance/`，共 **86** 个 JVM 测试：

- `LoanCalculatorTest`：8.1–8.2 全部用例（首期/末期/总应收/总利息/到期日）、
  legacy MONTHS、零利率、末期限差、非法组合、边界校验、周期利率。
- `LoanProgressTest`：8.3 进度、状态流转、记录/撤销还款、末期含本金、结清。
- `BalanceBookCodecTest`：v1 迁移、v2 往返、导出→清空→导入、损坏/异构 JSON、
  缺字段容错、非法组合归一化、越界值收敛。
- `FinanceCalculatorTest`：8.4 预算/超支/跨月、未来借款不扣余额、已收还款回补、汇总、近 6 月。
- `MoneyTest`：元↔分、非法输入、格式化、利率、日期。

运行：

```powershell
$env:JAVA_HOME="C:\tools\jdk-21.0.12.1+1"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Gradle 需要 **≥ 9.6**（AGP 9.4.1）；wrapper 使用官方 `gradle-9.7.1-bin.zip`。
离线环境可执行 `scripts/use-local-gradle.ps1 -Zip <本地 zip>` 临时切换（不要提交）。
`local.properties` 参考 `local.properties.example` 指向本机 Android SDK（不要提交）。

## 验收对照

| 用例 | 结果 |
| --- | --- |
| 8.1-1 ¥5000 / 8% / 12 期 / 30 天 / 等额本息 | 首期 ¥434.69，总应收 ¥5216.26，总利息 ¥216.26，本金和 ¥5000（单测 + 预览） |
| 8.1-2 ¥2000 / 6% / 6 期 / 先息后本 | 每期 ¥9.86，末期 ¥2009.86，总应收 ¥2059.16 |
| 8.1-3 ¥1200 / 0% / 4 期 | 每期 ¥300，总应收 ¥1200 |
| 8.2-4 固定总利息 ¥60 / 10 期 / 7 天 / 等额本息 | 每期 ¥106，总 ¥1060，到期日 +7…+70 天 |
| 8.2-5 固定每期 ¥10 / 6 期 / 14 天 / 先息后本 | 前 5 期 ¥10，末期 ¥1010，总 ¥1060 |
| 8.2-6 固定总利息 ¥60 / 6 期 / 先息后本 | 每期利息 ¥10，末期 ¥1010 |
| 8.3-7 记录 2/10 期 | 已还 2/10，进度 20%，下期第 3 期；撤销后 1/10 |
| 8.4-8 预算 ¥3000，支出 ¥2550 | 剩余 ¥450，85%，已省 ¥450 |
| 8.4-9 跨月 / 删除账单 | 月份互不影响，仓库 StateFlow 驱动统计刷新（单测 + 手动验证） |
| 8.5-10 导出→清空→导入 | 账单/借款/还款/设置全部恢复（单测 + 模拟器实测） |
| 8.5-11 损坏 JSON 导入 | 失败提示，原文件 MD5 不变（实测） |
| 8.5-12 无 INTERNET 权限 | `aapt2 dump permissions` 仅 POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED |
| 8.5-13 3 天内到期提醒 | 模拟器实测通知「逾期 1 笔 · 老陈 · 本期应还 ¥276.66 · 已逾期 13 天」，点击进入借款页 |

## 模拟器验证

已在 `emulator-5554`（Android 16 / API 36）安装运行并验证：

- 首次启动 Onboarding → 首页四页 Pager（首页/账单/借款/设置）。
- 新建借款：固定每期利息 10 元、6 期、每 14 天、先息后本 → 预览
  首期 ¥10.00 / 末期 ¥1,010.00 / 总应收 ¥1,060.00 / 最后还款日 2027-02-02；
  保存后列表「每期应还 ¥10.00」、详情记录还款、撤销、删除。
- 导入 v1 备份 → 列表显示「每 1 个月（旧版）」、到期日按 `plusMonths`。
- 导出 JSON 到 Downloads，文件为 `version: 2`。
- 通知渠道与文案、通知点击跳转借款页。

## 说明

- 模板自带的 Home / Settings / About / Permission 示例页仍保留在源码中但不再挂到导航上，
  避免误改模板组件；App 实际入口全部走 `ui/screen/finance`。
- 主题页使用模板原有 `ColorPaletteScreen`（模板文案为英文）。
- 旧版 v1 备份只保证「新 App 能导入」，不保证旧 App 反向导入 v2。


## 真机反馈修复（1.5.3 → 1.5.4）

1. **主题页菜单打不开（强调色 / 页面切换手势）**
   - 原因：`NavDisplay` 外层缺少 Miuix `Scaffold`，`OverlayDropdownPreference` 没有 `popupHost`，
     点击时只出现遮罩、菜单内容不渲染。
   - 修复：与模板一致，用 `top.yukonga.miuix.kmp.basic.Scaffold { NavDisplay(...) }` 包裹根导航。

2. **真机明显卡顿**
   - 主因是 debug 构建（Compose/ART 调试开销）；`90th percentile` 帧耗时从 **22ms 降到 9ms**，
     `Janky frames 0.00%`（OnePlus 25053RT47C，1280×2772 @120Hz）。
   - 另外去掉了一层不必要的全屏 `layerBackdrop`：只有常规 `NavigationBar` 才需要模糊层，
     悬浮玻璃底栏使用自己的 `backdrop`，避免同时录制两层全屏纹理。

**构建真机包请用 release：**

```powershell
.\gradlew.bat :app:assembleRelease
# 产物：app/build/outputs/apk/release/KernelSUStyleUIKit_v1.5.5_155-release.apk
```

release 暂未开启 R8（`isMinifyEnabled = false`），避免裁剪 `kotlinx.serialization` /
`miuix-nav` 的反射类；release 为非 debuggable，性能已足够。debug APK 仅用于调试。

## 自由还款 + 分期提醒开关（1.5.5）

真实场景下还款金额和日期往往不按分毫不差，因此分期计划改为「提醒参考」：

- **每笔借款独立开关「按分期提醒」**（新建/编辑页，默认开）：
  - 开：详情显示下次还款日、本期还应，列表显示「本期应还」，逾期/今天到期状态与通知按计划触发。
  - 关：不再产生到期/逾期状态和通知；详情与列表显示「分期提醒已关闭 / 不提醒」，
    只按总待收跟踪，分期计划仍展示但标注「仅参考」。
- **详情页「记一笔还款」**：金额、收款日期、备注都可自由填写，默认带出本期剩余金额但不强制；
  支持小于一期、跨多期、多还、提前还。
- **还款记录**：逐笔展示金额/日期/备注，可单独删除任意一笔（不再只能撤销最后一笔）。
- **进度算法**：按期数进度用「累计已还 ≥ 累计应还」计算覆盖期数，
  下一期应还 = 累计应还 − 已还（不超过该期金额）；进度百分比仍按金额。
- 兼容旧数据：`period > 0` 的旧还款继续按期数计入；新自由还款 `period = 0`。
- JSON 新增字段（向后兼容，仍为 version 2）：
  - `loans[].reminderEnabled`
  - `payments[].id`、`payments[].note`；`payments[].period = 0` 表示自由还款。
- 新增单测：部分还款、多还结清、关闭提醒后不判逾期、多笔自由还款 JSON 往返（共 86 个测试）。
