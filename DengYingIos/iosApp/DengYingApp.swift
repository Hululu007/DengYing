//
//  DengYingApp.swift
//  DengYing
//
//  iOS 壳入口。职责只有三件：
//   1. 托管 :ui 的 ComposeUIViewController（见 ui/src/iosMain/.../MainViewController.kt）
//   2. 把「允许的方向」这一层从 Compose 接回 UIKit
//      （supportedInterfaceOrientations 只能由正在显示的 VC 回答）
//   3. 把状态栏显隐交给同一个 VC
//
//  未验证（需 macOS + Xcode）：import DengYing 依赖 framework 产物存在。
//

import SwiftUI
import UIKit
import DengYing

// MARK: - 方向与状态栏宿主

/// 把 Compose 侧的方向请求翻译成 UIKit 的 supportedInterfaceOrientations。
///
/// Kotlin 侧 `IosOrientationLock.request(...)` 会更新 `mask`，再调
/// `UIWindowScene.requestGeometryUpdateWithPreferences` 发起转屏动效。
/// UIKit 在决定「这次转屏允不允许」时会来问当前 VC 的 supportedInterfaceOrientations，
/// 这里就是回答的地方。两条路缺一不可：Kotlin 侧负责发起，mask 负责允许。
final class OrientationHostingController: UIViewController {

    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is unused: this controller is built in code")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    /// 读 Kotlin 侧的掩码。
    ///
    /// 是「查询」而不是「推送」：推送需要把 Swift 闭包存进 Kotlin object，
    /// 线程与生命周期都不好保证；而 UIKit 只在需要时问一次，读属性正好。
    ///
    /// `IosOrientationLock.mask` 在 Kotlin 侧是 `platform.UIKit.UIInterfaceOrientationMask`
    /// （即 `NSUInteger`），导出到 Swift 变成 `UInt`，故需显式包成 OptionSet。
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(DengYing.IosOrientationLock.shared.mask))
    }

    /// 全屏态隐藏状态栏。当前恒 false：全屏由 Compose 侧控制层承担，
    /// 状态栏在竖屏列表页必须可见，横屏播放页保留状态栏也不影响沉浸感体验
    /// （真要隐藏时把它接到 IosOrientationLock 的横屏态即可）。
    override var prefersStatusBarHidden: Bool { false }

    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }
}

// MARK: - Compose 桥

/// 把 ComposeUIViewController 装进 SwiftUI。
///
/// 必须用 UIViewControllerRepresentable：`MainViewControllerKt.MainViewController()`
/// 返回的是 `UIViewController`，不是 SwiftUI 的 `View`，不能直接 `.ignoresSafeArea()`。
/// （旧壳写的 `MainViewControllerKt.MainViewController().ignoresSafeArea(.all)`
/// 连类型都不成立 —— 这是 M-6 之外的第二个 Swift 侧缺陷。）
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // 先建 Compose 内容再包外层：OrientationHostingController 首次布局时
        // 会读 IosOrientationLock，而它由 MainViewController 里的 ensureStarted() 装配。
        let compose = MainViewControllerKt.MainViewController()
        return OrientationHostingController(content: compose)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct DengYingApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
