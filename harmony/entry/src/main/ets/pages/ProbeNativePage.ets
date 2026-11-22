import { hilog } from '@kit.PerformanceAnalysisKit';
import { UIContext, NodeController, FrameNode, RenderNode, DrawContext } from '@kit.ArkUI';
import { drawing } from '@kit.ArkGraphics2D';
import entry from 'libentry.so';

const DOMAIN = 0x0000;
const TAG = 'DY/ProbeN';
let ctorSeq: number = 0;

// v4：三组并排对照
//  A) 纯 ArkTS 建的 RenderNode（对照组，已验证能 draw，绿色）
//  B) nativeCreateRootNode() 建出来的节点（被 draw 次数恒 0 的那个，红色）
//  C) ArkTS 建的 RenderNode，但在它自己的 draw(context) 里把 canvas 转发给 B 的
//     原生节点（entry.nativeOHRenderNodeDraw）—— 若这条路能出画面，
//     说明「原生绘制能力没问题，只是节点进不了 ArkUI 绘制树」，可作绕行方案。
class ProbeRenderNode extends RenderNode {
  private seq: number = 0;
  private drawCount: number = 0;
  private nativeChild: RenderNode | undefined = undefined;

  constructor() {
    super();
    ctorSeq++;
    this.seq = ctorSeq;
    hilog.warn(DOMAIN, TAG, 'ProbeRenderNode ctor #%{public}d', this.seq);
  }

  setNativeChild(node: RenderNode): void {
    this.nativeChild = node;
  }

  draw(context: DrawContext): void {
    this.drawCount++;
    if (this.drawCount <= 3 || this.drawCount % 120 === 0) {
      hilog.warn(DOMAIN, TAG, 'RenderNode.draw seq=%{public}d #%{public}d w=%{public}d h=%{public}d',
        this.seq, this.drawCount, context.size.width, context.size.height);
    }
    try {
      if (this.seq === 1) {
        context.canvas.drawColor(0xFF22AA44, drawing.BlendMode.SRC_OVER);
      } else if (this.seq === 3 && this.nativeChild !== undefined) {
        entry.nativeOHRenderNotifyRedraw(this.nativeChild);
        entry.nativeOHRenderNodeDraw(context, this.nativeChild);
        if (this.drawCount <= 3 || this.drawCount % 120 === 0) {
          hilog.warn(DOMAIN, TAG, 'C) forwarded native draw #%{public}d', this.drawCount);
        }
      } else {
        context.canvas.drawColor(0xFFCC2222, drawing.BlendMode.SRC_OVER);
      }
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'draw threw %{public}s', JSON.stringify(e));
    }
  }
}

class ProbeNativeController extends NodeController {
  private rootNode: FrameNode | null = null;
  private arktsNode: ProbeRenderNode | null = null;
  private nativeNode: RenderNode | null = null;
  private forwardNode: ProbeRenderNode | null = null;
  private invalidateCount: number = 0;

  makeNode(uiContext: UIContext): FrameNode | null {
    hilog.warn(DOMAIN, TAG, 'makeNode called');
    this.rootNode = new FrameNode(uiContext);
    const rn = this.rootNode.getRenderNode();
    if (rn === null) {
      hilog.error(DOMAIN, TAG, 'rootNode.getRenderNode() returned null');
      return this.rootNode;
    }

    // A) 对照组：纯 ArkTS 节点
    this.arktsNode = new ProbeRenderNode();
    this.arktsNode.frame = { x: 0, y: 0, width: 1308, height: 882 };
    rn.appendChild(this.arktsNode);
    hilog.warn(DOMAIN, TAG, 'A) arkts child appended');

    // B) 实验组：native 建的节点
    entry.nativeRegisterNodeConstructor(ProbeRenderNode);
    entry.nativeSetPixelRatio(vp2px(1));
    const created = entry.nativeCreateRootNode();
    this.nativeNode = created as RenderNode;
    this.nativeNode.frame = { x: 0, y: 882, width: 1308, height: 882 };
    rn.appendChild(this.nativeNode);
    hilog.warn(DOMAIN, TAG, 'B) native child appended');

    // B 的关键判据：appendChild 是否真的把它挂进去了
    const c0 = rn.getChild(0);
    const c1 = rn.getChild(1);
    hilog.warn(DOMAIN, TAG, 'B) probe child0IsArkts=%{public}s child1IsNative=%{public}s child1Null=%{public}s',
      String(c0 === this.arktsNode), String(c1 === this.nativeNode), String(c1 === null));

    // C) 转发组：ArkTS 节点托管绘制
    this.forwardNode = new ProbeRenderNode();
    this.forwardNode.setNativeChild(this.nativeNode);
    this.forwardNode.frame = { x: 0, y: 1764, width: 1308, height: 882 };
    rn.appendChild(this.forwardNode);
    hilog.warn(DOMAIN, TAG, 'C) forward child appended');

    return this.rootNode;
  }

  onResize(size: SizeResult): void {
    hilog.warn(DOMAIN, TAG, 'onResize w=%{public}d h=%{public}d', size.width, size.height);
    if (this.arktsNode !== null) {
      this.arktsNode.frame = { x: 0, y: 0, width: size.width, height: size.height / 3 };
    }
    if (this.nativeNode !== null) {
      this.nativeNode.frame = { x: 0, y: size.height / 3, width: size.width, height: size.height / 3 };
    }
    if (this.forwardNode !== null) {
      this.forwardNode.frame = { x: 0, y: size.height * 2 / 3, width: size.width, height: size.height / 3 };
    }
  }

  invalidateProbe(): void {
    this.invalidateCount++;
    const tag = this.invalidateCount <= 3 || this.invalidateCount % 120 === 0;
    if (this.arktsNode !== null) {
      this.arktsNode.invalidate();
    }
    if (this.nativeNode !== null) {
      this.nativeNode.invalidate();
      if (tag) {
        hilog.warn(DOMAIN, TAG, 'invalidateProbe #%{public}d node=ok', this.invalidateCount);
      }
    }
    if (this.forwardNode !== null) {
      this.forwardNode.invalidate();
    }
  }
}

@Entry
@Component
struct ProbeNativePage {
  private controller: ProbeNativeController = new ProbeNativeController();
  private timer: number = -1;

  aboutToAppear(): void {
    hilog.warn(DOMAIN, TAG, 'ProbeNativePage aboutToAppear');
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
