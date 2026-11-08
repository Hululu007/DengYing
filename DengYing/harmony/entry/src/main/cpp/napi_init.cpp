#include <napi/native_api.h>

#include <dlfcn.h>
#include <hilog/log.h>
#include <memory>
#include <string>


// 社区 CMP 导出层（org.jetbrains.compose.export:export 自动生成）：
// 把 ArkTS 侧模块对象注册给 Compose 的 utils（剪贴板/主题/Accessibility 等）。
extern "C" void androidx_compose_ui_arkui_init(void* env, void* exports);

#define BRIDGE_LOG(fmt, ...) OH_LOG_Print(LOG_APP, LOG_WARN, 0x0000, "NapiBridge", fmt, ##__VA_ARGS__)

namespace {

std::string ReadString(napi_env env, napi_value value) {
  size_t len = 0;
  napi_get_value_string_utf8(env, value, nullptr, 0, &len);
  std::string out(len, '\0');
  if (len > 0) {
    napi_get_value_string_utf8(env, value, out.data(), len + 1, &len);
  }
  return out;
}

napi_value WrapString(napi_env env, const char* text) {
  napi_value result = nullptr;
  napi_create_string_utf8(env, text, NAPI_AUTO_LENGTH, &result);
  return result;
}

// ---- P6-5 网络探针：dengy_start_http_test / dengy_http_test_running /
// dengy_http_test_result 三个 @CName 顶层函数。libdengyui.so 是 DT_NEEDED
// 依赖、不在全局符号作用域，RTLD_DEFAULT 搜不到，须显式 dlopen 取句柄。
using StartHttpTestFn = int32_t (*)(const char*);
using HttpTestRunningFn = int32_t (*)();
using HttpTestResultFn = const char* (*)();

void* DengyingHandle() {
  // 逻辑层与 UI 层现在同一个 .so（docs/04 §16.41：两个 .so = 两套 K/N 运行时，
  // 首帧渲染会 SIGSEGV）。所有 dengy_* @CName 都从 libdengyui.so 取。
  static void* handle = dlopen("libdengyui.so", RTLD_NOW | RTLD_GLOBAL);
  return handle;
}

template <typename Fn>
Fn ResolveTop(const char* name) {
  // NOTE: must NOT cache in a function-local static keyed by Fn — several
  // facade symbols share one signature (e.g. AppInitFn == SearchAllFn), so a
  // per-type static hands every later caller the FIRST symbol's pointer.
  return reinterpret_cast<Fn>(dlsym(DengyingHandle(), name));
}

// ---- CMP（社区 Compose Multiplatform ohos 线）宿主桥 ----
// libdengyui.so 是 :ohosUi 的链接产物（60MB，拖 skiko + 全套 Compose），与逻辑层
// 逻辑层（@CName dengy_*）与 Compose UI 现在**同一个 .so**：两个 .so = 两套
// 同样是 DT_NEEDED 依赖、不在全局符号作用域，须显式 dlopen 句柄取址。
using ComposeViewFn = void* (*)(void*);
using ComposeDispatchTouchFn = int32_t (*)(void*, void*);

void* DengyuiHandle() {
  static void* handle = dlopen("libdengyui.so", RTLD_NOW | RTLD_GLOBAL);
  return handle;
}

template <typename Fn>
Fn ResolveUi(const char* name) {
  return reinterpret_cast<Fn>(dlsym(DengyuiHandle(), name));
}

// ---- fusion 注入用的全局缓存 ----
// MainArkUIViewController 返回的 wrapped controller（JS 对象）与
// nativeCreateRootNode 返回给 har 的根节点都必须 napi_ref 持有。
napi_ref g_controllerRef = nullptr;
napi_ref g_rootNodeRef = nullptr;
bool g_fusionInjected = false;
// har 经 nativeRegisterNodeConstructor / nativeRegisterNodeStatusModifyConstructor
// 注册的 JS 构造器必须用 napi_ref 持有 —— 裸 napi_value 出了回调作用域即失效。
// ratio 供 InitJsRenderNodeContext(env, ctor, statusCtor, ratio, fixed) 使用。
napi_ref g_nodeCtorRef = nullptr;
napi_ref g_statusCtorRef = nullptr;
double g_pixelRatio = 3.5;

// 1.1.0 线 .so 的 C++ utils 层导出（llvm-nm 实测，纯转发到 Kotlin @CName）：
// initRenderNodeContext(env, nodeCtor, statusCtor, ratio, fixed)
//   -> Kotlin InitJsRenderNodeContext -> JsRenderNodeBridge.tryInit ->
//      initFusionRendererContext（ArkTsContext.initEnv/initRatio +
//      JsRenderNode.initJsRenderNodeContext(ctor, statusCtor, drawCallback)）
// initFusionRendererNode(controller, env, enableCApi, rootContent, frameMgr, isPreCompose)
//   -> Kotlin initFusionRendererNodeImpl -> 建 JsRenderNode（enableCApi=false 走
//      JsRenderNode()，依赖 isContextReady）+ 存 frameMgr + 登记
//      FUSION_RENDERER_VIEW_MAP[node.id]=container —— 没这步 draw 回调无主。
using InitRenderNodeContextFn =
    void (*)(napi_env, napi_value, napi_value, double, bool);
using InitFusionRendererNodeFn =
    void (*)(void*, napi_env, bool, napi_value, napi_value, bool);
// C++ 壳 GetJsNode(controller) -> napi_value：取 Kotlin 建 JsRenderNode 的
// JS 对象（用 har 注册的 ctor 建的，FUSION_RENDERER_VIEW_MAP 键 = 其 node.id）。
using GetJsNodeFn = napi_value (*)(void*);

// 返回 nullptr = 注入未完成（调用方回退到 shim 建的节点）。
napi_value InjectFusionRenderer(napi_env env) {
  if (g_fusionInjected || g_nodeCtorRef == nullptr ||
      g_statusCtorRef == nullptr || g_controllerRef == nullptr) {
    return nullptr;
  }
  napi_value nodeCtor = nullptr;
  napi_value statusCtor = nullptr;
  napi_value controller = nullptr;
  napi_get_reference_value(env, g_nodeCtorRef, &nodeCtor);
  napi_get_reference_value(env, g_statusCtorRef, &statusCtor);
  napi_get_reference_value(env, g_controllerRef, &controller);
  if (nodeCtor == nullptr || statusCtor == nullptr || controller == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: reference value null");
    return nullptr;
  }

  auto initCtx = ResolveUi<InitRenderNodeContextFn>(
      "_ZN8androidx7compose2ui5arkui5utils41ArkUIViewController_initRenderNode"
      "ContextEP10napi_env__P12napi_value__S7_db");
  if (initCtx == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: initRenderNodeContext symbol missing");
    return nullptr;
  }
  initCtx(env, nodeCtor, statusCtor, g_pixelRatio, false);
  BRIDGE_LOG("InjectFusionRenderer: initRenderNodeContext ok");

  auto initNode = ResolveUi<InitFusionRendererNodeFn>(
      "_ZN8androidx7compose2ui5arkui5utils42ArkUIViewController_initFusion"
      "RendererNodeEPNS3_19ArkUIViewControllerEP10napi_env__bP12napi_value__S9_b");
  if (initNode == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: initFusionRendererNode symbol missing");
    return nullptr;
  }

  // frameMgr stub：1.1.0 帧泵要一个带 postFrameCallback() 方法的 JS 对象。
  // 我们的帧泵由 har 的 displaySync（nativeOHRenderNotifyRedraw @60fps）驱动，
  // stub 的 postFrameCallback 空实现即可；vsync 由 FrameController 页面路径供。
  napi_value frameMgr = nullptr;
  napi_value noop = nullptr;
  napi_create_object(env, &frameMgr);
  napi_create_function(env, "postFrameCallback", NAPI_AUTO_LENGTH,
                       [](napi_env, napi_callback_info) -> napi_value {
                         return nullptr;
                       },
                       nullptr, &noop);
  napi_set_named_property(env, frameMgr, "postFrameCallback", noop);
  // rootContent 只用于挡 Kotlin 的 LTPO 早退（rootContent==null -> return）；
  // enableCApi=false 路径完全不使用它，传 shim 建的根节点即可。
  napi_value rootContent = nullptr;
  if (g_rootNodeRef != nullptr) {
    napi_get_reference_value(env, g_rootNodeRef, &rootContent);
  }
  if (rootContent == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: rootContent null");
    return nullptr;
  }

  // C++ 壳首参 ArkUIViewController* 实际是 Kotlin StableRef 指针，即 wrapped
  // JS 对象 napi_wrap 进去的那个 void* —— napi_unwrap 取回。不能把 JS 对象
  // 指针直接传（Kotlin 侧要 asStableRef 解引用）。
  void* controllerPtr = nullptr;
  if (napi_unwrap(env, controller, &controllerPtr) != napi_ok ||
      controllerPtr == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: napi_unwrap controller failed");
    return nullptr;
  }

  // enableCApi=false 走 JsRenderNode()：CRenderNode(rootContent) 路径已证伪
  // （run_f3 15:52：rootContent 必须是 ArkUI NodeContent，shim 的裸 JS 节点报
  // GetNodeContentFromNapiValue failed: 401 -> Can't wrap nullptr 崩溃）。
  // JsRenderNode 用 har 注册的 ctor 自建节点（isContextReady 已由上面
  // initRenderNodeContext 建立）；rootContent 传非 null 只为挡住 Kotlin 的
  // LTPO 早退（enableCApi=false 时完全不使用它）。
  // isPreCompose=false：让 skipNextInvalidate 不吞掉首帧 invalidate。
  initNode(controllerPtr, env, false, rootContent, frameMgr, false);
  BRIDGE_LOG("InjectFusionRenderer: initFusionRendererNode ok");
  g_fusionInjected = true;

  // 取 Kotlin 自建 JsRenderNode 的 JS 对象返回给 har：har 把它挂上画布后，
  // 每帧 nativeOHRenderNotifyRedraw(jsNode)/nativeOHRenderNodeDraw(canvas,
  // jsNode) 解出来的节点 id 与 FUSION_RENDERER_VIEW_MAP 键一致，draw 回调
  // 才有主。取不到则返回 nullptr，调用方回退 shim 节点。
  auto getJsNode = ResolveUi<GetJsNodeFn>(
      "_ZN8androidx7compose2ui5arkui5utils28ArkUiViewControllerGetJsNodeEPNS3_"
      "19ArkUIViewControllerE");
  if (getJsNode == nullptr) {
    BRIDGE_LOG("InjectFusionRenderer: GetJsNode symbol missing");
    return nullptr;
  }
  napi_value jsNode = getJsNode(controllerPtr);
  BRIDGE_LOG("InjectFusionRenderer: jsNode=%{public}p",
             reinterpret_cast<void*>(jsNode));
  return jsNode;
}

// ArkTS 侧调用：dengying.MainArkUIViewController() -> ArkUIViewController。
// 与社区样例 xiaqingpeng/HarmonyKMP同构：controller 交给 compose.har 的
// Compose({controller, libraryName}) 组件，由它驱动 Surface/触摸/帧回调。
napi_value MainArkUIViewController(napi_env env, napi_callback_info /*info*/) {
  auto make = ResolveUi<ComposeViewFn>("MainArkUIViewController");
  if (make == nullptr) {
    BRIDGE_LOG("MainArkUIViewController: libdengyui missing symbol (%{public}s)",
               dlerror() == nullptr ? "no dlerror" : dlerror());
    napi_value undef = nullptr;
    napi_get_undefined(env, &undef);
    return undef;
  }
  void* controller = make(reinterpret_cast<void*>(env));
  if (controller == nullptr) {
    BRIDGE_LOG("MainArkUIViewController: controller null");
    napi_value undef = nullptr;
    napi_get_undefined(env, &undef);
    return undef;
  }
  BRIDGE_LOG("MainArkUIViewController: controller=%{public}p", controller);
  // fusion 注入发生在 nativeCreateRootNode 里，controller 必须在这里提前缓存。
  if (g_controllerRef == nullptr) {
    napi_create_reference(env, reinterpret_cast<napi_value>(controller), 1,
                          &g_controllerRef);
  }
  return reinterpret_cast<napi_value>(controller);
}

// ---- fusion-renderer 的 6 个 NAPI（compose.har 的 Compose 组件在 aboutToAppear
// 时就具名 import 它们；缺一个 -> ES module SyntaxError -> onAbilityDied）。
// 实现在 libdengyui.so 里，是 export 1.9.2-OH.* 的 @CName 导出（裸 C 名）：
// createRootRenderNode / renderNodeDraw / renderNodeNotifyRedraw /
// setNodeConstructor / buildInstance / setPixelRatio。
using CreateRootNodeFn = void* (*)();
using RenderNodeDrawFn = void (*)(void*, void*, void*);
using RenderNodeNotifyRedrawFn = void (*)(void*, void*);
using NodeCtorFn = void (*)(void*, void*);
using SetPixelRatioFn = void (*)(double);

napi_value NativeCreateRootNode(napi_env env, napi_callback_info /*info*/) {
  BRIDGE_LOG("nativeCreateRootNode: called");
  auto fn = ResolveUi<CreateRootNodeFn>("createRootRenderNode");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeCreateRootNode: symbol missing (%{public}s)",
               dlerror() == nullptr ? "no dlerror" : dlerror());
    return nullptr;
  }
  void* root = fn();
  if (root == nullptr) {
    BRIDGE_LOG("nativeCreateRootNode: root null");
    return nullptr;
  }
  // 持有 rootContent：后面 initFusionRendererNode 注入要用。
  if (g_rootNodeRef == nullptr) {
    napi_create_reference(env, reinterpret_cast<napi_value>(root), 1,
                          &g_rootNodeRef);
  }
  BRIDGE_LOG("nativeCreateRootNode: root=%{public}p", root);

  // ---- 1.1.0 线 fusion 注入（替代新版 har 的 FusionRendererCompose）----
  // har（旧协议）永远不会调 initFusionRendererNode / InitJsRenderNodeContext，
  // 由宿主在这里补齐两次注入。controller 也在这个时机缓存。
  // 注入成功返回 Kotlin 自建 JsRenderNode 的 JS 对象 —— FUSION_RENDERER_VIEW_MAP
  // 键 = 它的 node.id，har 每帧 nativeOHRenderNotifyRedraw(它) 触发的 draw 回调
  // 才有主；shim 建的根节点只充当 rootContent dummy（挡 LTPO 早退）。
  napi_value jsNode = InjectFusionRenderer(env);
  if (jsNode != nullptr) {
    return jsNode;
  }
  return reinterpret_cast<napi_value>(root);
}

napi_value NativeRenderNodeDraw(napi_env env, napi_callback_info info) {
  size_t argc = 2;
  napi_value argv[2] = {nullptr, nullptr};
  napi_status status = napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
  if (status != napi_ok || argc < 2) {
    napi_throw_type_error(env, nullptr, "Expected canvas argument");
    return nullptr;
  }
  BRIDGE_LOG("nativeOHRenderNodeDraw: called");
  auto fn = ResolveUi<RenderNodeDrawFn>("renderNodeDraw");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeOHRenderNodeDraw: symbol missing");
    return nullptr;
  }
  fn(reinterpret_cast<void*>(env), reinterpret_cast<void*>(argv[0]),
     reinterpret_cast<void*>(argv[1]));
  return nullptr;
}

napi_value NativeRenderNodeNotifyRedraw(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value argv[1] = {nullptr};
  napi_status status = napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
  if (status != napi_ok || argc < 1) {
    napi_throw_type_error(env, nullptr, "Expected renderNode argument");
    return nullptr;
  }
  BRIDGE_LOG("nativeOHRenderNotifyRedraw: called");
  auto fn = ResolveUi<RenderNodeNotifyRedrawFn>("renderNodeNotifyRedraw");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeOHRenderNotifyRedraw: symbol missing");
    return nullptr;
  }
  fn(reinterpret_cast<void*>(env), reinterpret_cast<void*>(argv[0]));
  return nullptr;
}

napi_value NativeRegisterNodeConstructor(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value argv[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
  if (argc < 1) {
    return nullptr;
  }
  if (g_nodeCtorRef == nullptr) {
    napi_create_reference(env, argv[0], 1, &g_nodeCtorRef);
  }
  BRIDGE_LOG("nativeRegisterNodeConstructor: ctor cached ref=%{public}p",
             reinterpret_cast<void*>(g_nodeCtorRef));
  auto fn = ResolveUi<NodeCtorFn>("setNodeConstructor");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeRegisterNodeConstructor: symbol missing");
    return nullptr;
  }
  fn(reinterpret_cast<void*>(env), reinterpret_cast<void*>(argv[0]));
  return nullptr;
}

napi_value NativeRegisterNodeStatusModifyConstructor(napi_env env,
                                                      napi_callback_info info) {
  size_t argc = 1;
  napi_value argv[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
  if (argc < 1) {
    return nullptr;
  }
  if (g_statusCtorRef == nullptr) {
    napi_create_reference(env, argv[0], 1, &g_statusCtorRef);
  }
  BRIDGE_LOG(
      "nativeRegisterNodeStatusModifyConstructor: statusCtor cached ref=%{public}p",
      reinterpret_cast<void*>(g_statusCtorRef));
  auto fn = ResolveUi<NodeCtorFn>("buildInstance");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeRegisterNodeStatusModifyConstructor: symbol missing");
    return nullptr;
  }
  fn(reinterpret_cast<void*>(env), reinterpret_cast<void*>(argv[0]));
  return nullptr;
}

napi_value NativeSetPixelRatio(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value argv[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
  double ratio = 1.0;
  if (argc >= 1) {
    napi_get_value_double(env, argv[0], &ratio);
  }
  g_pixelRatio = ratio;
  BRIDGE_LOG("nativeSetPixelRatio: ratio=%{public}f", ratio);
  auto fn = ResolveUi<SetPixelRatioFn>("setPixelRatio");
  if (fn == nullptr) {
    BRIDGE_LOG("nativeSetPixelRatio: symbol missing");
    return nullptr;
  }
  fn(ratio);
  return nullptr;
}

napi_value StartHttpTest(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string url = ReadString(env, args[0]);
  auto start = ResolveTop<StartHttpTestFn>("dengy_start_http_test");
  BRIDGE_LOG("startHttpTest url=%{public}s symbol=%{public}s", url.c_str(),
             start == nullptr ? "MISSING" : "ok");
  napi_value result = nullptr;
  napi_create_int32(env, start == nullptr ? 0 : start(url.c_str()), &result);
  return result;
}

napi_value HttpTestRunning(napi_env env, napi_callback_info /*info*/) {
  auto running = ResolveTop<HttpTestRunningFn>("dengy_http_test_running");
  napi_value result = nullptr;
  napi_create_int32(env, running == nullptr ? 0 : running(), &result);
  return result;
}

napi_value HttpTestResult(napi_env env, napi_callback_info /*info*/) {
  auto get = ResolveTop<HttpTestResultFn>("dengy_http_test_result");
  const char* text = get == nullptr ? nullptr : get();
  napi_value result = nullptr;
  if (text == nullptr) {
    napi_get_null(env, &result);
  } else {
    napi_create_string_utf8(env, text, NAPI_AUTO_LENGTH, &result);
  }
  return result;
}

// ---- HTTP 桥 ----
// 流转（push 式）：Kotlin send() → dengy_http_submit() → CallJs → g_executor
// (ArkTS http.request) → submitHttpResult() → dengy_http_complete() → Kotlin resume。
napi_threadsafe_function g_httpTsfn = nullptr;
napi_ref g_executor = nullptr;  // (json: string) => void

struct HttpDispatch {
  std::string json;
};

void OnDispatchToJs(napi_env env, napi_value /*js_cb*/, void* /*context*/,
                    void* data) {
  auto* pending = static_cast<HttpDispatch*>(data);
  std::unique_ptr<HttpDispatch> guard(pending);
  if (g_executor == nullptr) {
    BRIDGE_LOG("dispatch dropped: executor not registered");
    return;
  }
  napi_value executor = nullptr;
  napi_get_reference_value(env, g_executor, &executor);
  if (executor == nullptr) {
    BRIDGE_LOG("dispatch dropped: executor ref null");
    return;
  }
  napi_value arg = nullptr;
  napi_create_string_utf8(env, pending->json.c_str(), NAPI_AUTO_LENGTH, &arg);
  napi_value undefined = nullptr;
  napi_get_undefined(env, &undefined);
  napi_status st = napi_call_function(env, undefined, executor, 1, &arg, nullptr);
  BRIDGE_LOG("dispatch to js: status=%{public}d", st);
  if (st != napi_ok) {
    napi_value exc = nullptr;
    napi_get_and_clear_last_exception(env, &exc);
    napi_value msg = nullptr;
    napi_coerce_to_string(env, exc, &msg);
    char buf[256] = {0};
    size_t copied = 0;
    napi_get_value_string_utf8(env, msg, buf, sizeof(buf), &copied);
    BRIDGE_LOG("executor threw: %{public}s", buf);
  }
}

// ArkTS 侧：registerHttpExecutor(cb) —— 存执行器引用并惰性创建 tsfn。
napi_value RegisterHttpExecutor(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  if (argc < 1) {
    return nullptr;
  }
  if (g_executor != nullptr) {
    napi_delete_reference(env, g_executor);
    g_executor = nullptr;
  }
  napi_create_reference(env, args[0], 1, &g_executor);
  if (g_httpTsfn == nullptr) {
    napi_value async_name = nullptr;
    napi_create_string_utf8(env, "dengy_http", NAPI_AUTO_LENGTH, &async_name);
    napi_create_threadsafe_function(env, nullptr, nullptr, async_name, 0, 1,
                                    nullptr, nullptr, nullptr, OnDispatchToJs,
                                    &g_httpTsfn);
  }
  return nullptr;
}

// Kotlin 回调入口在 libdengyui.so 里，以 @CName("dengy_http_complete") 导出；
// 显式 dlopen 句柄取址（DT_NEEDED 依赖不在全局符号作用域）。
// 形参须与 Kotlin 侧逐字对齐：(Long, Int, Int, CPointer<ByteVar>?, Int)。
using HttpCompleteFn = void (*)(int64_t, int32_t, int32_t, char*, int32_t);

HttpCompleteFn ResolveHttpComplete() {
  static HttpCompleteFn cached = [] {
    void* handle = dlopen("libdengyui.so", RTLD_NOW | RTLD_GLOBAL);
    return reinterpret_cast<HttpCompleteFn>(
        handle ? dlsym(handle, "dengy_http_complete") : nullptr);
  }();
  return cached;
}

// ArkTS 执行器完成后回调：submitHttpResult(id, status, binary, b64)
// 失败约定 status=0、b64=""，与 commonMain 契约一致。
napi_value SubmitHttpResult(napi_env env, napi_callback_info info) {
  size_t argc = 4;
  napi_value args[4] = {nullptr, nullptr, nullptr, nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

  double id_num = 0;
  int32_t status = 0;
  int32_t binary = 0;
  napi_get_value_double(env, args[0], &id_num);
  napi_get_value_int32(env, args[1], &status);
  napi_get_value_int32(env, args[2], &binary);
  std::string b64 = ReadString(env, args[3]);

  auto complete = ResolveHttpComplete();
  BRIDGE_LOG("submitHttpResult id=%{public}lld status=%{public}d binary=%{public}d b64len=%{public}zu complete=%{public}s",
             static_cast<long long>(id_num), status, binary, b64.size(),
             complete == nullptr ? "MISSING" : "ok");
  if (complete != nullptr) {
    complete(static_cast<int64_t>(id_num), status, binary, b64.data(),
             static_cast<int32_t>(b64.size()));
  }
  return nullptr;
}

// ---- P6-6 数据门面：dengy_app_init / dengy_home_categories /
// dengy_fetch_category / dengy_search_all / dengy_task_running /
// dengy_task_result（同为 @CName 顶层函数，显式 dlopen 句柄取址）
using AppInitFn = int32_t (*)(const char*);
using HomeCategoriesFn = const char* (*)();
using FetchCategoryFn = int32_t (*)(const char*, int32_t);
using SearchAllFn = int32_t (*)(const char*);
using TaskRunningFn = int32_t (*)();
using TaskResultFn = const char* (*)();
using CancelSearchFn = int32_t (*)();
using RemoveHistoryFn = int32_t (*)(const char*);
using VoidIntFn = int32_t (*)();
using StringFn = const char* (*)();

napi_value AppInit(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string dir = ReadString(env, args[0]);
  auto init = ResolveTop<AppInitFn>("dengy_app_init");
  napi_value result = nullptr;
  napi_create_int32(env, init == nullptr ? 0 : init(dir.c_str()), &result);
  return result;
}

napi_value HomeCategories(napi_env env, napi_callback_info /*info*/) {
  auto get = ResolveTop<HomeCategoriesFn>("dengy_home_categories");
  if (get == nullptr) {
    napi_value result = nullptr;
    napi_create_string_utf8(env, "[]", NAPI_AUTO_LENGTH, &result);
    return result;
  }
  // String 返回：WrapString 建完 JS 串后立即 DisposeString 归还 K/N 堆
  return WrapString(env, get());
}

napi_value FetchCategory(napi_env env, napi_callback_info info) {
  size_t argc = 2;
  napi_value args[2] = {nullptr, nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string category = ReadString(env, args[0]);
  int32_t page = 0;
  napi_get_value_int32(env, args[1], &page);
  auto fetch = ResolveTop<FetchCategoryFn>("dengy_fetch_category");
  napi_value result = nullptr;
  napi_create_int32(env, fetch == nullptr ? 0 : fetch(category.c_str(), page),
                    &result);
  return result;
}

napi_value SearchAll(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string keyword = ReadString(env, args[0]);
  auto search = ResolveTop<SearchAllFn>("dengy_search_all");
  int32_t ret = search == nullptr ? 0 : search(keyword.c_str());
  BRIDGE_LOG("searchAll done ret=%{public}d", ret);
  napi_value result = nullptr;
  napi_create_int32(env, ret, &result);
  return result;
}

napi_value TaskRunning(napi_env env, napi_callback_info /*info*/) {
  auto running = ResolveTop<TaskRunningFn>("dengy_task_running");
  napi_value result = nullptr;
  napi_create_int32(env, running == nullptr ? 0 : running(), &result);
  return result;
}

napi_value TaskResult(napi_env env, napi_callback_info /*info*/) {
  auto get = ResolveTop<TaskResultFn>("dengy_task_result");
  const char* text = get == nullptr ? nullptr : get();
  napi_value result = nullptr;
  if (text == nullptr) {
    napi_get_null(env, &result);
  } else {
    // 指向 Kotlin 常驻 slot 缓冲，拷进 JS 堆即可，不能 DisposeString
    napi_create_string_utf8(env, text, NAPI_AUTO_LENGTH, &result);
  }
  return result;
}

napi_value CancelSearch(napi_env env, napi_callback_info /*info*/) {
  auto cancel = ResolveTop<CancelSearchFn>("dengy_cancel_search");
  napi_value result = nullptr;
  napi_create_int32(env, cancel == nullptr ? 0 : cancel(), &result);
  return result;
}

napi_value StartSuggest(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string keyword = ReadString(env, args[0]);
  auto start = ResolveTop<RemoveHistoryFn>("dengy_start_suggest");
  napi_value result = nullptr;
  napi_create_int32(env, start == nullptr ? 0 : start(keyword.c_str()),
                    &result);
  return result;
}

napi_value SuggestResult(napi_env env, napi_callback_info /*info*/) {
  auto get = ResolveTop<StringFn>("dengy_suggest_result");
  return WrapString(env, get == nullptr ? "[]" : get());
}

napi_value GetHistory(napi_env env, napi_callback_info /*info*/) {
  auto get = ResolveTop<StringFn>("dengy_get_history");
  const char* text = get == nullptr ? nullptr : get();
  napi_value result = nullptr;
  if (text == nullptr) {
    napi_create_string_utf8(env, "[]", NAPI_AUTO_LENGTH, &result);
  } else {
    napi_create_string_utf8(env, text, NAPI_AUTO_LENGTH, &result);
  }
  return result;
}

napi_value RefreshHistory(napi_env env, napi_callback_info /*info*/) {
  auto refresh = ResolveTop<VoidIntFn>("dengy_refresh_history");
  napi_value result = nullptr;
  napi_create_int32(env, refresh == nullptr ? 0 : refresh(), &result);
  return result;
}

napi_value RemoveHistory(napi_env env, napi_callback_info info) {
  size_t argc = 1;
  napi_value args[1] = {nullptr};
  napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
  std::string keyword = ReadString(env, args[0]);
  auto remove = ResolveTop<RemoveHistoryFn>("dengy_remove_history");
  napi_value result = nullptr;
  napi_create_int32(env, remove == nullptr ? 0 : remove(keyword.c_str()),
                    &result);
  return result;
}

napi_value ClearHistory(napi_env env, napi_callback_info /*info*/) {
  auto clear = ResolveTop<VoidIntFn>("dengy_clear_history");
  napi_value result = nullptr;
  napi_create_int32(env, clear == nullptr ? 0 : clear(), &result);
  return result;
}

napi_value Init(napi_env env, napi_value exports) {
  BRIDGE_LOG("module init v8");
  // 必须早于任何 Compose 调用：社区 utils 在此拿 env/exports 建好 napi 引用表。
  androidx_compose_ui_arkui_init(reinterpret_cast<void*>(env),
                                 reinterpret_cast<void*>(exports));
  napi_property_descriptor desc[] = {
      {"registerHttpExecutor", nullptr, RegisterHttpExecutor, nullptr, nullptr,
       nullptr, napi_default, nullptr},
      {"submitHttpResult", nullptr, SubmitHttpResult, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"startHttpTest", nullptr, StartHttpTest, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"httpTestRunning", nullptr, HttpTestRunning, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"httpTestResult", nullptr, HttpTestResult, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"appInit", nullptr, AppInit, nullptr, nullptr, nullptr, napi_default,
       nullptr},
      {"homeCategories", nullptr, HomeCategories, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"fetchCategory", nullptr, FetchCategory, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"searchAll", nullptr, SearchAll, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"taskRunning", nullptr, TaskRunning, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"taskResult", nullptr, TaskResult, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"cancelSearch", nullptr, CancelSearch, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"startSuggest", nullptr, StartSuggest, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"suggestResult", nullptr, SuggestResult, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"getHistory", nullptr, GetHistory, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"refreshHistory", nullptr, RefreshHistory, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"removeHistory", nullptr, RemoveHistory, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"clearHistory", nullptr, ClearHistory, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"MainArkUIViewController", nullptr, MainArkUIViewController, nullptr,
       nullptr, nullptr, napi_default, nullptr},
      // fusion-renderer 的 6 个 NAPI：compose.har 的 Compose 组件 aboutToAppear
      // 具名 import，缺一个就 ES SyntaxError -> onAbilityDied。实现在 libdengyui.so。
      {"nativeCreateRootNode", nullptr, NativeCreateRootNode, nullptr, nullptr,
       nullptr, napi_default, nullptr},
      {"nativeOHRenderNodeDraw", nullptr, NativeRenderNodeDraw, nullptr, nullptr,
       nullptr, napi_default, nullptr},
      {"nativeOHRenderNotifyRedraw", nullptr, NativeRenderNodeNotifyRedraw,
       nullptr, nullptr, nullptr, napi_default, nullptr},
      {"nativeRegisterNodeConstructor", nullptr, NativeRegisterNodeConstructor,
       nullptr, nullptr, nullptr, napi_default, nullptr},
      {"nativeRegisterNodeStatusModifyConstructor", nullptr,
       NativeRegisterNodeStatusModifyConstructor, nullptr, nullptr, nullptr,
       napi_default, nullptr},
      {"nativeSetPixelRatio", nullptr, NativeSetPixelRatio, nullptr, nullptr,
       nullptr, napi_default, nullptr},
  };
  napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
  return exports;
}

// 模块名必须与产物文件同名（libentry.so）：compose.har 的 Compose 组件按这个名字取模块。
napi_module g_module = {1, 0, nullptr, Init, "entry", nullptr, {0}};

}  // namespace

// Kotlin dlsym 取址用：必须在匿名 namespace 之外（外部链接）。
extern "C" {

// 由 libdengyui.so 里的 HttpTransport 调用：投递 JSON 请求到 JS 线程。
// 0=已投递；-1=执行器未注册/投递失败（Kotlin 侧当场折叠 status=0）。
__attribute__((visibility("default"))) int dengy_http_submit(const char* json) {
  if (json == nullptr || g_httpTsfn == nullptr) {
    BRIDGE_LOG("submit refused: tsfn=%{public}s",
               g_httpTsfn == nullptr ? "null" : "ok");
    return -1;
  }
  auto* pending = new HttpDispatch{std::string(json)};
  if (napi_call_threadsafe_function(g_httpTsfn, pending,
                                    napi_tsfn_blocking) != napi_ok) {
    delete pending;
    BRIDGE_LOG("submit: tsfn call failed");
    return -1;
  }
  BRIDGE_LOG("submit: queued json len=%{public}zu", strlen(json));
  return 0;
}

}  // extern "C"

extern "C" __attribute__((constructor)) void RegisterDengYingModule() {
  napi_module_register(&g_module);
}
