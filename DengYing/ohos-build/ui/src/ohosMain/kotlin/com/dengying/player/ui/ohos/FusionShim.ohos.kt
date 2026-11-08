@file:OptIn(ExperimentalForeignApi::class)
package com.dengying.player.ui.ohos

import org.jetbrains.skia.OHRenderNode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value

/**
 * 旧版 fusion 契约 shim（6 个裸名 C 入口）。
 * compose.har 的 modules.abc 硬编码调用宿主 libentry.so 的 nativeCreateRootNode 等 6 个属性，
 * 宿主 napi_init.cpp 再 dlsym libdengyui.so 的 6 个裸名；而 export 1.9.2-1.1.0 只提供
 * 新 API（initFusionRendererNode 等），旧 6 入口被上游删除 —— 这里补齐，委托 skiko
 * OHRenderNode（fusion 变体静态库 skiko-native-bridges 会随引用被链入）。
 */
@CName("createRootRenderNode")
fun shimCreateRootRenderNode(): napi_value {
    return OHRenderNode.createRootRenderNode(null, null)
}

@CName("setNodeConstructor")
fun shimSetNodeConstructor(env: napi_env, func: napi_value) {
    OHRenderNode.setNodeConstructor(env, func)
}

@CName("renderNodeDraw")
fun shimRenderNodeDraw(env: napi_env, jsCanvas: napi_value, jsNode: napi_value) {
    OHRenderNode.renderNodeDraw(env, jsCanvas, jsNode)
}

@CName("renderNodeNotifyRedraw")
fun shimRenderNodeNotifyRedraw(env: napi_env, jsNode: napi_value) {
    OHRenderNode.renderNodeNotifyRedraw(env, jsNode)
}

@CName("setPixelRatio")
fun shimSetPixelRatio(ratio: Double) {
    OHRenderNode.setPixelRatio(ratio)
}

@CName("buildInstance")
fun shimBuildInstance(env: napi_env, func: napi_value) {
    OHRenderNode.buildInstance(env, func)
}
