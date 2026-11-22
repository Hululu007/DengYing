/**
 * 最小环境声明：hvigorfile.ts 里的 `@ohos/hvigor-ohos-plugin` 由 DevEco 自带工具链
 * （D:/DevEco Studio/tools/hvigor/hvigor-ohos-plugin）在构建期解析，项目内不存在
 * node_modules，因此 tsc / LSP 必然报 TS2307。
 *
 * 本文件只声明 hvigorfile.ts 实际用到的两个导出（appTasks / hapTasks），
 * 让静态检查有权威依据可解析；它不参与 hvigor 构建，也不改变运行时行为。
 * 真实构建证据以 hvigor 产出 hap 为准（见 harmony/README.md）。
 */
declare module '@ohos/hvigor-ohos-plugin' {
  export const appTasks: unknown;
  export const hapTasks: unknown;
}
