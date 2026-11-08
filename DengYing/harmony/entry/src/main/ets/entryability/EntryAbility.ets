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
