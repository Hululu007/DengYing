import { hilog } from '@kit.PerformanceAnalysisKit';
import { ArkUIViewController, Compose } from 'compose';
import dengying from 'libentry.so';

const DOMAIN = 0x0000;
const TAG = 'DY/Compose';

@Entry
@Component
struct ComposePage {
  private controller: ArkUIViewController | undefined = undefined;
  @State errorMessage: string = '';

  aboutToAppear(): void {
    try {
      this.controller = dengying.MainArkUIViewController();
      hilog.warn(DOMAIN, TAG, 'controller created %{public}s',
        this.controller === undefined ? 'null' : 'ok');
    } catch (e) {
      this.errorMessage = JSON.stringify(e);
      hilog.error(DOMAIN, TAG, 'controller create failed: %{public}s', this.errorMessage);
    }
  }

  build(): void {
    Column() {
      if (this.controller !== undefined) {
        Compose({
          // pageId 必须与 Kotlin 侧已注册的控制器 id 完全一致：
          // CMP 的 OhosComposeControllerIdContracts 只接受 "Compose_<n>" 形式，
          // 而 har 在 pageId 缺省时会自行拼 "oh_canvas_<n>"，导致 XComponentHolder
          // 用 oh_canvas_0 找不到 render（CMP.ControllerRegistry.IdRejected reason=invalid）。
          pageId: 'Compose_0',
          controller: this.controller,
          libraryName: 'entry',
          onBackPressed: () => this.controller !== undefined ? this.controller.onBackPress() : false
        })
      } else {
        Text(this.errorMessage === '' ? 'controller pending' : this.errorMessage)
          .fontSize(16)
          .fontColor(Color.Red)
      }
    }
    .width('100%')
    .height('100%')
  }

  onPageShow(): void {
    if (this.controller !== undefined) {
      this.controller.onPageShow();
    }
  }

  onPageHide(): void {
    if (this.controller !== undefined) {
      this.controller.onPageHide();
    }
  }

  onBackPress(): boolean {
    return this.controller !== undefined ? this.controller.onBackPress() : false;
  }
}
