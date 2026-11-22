import { hilog } from '@kit.PerformanceAnalysisKit';
// SizeResult 是全局 ambient 接口（component/common.d.ts:29873 declare interface SizeResult），
// 不在 @kit.ArkUI 的导出面里 —— 从 import 列表去掉，直接用全局声明。
import { UIContext, NodeController, FrameNode, RenderNode, DrawContext } from '@kit.ArkUI';
import { drawing } from '@kit.ArkGraphics2D';

const DOMAIN = 0x0000;
const TAG = 'DY/Probe';

/**
 * 纯 ArkTS 探针页（2026-11-05）。
 *
 * 目的：把「鸿蒙 Compose 首帧不上屏」的问题与 Compose/Kotlin/har 完全隔离。
 * 本页只用 ArkUI 自己的 NodeContainer + NodeController + RenderNode，
 * 不 import compose、不 import libentry.so、不调用任何 Kotlin 代码。
 *
 * 判定方式（看 hilog 里 DY/Probe 的计数）：
 *   - RenderNode.draw 有计数、且截图能变蓝 → ArkUI 的 RenderNode 绘制链路正常，
 *     问题在我们 .so / har 的契约侧。
 *   - RenderNode.draw 恒为 0 → 模拟器（API 23 x86_64）本身不支持 RenderNode 绘制，
 *     与 `getWindowDecorVisible: Capability not supported` 同源，属设备能力缺失。
 */
class ProbeRenderNode extends RenderNode {
  private drawCount: number = 0;

  draw(context: DrawContext): void {
    this.drawCount++;
    hilog.warn(DOMAIN, TAG, 'RenderNode.draw #%{public}d w=%{public}d h=%{public}d',
      this.drawCount, context.size.width, context.size.height);
    try {
      context.canvas.drawColor(0xFF2244FF, drawing.BlendMode.SRC_OVER);
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'drawColor threw: %{public}s', JSON.stringify(e));
    }
  }
}

class ProbeController extends NodeController {
  private renderNode: ProbeRenderNode | null = null;
  private rootNode: FrameNode | null = null;
  private invalidateCount: number = 0;

  makeNode(uiContext: UIContext): FrameNode | null {
    hilog.warn(DOMAIN, TAG, 'makeNode called');
    this.rootNode = new FrameNode(uiContext);
    const rn = this.rootNode.getRenderNode();
    if (rn === null) {
      hilog.error(DOMAIN, TAG, 'rootNode.getRenderNode() returned null');
      return this.rootNode;
    }
    this.renderNode = new ProbeRenderNode();
    this.renderNode.frame = { x: 0, y: 0, width: 1308, height: 2646 };
    this.renderNode.backgroundColor = 0xFF2244FF;
    rn.appendChild(this.renderNode);
    hilog.warn(DOMAIN, TAG, 'appendChild done, rootRenderNode=%{public}s', String(rn));
    return this.rootNode;
  }

  onResize(size: SizeResult): void {
    hilog.warn(DOMAIN, TAG, 'onResize w=%{public}d h=%{public}d', size.width, size.height);
    if (this.renderNode !== null) {
      this.renderNode.frame = { x: 0, y: 0, width: size.width, height: size.height };
    }
  }

  invalidateProbe(): void {
    this.invalidateCount++;
    if (this.invalidateCount <= 3 || this.invalidateCount % 60 === 0) {
      hilog.warn(DOMAIN, TAG, 'invalidateProbe #%{public}d renderNode=%{public}s',
        this.invalidateCount, this.renderNode === null ? 'null' : 'ok');
    }
    if (this.renderNode !== null) {
      this.renderNode.invalidate();
    }
  }
}

@Entry
@Component
struct ProbePage {
  private controller: ProbeController = new ProbeController();
  private timer: number = -1;

  aboutToAppear(): void {
    hilog.warn(DOMAIN, TAG, 'ProbePage aboutToAppear');
    this.timer = setInterval(() => {
      this.controller.invalidateProbe();
    }, 16);
  }

  aboutToDisappear(): void {
    if (this.timer !== -1) {
      clearInterval(this.timer);
      this.timer = -1;
    }
  }

  build() {
    Column() {
      NodeContainer(this.controller)
        .width('100%')
        .height('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.White)
  }
}
