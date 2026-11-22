# DengYing · HarmonyOS 壳工程（P6-1 → P6-7）

本目录是 **鸿蒙（HarmonyOS / OpenHarmony）ArkTS + ArkUI 壳**。
P6-1（2026-10-27）时它只是一具能编译的骨架；P6-4（2026-11-01）起，`:shared` 的 Kotlin
逻辑经 **Kotlin/Native ohos_x64 `.so` + 手写 NAPI 桥**真正接进壳里；P6-5/P6-6/P6-7
（2026-11-02）打通 HTTPS 网络桥、数据门面（首页分类聚合）与搜索页（聚合搜索/历史/
联想/取消）——验证记录见 `docs/04-验证记录.md` §16.36–16.39。

## 已实测（Windows，2026-11-01）

| 项 | 实测值 |
|---|---|
| DevEco Studio | `D:\DevEco Studio` |
| SDK | HarmonyOS 6.1.0（API 23，`sdk/default/openharmony`） |
| hvigor | `@ohos/hvigor 6.23.5`，需 `DEVECO_SDK_HOME` 指到 SDK |
| 编译命令 | `node D:/DevEco Studio/tools/hvigor/bin/hvigorw.js --mode module -p product=default assembleHap --no-daemon` |
| 产物 | `entry/build/default/outputs/default/entry-default-signed.hap` **8.1M**（含 `libs/x86_64/` 三个 `.so`） |
| 模拟器 | DevEco Local Emulator `Pura 90 Pro Max`（API 23，**ABI x86_64**）；安装启动后页面显示四条 PASS |

签名：仓库不再携带签名配置，`build-profile.json5` 的 `signingConfigs` 是空数组；
本地需要时按下面「签名配置」一节自行恢复。p12 必须是 legacy PBE 导出的，
openssl 3.x 默认 PBES2/AES 格式 hap-sign-tool 解析不了（`11014003 Init keystore failed`）。

## 签名配置

- **不入库**：签名材料（p12 / p7b / cer、storePassword / keyPassword、本机绝对路径）
  一律不提交。仓库历史中曾出现这些内容，应视为**已泄漏**；请自行在
  AppGallery Connect 轮换证书与口令，不要继续复用旧材料。
- **推荐流程**：DevEco Studio → Project Structure → Signing Configs，本地自动生成
  或导入自己的证书；生成结果只保留在本机 `build-profile.json5`，不要提交。
- **手工恢复**：复制 `build-profile.local.json5.example` 为
  `build-profile.local.json5`（已被 `.gitignore` 忽略），把 `<STORE_PASSWORD>` 等
  占位符替换为真实值，再把 `signingConfigs` 和 `products[0].signingConfig: "default"`
  合并回 `build-profile.json5`。真实 p12 / p7b / cer 放在本机任意目录，
  路径只写进本地文件。
- **未配置签名时**：hvigor 产出未签名 HAP，可编译、可装模拟器；真机发布与上架必须签名。

## shared 接入方式（推翻旧「不可用」结论）

旧版 README 依据本仓 Kotlin 2.4.21 无 ohos target，判定「`:shared` 无法产出 ohos klib」。
该结论已失效：**CPF（毕昇）Kotlin 2.2.21-1.1.0 支持 `ohos_arm64` 与 `ohos_x64`**。
P7 拆分（2026-11-05）后不再有独立的 `ohos-build/`，改为由本工程 `:ui` 模块一次链出：

1. `./gradlew :ui:linkDebugSharedOhosX64` 产出**单个** sharedLib `libdengyui.so`
   （`ui/build/bin/ohosX64/debugShared/`），里面**同时**含 `:ui` 与 `:shared` 的代码
   —— 靠 `ui/build.gradle.kts` 里 `binaries.sharedLib { export(project(":shared")) }`
   把 `:shared` 的 `@CName` 顶层函数（`dengy_app_init` / `dengy_home_categories` 等）
   保留下来；不 export 就会被 DCE 掉，`dlsym` 取不到。模拟器是 x86_64 所以用 ohosX64；
   真机用 ohosArm64。
2. `libdengyui.so` 同时导出两套 C 入口：业务 `dengy_*`（13+ 个）与 Compose 的
   `MainArkUIViewController` / `androidx_compose_ui_arkui_init`，以及 6 个 fusion 裸名
   shim（`createRootRenderNode` 等，见 `ui/src/ohosMain/.../FusionShim.ohos.kt`）。
   壳侧 `entry/src/main/cpp/napi_init.cpp` 不静态链接，而是
   `dlopen("libdengyui.so", RTLD_NOW|RTLD_GLOBAL)` + 逐次 `dlsym` 转发。
3. ArkTS 经 `import dengying from 'libentry.so'` 调用（模块名 `entry`，由 `Init()`
   里 `napi_define_properties` 注册），Compose 页面用 `harmony/entry/libs/compose.har`
   提供的 `Compose({controller, libraryName:'entry', ...})`。
4. `libc++_shared.so` 取自 SDK `native/llvm/lib/x86_64-linux-ohos/`，放 `entry/libs/x86_64/`。

## 目录说明

```
harmony/
├── AppScope/app.json5                    bundleName = com.dengying.player
├── build-profile.json5                   signingConfigs 为空（见「签名配置」）
├── build-profile.local.json5.example     本地签名模板（只有占位符，不含真实值）
├── entry/
│   ├── build-profile.json5               externalNativeOptions → src/main/cpp/CMakeLists.txt
│   ├── libs/x86_64/                      libc++_shared.so（手工放置；libdengyui.so 不放这，
│   │                                     走 thirdparty 单通道，双份会撞 hvigor 00306049）
│   ├── oh-package.json5                  依赖 libdengyingnapi.so (types 目录)
│   └── src/main/
│       ├── cpp/                          napi_init.cpp / CMakeLists.txt / thirdparty/(libdengyui.so) / include/ / types/
│       ├── ets/pages/Index.ets           ← NAPI 探针页（四条 PASS/FAIL）
│       └── resources/base/{element,media,profile}
```

## 边界声明（不粉饰）

- 已接入并实测（模拟器）：TitleKey、HTTPS 网络桥（GET/POST、base64、超时兜底）、
  首页分类聚合（120 条出真实内容）、搜索页（聚合/流式 partial/取消/历史落盘/chips
  删清/联想链路）。**未接入**：详情、播放、收藏 UI、源开关 UI、发现/排行。
- `:shared` 的 ohos actual 成熟度（§16.15 口径 + §16.39 增量）：`HttpTransport`
  可用（含 3s 缓冲硬超时）、`RsaCipher` 仍是桩（搜索聚合撞上会整源失败但不再杀进程）、
  `MediaCacheHost`/`PlaybackProbe` 是桩。
- 模拟器（x86_64）口径已验证；**真机（ohosArm64）链路未走通**。
- `compatibleSdkVersion` 沿用模板 `5.0.0(12)`，未按 API 23 重新评估。

## 装机验证纪律（§16.39 三天排查换来的）

1. 每轮改动 **bump `AppScope/app.json5` versionCode** 或 uninstall+install——
   `install -r` 同版本号静默不更新（launcher hilog `appInstall_CacheManager` 可查真实 installTime）。
2. 日志一律 `Log.warn` + domain ≤ 0xFFFF（0xA0000 被 hilog 静默吞）。
3. napi 桥取符号**每次 dlsym、不按类型 static 缓存**（§16.39.3-1 事故）。
4. 验证装机生效用 marker 串（dumpLayout 找改动文本），别信 hvigor/install 回执。
5. 构建必须 JDK18（`export JAVA_HOME="C:/Program Files/Java/jdk-18.0.2.1"`）；
   hvigor 用 `JAVA_HOME="D:/DevEco Studio/jbr"` + `DEVECO_SDK_HOME="D:/DevEco Studio/sdk"`。
