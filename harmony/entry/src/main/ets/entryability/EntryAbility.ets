import { AbilityConstant, bundleManager, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { registerHttpExecutor } from '../net/HttpBridge';
import dengying from 'libentry.so';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    registerHttpExecutor();
    const code = dengying.appInit(this.context.filesDir);
    hilog.info(DOMAIN, 'DengYing', 'appInit code=%{public}d dir=%{public}s', code, this.context.filesDir);
    // 版本号推给 Kotlin：`:ui` 关了 BuildConfig，读不到 HAP 的 build-profile，
    // 更新检查必须与真正安装的包同版本（Android 读 BuildConfig、iOS 读 Info.plist）。
    try {
      // 必须带 WITH_APPLICATION：DEFAULT 标志返回的 BundleInfo 里 appInfo 是 null，
      // 读 appInfo.debug 会抛「Cannot read property debug of null」。
      const info = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
      // debug 标志一并发（编译期常量，HAP 构建形态决定）：鸿蒙没有 BuildConfig 也没有
      // isDebugBinary，release 包里若仍按 debug 处理会露出账号密码表单与「令牌设置」。
      const isDebug = info.appInfo.debug ? 1 : 0;
      dengying.setAppVersion(info.versionName, info.versionCode, isDebug);
      hilog.info(DOMAIN, 'DengYing', 'version=%{public}s code=%{public}d debug=%{public}d', info.versionName, info.versionCode, isDebug);
    } catch (e) {
      const err = e as BusinessError;
      hilog.warn(DOMAIN, 'DengYing', 'setAppVersion failed: code=%{public}d msg=%{public}s', err.code, err.message);
    }
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onWindowStageCreate');

    // 全屏播放控件贴边：让应用内容进入状态栏/手势条区域（Compose 侧留 6dp），
    // 与 Android/iOS 同一口径。失败只记日志，不阻断 loadContent。
    try {
      const mainWindow = windowStage.getMainWindowSync();
      mainWindow.setWindowLayoutFullScreen(true);
      // 横竖屏真值经 libentry.so 推给 Kotlin：rememberIsLandscape() 不再恒 false。
      const props: window.WindowProperties = mainWindow.getWindowProperties();
      const w: number = props.windowRect.width;
      const h: number = props.windowRect.height;
      const isLandscape = w > h;
      dengying.setLandscape(isLandscape ? 1 : 0);
      // 横竖屏切换时补推一次（避免锁死在启动方向）。
      mainWindow.on('windowSizeChange', (size: window.Size) => {
        dengying.setLandscape(size.width > size.height ? 1 : 0);
      });
      // 反向通道：Kotlin 的 FullscreenState.enter/exit 经 NAPI 请求转屏/收系统栏。
      // Kotlin 调不到 ArkUI 的 window 接口，只能由这里真正执行；未注册时
      // Kotlin 侧静默降级（与 Android/iOS 「只请求、不保证生效」同语义）。
      dengying.registerOrientationSink((landscape: number): void => {
        try {
          const toLandscape = landscape === 1;
          hilog.info(DOMAIN, 'DengYing', 'orientation sink landscape=%{public}d', landscape);
          // 两个接口都返回 Promise：不接 then/catch 的话失败会变成未处理的
          // rejection（ArkTS 只打一条错误日志，调用方以为成功）。
          mainWindow.setPreferredOrientation(
            toLandscape ? window.Orientation.LANDSCAPE : window.Orientation.PORTRAIT)
            .catch((e: BusinessError) => {
              hilog.warn(DOMAIN, 'DengYing', 'setPreferredOrientation failed: %{public}s', JSON.stringify(e));
            });
          // 全屏播放时收起状态栏与手势条，内容贴边（Compose 侧留 6dp）。
          mainWindow.setWindowSystemBarEnable(toLandscape ? [] : ['status', 'navigation'])
            .catch((e: BusinessError) => {
              hilog.warn(DOMAIN, 'DengYing', 'setWindowSystemBarEnable failed: %{public}s', JSON.stringify(e));
            });
        } catch (e) {
          hilog.warn(DOMAIN, 'DengYing', 'orientation sink failed: %{public}s', JSON.stringify(e));
        }
      });
      // 反向通道：Kotlin 的 UiDeps.openExternalUrl / copyText 经 NAPI 请求打开链接、
      // 写剪贴板。URL 白名单在 Kotlin 侧（UpdateChecker.isAllowedReleaseUrl）已校验，
      // 这里只负责执行；op 见 OhosShellControl。
      dengying.registerShellSink((op: number, arg: string): void => {
        try {
          hilog.info(DOMAIN, 'DengYing', 'shell sink op=%{public}d', op);
          if (op === 1) {
            this.context.openLink(arg).catch((e: BusinessError) => {
              hilog.warn(DOMAIN, 'DengYing', 'openLink failed: %{public}s', JSON.stringify(e));
            });
          } else if (op === 2) {
            const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, arg);
            pasteboard.getSystemPasteboard().setData(data).catch((e: BusinessError) => {
              hilog.warn(DOMAIN, 'DengYing', 'copyText failed: %{public}s', JSON.stringify(e));
            });
          }
        } catch (e) {
          hilog.warn(DOMAIN, 'DengYing', 'shell sink failed: %{public}s', JSON.stringify(e));
        }
      });
    } catch (e) {
      hilog.warn(DOMAIN, 'DengYing', 'setWindowLayoutFullScreen / orientation failed: %{public}s', JSON.stringify(e));
    }

    windowStage.loadContent('pages/ComposePage', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, 'DengYing', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, 'DengYing', 'Succeeded in loading the content.');
    });
  }

  onWindowStageDestroy(): void {
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onForeground');
  }

  onBackground(): void {
    hilog.info(DOMAIN, 'DengYing', '%{public}s', 'Ability onBackground');
  }

  /**
   * 系统返回键。鸿蒙没有 Android 的 OnBackPressedDispatcher，页面返回只能由
   * 应用自己接：Kotlin 侧（OhosBackStack，LIFO）决定这次返回是否被消费。
   *
   * 返回 true = 已消费（导航 popBackStack / 全屏先退形态），系统不再处理；
   * 返回 false = 没人消费（停在根页），交回默认行为退到后台。
   */
  onBackPressed(): boolean {
    const handled: number = dengying.dispatchBack();
    hilog.info(DOMAIN, 'DengYing', 'onBackPressed handled=%{public}d', handled);
    return handled === 1;
  }
}
