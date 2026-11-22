import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
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
}
