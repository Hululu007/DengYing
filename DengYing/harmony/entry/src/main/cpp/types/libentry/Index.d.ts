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
  submitHttpResult(id: number, status: number, binary: number, payloadBase64: string): void;
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
};

export default dengying;
