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
  // 播放画面：全屏 XComponent 铺在 Compose 下方，surfaceId 经 NAPI 交给 native AVPlayer。
  //
  // 层序说明（2026-11-14）：Stack 顺序「XComponent 在下、Compose 在上」是
  // SurfaceView 式打洞模型——视频层在下、UI 层在上；har 以 TEXTURE XComponent /
  // CanvasNode 渲染，未绘制区域可以透出下层视频。外层 Column 显式透明，
  // Kotlin 侧播放页根 Box 在出画时也让位。此前画面全黑的原因不是层序，
  // 而是 Kotlin 侧播放页根 Box 自己铺了不透明黑底，把视频盖住了；修复在
  // PlayerScreen.kt：surface 绑定且不在缓冲/错误态时根背景让位。
  // 内嵌小窗（InlinePlayer）仍走黑底占位：全屏 XComponent 与 16:9 小窗的
  // 坐标对齐需要 Compose↔ArkUI interop，尚未做，详见 docs/ohos-player-notes.md。
  private surfaceController: XComponentController = new XComponentController();
  private surfaceId: string = '';

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
    // Stack 的绘制顺序：先 XComponent（视频层），再 Compose（UI 层）——SurfaceView
    // 式打洞模型。har 的 Compose 以 TEXTURE XComponent / CanvasNode 渲染；
    // 字节码里有 #00000000 / Transparent、上游示例也不传私有 bgColor，但 .d.ets
    // 没有默认值、har 不含源码，缺省透明色只有旁证，未直接证明，仍需真机确认。
    // 故这里不改 har、不传私有属性；外层 Column 显式透明，播放页根 Box 在 surface
    // 生效后透明（PlayerScreen.kt），视频从下层透出；未就绪时 Kotlin 侧画黑底。
    // 其余页面根都有不透明柔光底，不会意外透出视频。全屏 XComponent 只保证
    // 「播放页铺满」这一种版式，内嵌小窗需后续 interop 定位。
    Stack({ alignContent: Alignment.TopStart }) {
      XComponent({
        id: 'dy_video_surface',
        type: XComponentType.SURFACE,
        controller: this.surfaceController
      })
        .onLoad(() => {
          try {
            this.surfaceId = this.surfaceController.getXComponentSurfaceId();
            dengying.setVideoSurfaceId(this.surfaceId);
            hilog.warn(DOMAIN, TAG, 'surface ready: %{public}s', this.surfaceId);
          } catch (e) {
            hilog.error(DOMAIN, TAG, 'surface onLoad failed: %{public}s', JSON.stringify(e));
          }
        })
        .onDestroy(() => {
          try {
            if (this.surfaceId !== '') {
              dengying.clearVideoSurfaceId(this.surfaceId);
            }
          } catch (e) {
            hilog.warn(DOMAIN, TAG, 'surface onDestroy failed: %{public}s', JSON.stringify(e));
          }
          this.surfaceId = '';
        })
        .width('100%')
        .height('100%')

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
            // 【两级】先走 CMP 自己的返回分发（controller.onBackPress），它内部会
            // 调 Kotlin 的 BackHandler 链；这条链路在本工程实测会抛异常（日志
            // `DY/Compose: onBackPress threw: {}`），抛出后 ArkUI 当 false 处理，
            // 于是返回键直接把应用退到后台。所以 try/catch 兜住，再落到我们自己的
            // 分发通道（dengy.dispatchBack，读 Kotlin 的 OhosBackStack）。
            onBackPressed: () => {
              try {
                if (this.controller !== undefined && this.controller.onBackPress()) {
                  return true;
                }
              } catch (e) {
                hilog.warn(DOMAIN, TAG, 'onBackPress threw: %{public}s', JSON.stringify(e));
              }
              return dengying.dispatchBack() === 1;
            }
          })
        } else {
          Text(this.errorMessage === '' ? 'controller pending' : this.errorMessage)
            .fontSize(16)
            .fontColor(Color.Red)
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
    }
    .width('100%')
    .height('100%')
  }

  // har 的 onPageShow 内部会调 getWindowDecorVisible 等窗口能力 API；模拟器/部分
  // 设备不支持这些能力时会抛异常。若不在这里 try/catch，异常会作为 pending
  // exception 挂在 napi_env 上，之后所有 napi_call_function（含 fusion 渲染的
  // tsfn 重绘回调）都会静默失败 —— 实测表现为 notifyRedraw 每帧调用但 draw 0 次。
  onPageShow(): void {
    if (this.controller !== undefined) {
      try {
        this.controller.onPageShow();
      } catch (e) {
        hilog.warn(DOMAIN, TAG, 'onPageShow threw: %{public}s', JSON.stringify(e));
      }
    }
  }

  onPageHide(): void {
    if (this.controller !== undefined) {
      try {
        this.controller.onPageHide();
      } catch (e) {
        hilog.warn(DOMAIN, TAG, 'onPageHide threw: %{public}s', JSON.stringify(e));
      }
    }
  }

  onBackPress(): boolean {
    if (this.controller === undefined) {
      return false;
    }
    try {
      return this.controller.onBackPress();
    } catch (e) {
      hilog.warn(DOMAIN, TAG, 'onBackPress threw: %{public}s', JSON.stringify(e));
      return false;
    }
  }
}
