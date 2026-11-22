// 声明与 compose.har 的 ArkUIViewController 结构兼容（d.ts 不能 import ArkTS 文件）。
declare interface DengYingViewController {
  onPageShow(): void;
  onPageHide(): void;
  onSurfaceShow(): void;
  onSurfaceHide(): void;
  onBackPress(): boolean;
}

declare const dengying: {
  normalizeTitle(title: string): string;
  toHalfWidth(text: string): string;
  parseYear(yearText: string): number;
  ofRaw(title: string, yearText: string): string;
  registerHttpExecutor(executor: (json: string) => void): void;
  submitHttpResult(id: number, status: number, binary: number, payloadBase64: string, headersText?: string): void;
  startHttpTest(url: string): number;
  httpTestRunning(): number;
  httpTestResult(): string | null;
  appInit(dataDir: string): number;
  homeCategories(): string;
  fetchCategory(category: string, page: number): number;
  searchAll(keyword: string): number;
  taskRunning(): number;
  taskResult(): string | null;
  cancelSearch(): number;
  startSuggest(keyword: string): number;
  suggestResult(): string;
  getHistory(): string;
  refreshHistory(): number;
  removeHistory(keyword: string): number;
  clearHistory(): number;
  /** CMP 渲染层（libdengyui.so）的控制器，交给 compose 的 Compose({controller}) */
  MainArkUIViewController(): DengYingViewController;
  /** XComponent 的 surfaceId（十进制字符串）交给 native AVPlayer 显示画面。 */
  setVideoSurfaceId(surfaceId: string): void;
  /** XComponent 销毁时清理对应 surface（旧 id 不会拆掉新 surface）。 */
  clearVideoSurfaceId(surfaceId: string): void;
  /** ArkTS 窗口方向推送到 Kotlin（1=横屏，0=竖屏），驱动全屏控件分支。 */
  setLandscape(value: number): void;
  /**
   * 反向通道：注册「Kotlin 请求转屏/收系统栏」的 sink。
   * Kotlin 的 FullscreenState.enter/exit 经 libentry.so 的
   * `dengy_request_orientation` 投递到这里，由 ArkTS 调 window 接口真正生效。
   * @param sink 收到 1=进入全屏横屏（收系统栏），0=退出全屏（恢复竖屏+系统栏）
   */
  registerOrientationSink(sink: (landscape: number) => void): void;
  /** 应用版本推送到 Kotlin（更新检查用）。 */
  setAppVersion(versionName: string, versionCode: number, debug: number): void;
  /**
   * 注册「壳动作」执行器（Kotlin → ArkTS 反向调用）。
   * op: 1=打开外部链接 2=写系统剪贴板；arg 为链接或文本。
   */
  registerShellSink(sink: (op: number, arg: string) => void): void;

  // ---- fusion 渲染后端（rendererBackend=fusion-renderer）----
  // compose.har 的 modules.abc 按这 6 个裸名从 libentry.so 取 native 函数；
  // 缺任一个会在 ArkTS 侧 import 阶段直接 SyntaxError。宿主 napi_init.cpp
  // 逐个转发到 libdengyui.so 的 @CName 裸名（FusionShim.ohos.kt）。
  nativeCreateRootNode(): object;
  nativeOHRenderNodeDraw(canvas: object, node: object): void;
  nativeOHRenderNotifyRedraw(node: object): void;
  nativeRegisterNodeConstructor(ctor: object): void;
  nativeRegisterNodeStatusModifyConstructor(ctor: object): void;
  nativeSetPixelRatio(ratio: number): void;
};

export default dengying;
