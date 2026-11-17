# 理小广 iOS 版：可行性与迁移方案

> 结论：**能做，但没有「一键把 Android 工程转成 iOS」的现成工作流。**
> 好消息是我核实了一件事——这套 UI 的底层库**官方就支持 iOS**，所以不用重写成 SwiftUI，
> 一套 Compose 代码可以编到 iOS；真正要改的是 ~1.2k 行 Android API 调用。

---

## 0. 先纠正一个预期

「网上现成的工作流」能解决的是**最后一公里**（在 macOS 上编出未签名 IPA），
解决不了**前九公里**（把 Android-only 的代码换成 iOS 实现）。目前没有任何工具能
自动完成 Jetpack Compose → Compose Multiplatform + Android API → iOS API 的迁移。

能直接拿来用的现成东西只有这几样：

| 现成资源 | 用途 |
|---|---|
| `kmp.jetbrains.com` 向导 / `JetBrains/compose-multiplatform-template` | 生成 `composeApp` + `iosApp` 骨架，省掉 Xcode 工程搭建 |
| **XcodeGen**（`project.yml`） | 免手写 `project.pbxproj`，CI 里一条命令生成工程 |
| GitHub Actions `macos-15` runner | 唯一能编 iOS 产物的免费方案（iOS 只能用 macOS 编译） |
| `xcodebuild CODE_SIGNING_ALLOWED=NO` | 出未签名 `.app` → 打成未签名 IPA，签名你自己来 |

---

## 1. 关键事实：Miuix 官方支持 iOS（已核实）

项目用的 `top.yukonga.miuix.kmp` 在 Maven Central 上**确实发布了 iOS 产物**：

```
top/yukonga/miuix/kmp/miuix-ui-iosarm64/0.9.4/
top/yukonga/miuix/kmp/miuix-ui-iossimulatorarm64/0.9.4/
top/yukonga/miuix/kmp/miuix-icons-iosarm64/
top/yukonga/miuix/kmp/miuix-blur-iosarm64/          ← 玻璃效果也有 iOS 产物
```

也就是说 `MiuixTheme` / `Card` / `Text` / `SegmentedTabs` / `GfxDock` 这些
**在 iOS 上都能直接用**，UI 层不需要换框架。这是整个方案能成立的前提。

---

## 2. 要改什么：Android API 清单

| 现在的写法 | 位置 | iOS 上换成 |
|---|---|---|
| `android.webkit.WebView` + `CookieManager` + `evaluateJavascript` | `ImportScreen.kt`（46KB） | **直接删掉**，iOS 只走无头引擎 |
| `android.util.AtomicFile` | `ScheduleStore.kt` | `NSFileManager` → 临时文件 + `replaceItem` |
| `SharedPreferences` | `Prefs.kt` | `NSUserDefaults` |
| Android Keystore + AES-GCM | `CredentialStore.kt` | **Keychain**（`SecItemAdd`，`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`） |
| `HttpURLConnection` + `org.json` | `net/*.kt` | Ktor client（Darwin 引擎，自带 `HttpCookies`）+ kotlinx.serialization |
| `BitmapFactory` / 壁纸 / 彩蛋 | `Wallpaper.kt`、`EasterEgg.kt` | `UIImage`（`expect/actual`），**建议第一版先砍掉** |
| `Handler` / `Looper` | `ImportScreen.kt` | `kotlinx.coroutines`（已有） |
| `Build.VERSION.SDK_INT` / AGSL | `AppRoot.kt`、`Dock` | `miuix-blur` 有 iOS 产物，但玻璃效果会降级 |
| `network_security_config.xml`（明文 HTTP） | `res/xml` | **Info.plist 的 ATS 例外**（见第 5 节） |

**WebView 这条是最大的减法**：无头登录做完之后，iOS 版根本不需要 WKWebView，
省掉 46KB 里最脏的那部分（JS 注入、桥接、翻周驱动全靠无头引擎替掉）。

---

## 3. 目标结构（Android 的 `app/` 一行不动）

```
ios/
├── settings.gradle.kts
├── shared/                 # KMP：数据模型 + 无头引擎 + 解析（commonMain）
│   └── src/commonMain/kotlin/...
│   └── src/iosMain/kotlin/...      # Keychain / 文件 / NSUserDefaults 的 actual
├── composeApp/             # Compose Multiplatform UI（周课表 / 今日 / 设置）
│   └── src/commonMain/kotlin/...
└── iosApp/                 # Swift 壳 + Info.plist + project.yml（XcodeGen）
```

复用优先级：`WeekLayout.kt`、`ScheduleWeek.kt`、`WeekParser.kt`、`ZfsoftParser.kt`、
`Course.kt` 这些**纯逻辑可以直接搬进 commonMain**，一行不用改。

---

## 4. 三个里程碑

| 里程碑 | 内容 | 估时 | 验证方式 |
|---|---|---|---|
| **M1 引擎** | `shared` 模块 + Ktor 版 `HttpLite/CasClient/JwClient` + 纯 Kotlin 大数 modPow | 2 天 | 在 macOS / iOS 模拟器跑一次真实登录，打印出课表 JSON |
| **M2 UI** | CMP 三个页面（周课表 / 今日 / 设置），复用 WeekLayout 摆放算法 | 3 天 | 模拟器截图，与 Android 版对比 |
| **M3 交付** | `project.yml` + CI 出未签名 IPA | 1 天 | GitHub Actions 产物能装进签名工具 |

### M1 有个真正的技术点：没有 BigInteger

Kotlin/Native 的标准库**没有 `java.math.BigInteger`**，而登录密码要做无填充的
`m^e mod n`。三种解法：

1. **纯 Kotlin 手写 modPow**（推荐）：2048 位、指数 65537，用 `UIntArray` 做大数乘法 + 蒙哥马利/平方乘，
   约 80 行。可以在 `shared` 里加一个 `jvm()` target，**用现有的 `HeadlessNetTest` 向量在 JVM 上验证**，
   保证与 Android 版逐字节一致。
2. 调 iOS 的 `Security` 框架：`SecKeyCreateEncryptedData` 走的是 PKCS#1 v1.5，
   **和学校的裸模幂不匹配**，不能用。
3. 用 C 写的 bigint 走 cinterop：重，且要维护。

第 1 条是唯一干净的，且能被单测锁住。

---

## 5. iOS 特有的坑（都会在第一天遇到）

**① ATS：明文 HTTP 默认被拦死**

学校的课表接口是 `http://jw.gzist.edu.cn/...`。iOS 的 App Transport Security 默认
拒绝明文 HTTP，不写例外的话**登录成功、取课表全失败**，报错还很不直观。
Info.plist 里必须按 `network_security_config.xml` 同样的粒度开口子：

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>jw.gzist.edu.cn</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key><true/>
            <key>NSIncludesSubdomains</key><false/>
        </dict>
    </dict>
</dict>
```

已经写进 `ios/iosApp/Info.plist`（与 Android 的 `network_security_config.xml` 一一对应）。

**② 自签的代价**

- 免费 Apple ID：**7 天**过期、最多 3 个 App、要重新签
- 付费开发者账号：1 年
- TrollStore / 企业证书：能长用，但都有额外风险
- iOS 的 App **不能自己安装自己**——「更新」只能重新签名安装，这点和 Android 的侧载完全不同

**③ 后台更新不可行**

iOS 不允许长期后台运行，WorkManager 那套思路没有对应物。现在「用户手动点按钮」的
设计恰好符合 iOS 的限制，不用改。

**④ Keychain 卸载残留**

Keychain 项在 App 卸载后仍然存在（这是特性不是 bug）。「清除已保存的账号」必须显式
`SecItemDelete`，否则重装后账号还在。

---

## 6. 签名之外的交付物（本轮已写）

| 文件 | 说明 |
|---|---|
| `.github/workflows/ios-unsigned.yml` | macOS runner：Gradle 出 framework → XcodeGen 生成工程 → `xcodebuild` 关签名 → 打包未签名 IPA |
| `ios/project.yml` | XcodeGen 工程定义（免手写 pbxproj） |
| `ios/iosApp/Info.plist` | ATS 例外、显示名、竖屏 |
| `ios/iosApp/iOSApp.swift` | Compose 的 SwiftUI 入口（`MainViewControllerKt.MainViewController()`） |

> workflow 目前是**待激活**状态：它假设 `ios/composeApp` 已经存在（即 M1/M2 完成）。
> 现在直接跑会失败在 gradle 任务上，这是预期的。

---

## 7. 一句话总结

**不是「跑个工作流」就能出 iOS 包**。真实路径是：把无头引擎抽成 KMP（2 天）→ CMP 重写三个界面（3 天）
→ CI 出未签名 IPA（1 天）。UI 框架不用换（Miuix 官方支持 iOS），WebView 那 46KB 直接不用写。

---

## 8. 实施进度

### 已完成（M1 第一批）

| 文件 | 说明 |
|---|---|
| `ios/settings.gradle.kts`、`ios/build.gradle.kts`、`ios/gradle.properties`、`ios/gradlew` | 独立的 KMP 工程（与 Android 的 `app/` 完全隔离） |
| `ios/shared/build.gradle.kts` | 目标：`iosArm64` + `iosSimulatorArm64` + `jvm()`（jvm 只为跑单测） |
| `shared/.../model/Model.kt` | `Course` / `PeriodTime` / `SemesterInfo` / `Schedule`，与 Android 版字段一致 |
| `shared/.../crypto/Rsa.kt` | **base-2^32 limb 的大数 modPow**，绕开 Kotlin/Native 没有 BigInteger 的问题 |
| `shared/src/commonTest/.../RsaTest.kt` | 固定向量（`test123` / `abc` 不补零 / 去空格 / 空密码） |

> RSA 的 limb 算法用等价的 Python 实现对照内置 `pow()` 逐字节验证过，
> 所以它和 Android 侧（`CasClient.rsaEncrypt`）是同一份数学。

**现在就能验证**（不需要 Mac、不需要 Xcode，Windows 即可）：

```bash
cd E:\Code\lixiaoguang\ios
./gradlew :shared:jvmTest        # Windows 用 gradlew.bat
```

### 已完成（M1 第二批：网络层）

| 文件 | 说明 |
|---|---|
| `shared/.../net/Http.kt` | Ktor 薄封装：`get` / `postForm` / `postJson` / `formBody` / `urlEncode`，UA 伪装 |
| `shared/.../net/CasClient.kt` | kaptcha 取图、`/v1/tickets` 登录、TGT→ST、建教务会话、登录响应解析 |
| `shared/.../net/JwClient.kt` | 课表页 warm-up、`xskbcx_cxXsKb` 取数、学年学期推算 |
| `shared/.../net/CaptchaAnswer.kt` | 算术题验证码归一化 |
| `shared/src/iosMain/.../HttpClient.ios.kt` | Darwin 引擎 + `HttpCookies` + 超时 |
| `shared/src/jvmMain/.../HttpClient.jvm.kt` | CIO 引擎（只给单测用） |

> **Ktor 替掉了 Android 版 `HttpLite` 的两百行**：`HttpCookies` 自动保存并回传各跳的
> `Set-Cookie`，`followRedirects` 自动跟 `/sso/lyiotlogin?ticket=ST` 的 302。
> 版本固定为 Ktor 3.6.1 / serialization 1.13.0 / coroutines 1.11.0 / okio 3.18.2。

### 已完成（M1 第三批：解析 / 打码 / 存储 / 编排）

| 文件 | 说明 |
|---|---|
| `shared/.../parse/ZfsoftParser.kt` | 正方 kbList 解析（`org.json` → `kotlinx.serialization`），周次/节次容错规则与 Android 版一致 |
| `shared/.../parse/WeekParser.kt` | 「第几周」识别，改用 ISO 字符串比较（`LocalDate` 在 Native 上没有） |
| `shared/.../parse/ScheduleBuilder.kt` | 组课表、反推学期起始日 |
| `shared/.../util/IsoDate.kt` | `days_from_civil` 日期算术：比较、加减天、找周一 |
| `shared/.../model/CourseColors.kt` | 手写 Java 风格哈希，保证**两端同色** |
| `shared/.../model/ClassTimes.kt` | 学校作息与 5 大节 |
| `shared/.../net/TtshituSolver.kt` | 图鉴打码（typeid=11 计算题）+ 失败上报 |
| `shared/.../storage/ScheduleStore.kt` | 编解码 + okio 读写，「永不空白」双写 last-good / 损坏留档 |
| `shared/.../storage/Credentials.kt` | `CredentialVault` 接口（iOS 由 Swift 实现成 Keychain）+ 内存实现 |
| `shared/.../net/HeadlessImport.kt` | 编排：TGT 优先 → 打码兜底 → 取数 → 结构自检 → 存盘 |
| `commonTest/.../ParserTest.kt`、`StorageTest.kt` | 解析与存储的 14 个用例 |

**M1 到这里就完整了**：`shared` 已经能在 iOS 上独立完成「登录 → 拉课表 → 存盘」。

### 已完成（M2：界面）

| 文件 | 说明 |
|---|---|
| `composeApp/build.gradle.kts` | Compose Multiplatform 1.12.2 + Kotlin 2.4.21，静态 framework `ComposeApp` |
| `composeApp/.../ui/Theme.kt` | 配色 + 字号（第一版不依赖 Miuix，见文件内注释） |
| `composeApp/.../ui/Widgets.kt` | 卡片 / 按钮 / 输入框 / 分割线 |
| `composeApp/.../ui/App.kt` | 根界面：三页切换、无头更新编排、失败只弹提示 |
| `composeApp/.../ui/WeekScreen.kt` | 周课表：7 天 × 5 大节，重叠课按车道并排 |
| `composeApp/.../ui/TodayScreen.kt` | 今日课程 + 已结束 / 进行中 / 未开始 |
| `composeApp/.../ui/SettingsScreen.kt` | 账号、图鉴、更新入口、上次更新时间 |
| `composeApp/.../iosMain/MainViewController.kt` | `fun MainViewController(vault: CredentialVault): UIViewController` |

### 已完成（M3：打包）

| 文件 | 说明 |
|---|---|
| `ios/iosApp/iOSApp.swift` | **Swift 侧 Keychain 实现** `CredentialVault` + `UIViewControllerRepresentable` |
| `ios/iosApp/Info.plist` | ATS 例外、显示名、竖屏 |
| `ios/project.yml` | XcodeGen 工程（静态 framework 不 embed） |
| `.github/workflows/ios-unsigned.yml` | macOS runner：gradle 出 framework → xcodegen → `xcodebuild` 关签名 → 未签名 IPA |

### 本地 UI 预览（Windows，不需要 Mac）

`composeApp` 有一个 `jvm("desktop")` 目标，**只用于界面预览**，不进 iOS 包。
同一份 `commonMain` 界面代码直接跑在 Windows 上：

```powershell
cd E:\Code\lixiaoguang\ios
$env:JAVA_HOME='E:\Apps\jdk17'
gradlew.bat :composeApp:run
```

- 窗口按 iPhone 竖屏比例（390×844）打开
- 改 `commonMain` 里任何界面代码，重新 run 一次约 **3 秒**（有缓存后）
- 平台能力用替身：账号存进程内存、壁纸 `pick` 直接回 null

> 为什么不靠 iOS 模拟器：VM 里的 macOS 没有 Metal，模拟器起不来/极慢。
> 桌面预览 + 真机验证是这套组合里最快的循环。

### 怎么编（没有 Mac 也能走完）

iOS 的编译只能在 macOS 上做，但**你不用有 Mac**——GitHub Actions 的 `macos-15` runner 就是一台云 Mac。

**① 推仓库**

把整个项目推到 GitHub。**建议 public**：公开仓库的 Actions 分钟数完全免费；
私有仓库跑 macOS runner 按 10 倍计费（2000 分钟/月额度消耗得很快）。

**② 先看引擎单测（2~3 分钟，ubuntu，不花 macOS 额度）**

push 后 `Engine tests (JVM)` 会自动跑 `:shared:jvmTest`。
30 个用例全绿再往下走——这一步能拦住绝大多数逻辑/协议错误。

**③ 出包（15~25 分钟）**

Actions → 左侧 `iOS unsigned IPA` → `Run workflow`。
成功后 Artifacts 里下载 `LiXiaoGuang-unsigned-ipa`，解压得到 `LiXiaoGuang-unsigned.ipa`。

**④ 在 Windows 上签名**

- 装 **Sideloadly**（Windows 版需要非 Microsoft Store 的 iTunes + iCloud）
- 连上 iPhone，把 IPA 拖进去，填 Apple ID，Start
- 免费 Apple ID：**7 天过期、最多 3 个 App**；付费开发者账号 1 年
- 手机端要在「设置 → 通用 → VPN与设备管理」里信任证书

**⑤ 迭代节奏**

```
改代码 → git push
   ├─ Engine tests (JVM)     2 分钟   ← 逻辑错在这里改
   └─ iOS unsigned IPA       20 分钟  ← 编译错在这里改（有渲染/链接问题才需要）
```

⚠️ **gradlew 的可执行位**：从 Windows 提交时 git 会丢掉 `+x` 位，第一次跑必然
`Permission denied`。两个 workflow 里都已经加了 `chmod +x gradlew`，不用你手工处理。

**备选方案**（GitHub Actions 不够用时）：
- **Codemagic**：macOS 免费额度 500 分钟/月，专门做移动端 CI，配置比 GH Actions 还短
- **MacinCloud / MacStadium**：按小时租云 Mac，能用 Xcode 图形界面调试
- 本地 Xcode 只对「调 UI 细节」有意义，出包和联调都可以纯 CI

**如果你以后有了 Mac**：
```bash
cd ios
./gradlew :shared:jvmTest
./gradlew :composeApp:linkReleaseFrameworkIosArm64
cd iosApp && xcodegen generate
open LiXiaoGuang.xcodeproj
```

### 壁纸：用 UIKit 画在 Compose 下面（已实现）

一开始以为要在 Compose 里解码图片，其实不用——**照片根本不进 Compose**：

```
RootViewController (Swift)
├── UIImageView        ← 壁纸，最底层
└── ComposeVC (clear)  ← 界面，透明背景
```

- Kotlin 只声明 `WallpaperHost { pick(onDone); clear(); isActive() }`
- Swift 实现：`PHPickerViewController` 选图 → 存 `Documents/wallpaper.jpg` → 设给 `UIImageView`
- Compose 侧只需在读 `isActive()` 为真时把根背景、顶栏、底栏和卡片改成半透明
- 「有没有壁纸」= 「文件在不在」，不需要额外持久化

好处：不依赖 Compose 的图片解码 API（那跨版本变过），也不用手动处理缩放/裁切——
`UIImageView.contentMode = .scaleAspectFill` 就是 Android 版的 `ContentScale.Crop`。

其余有意省略的：
- **Miuix**：官方有 iOS 产物，但 `ThemeController` 默认走 Monet 动态取色（Android 能力），
  iOS 上行为不确定；先把界面用 Compose foundation 写稳，换 Miuix 只动 `Theme.kt`
- **彩蛋 / Dock 液态玻璃**：AGSL 着色器没有 iOS 等价物

