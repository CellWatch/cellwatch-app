import UIKit

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    /// Retained for the lifetime of the scene; the shell owns the navigator.
    private var productShell: ProductShell?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else {
            return
        }
        let window = UIWindow(windowScene: windowScene)
        window.frame = windowScene.coordinateSpace.bounds
        window.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.97, alpha: 1.0)
        // Product navigation graph - now the default.
        //
        // It used to be opt-in behind this launch argument while it was built
        // out screen by screen, which was fine from Xcode and useless on a
        // phone: tapping the icon passes no arguments, so a device install
        // always landed in the harness. The harness is still reachable with
        // `-CellWatchHarness`, which is the right way round now that the
        // product graph is the thing being reviewed.
        let wantsHarness = CommandLine.arguments.contains("-CellWatchHarness")
        let wantsGallery = CommandLine.arguments.contains("-CellWatchComponentGallery")
        if !wantsHarness && !wantsGallery {
            let shell = ProductShell()
            window.rootViewController = shell.rootViewController
            window.makeKeyAndVisible()
            self.productShell = shell
            self.window = window
            return
        }

        // Design-system review surface, outside the product navigation graph.
        if wantsGallery {
            window.rootViewController = UINavigationController(
                rootViewController: ComponentGalleryViewController()
            )
            window.makeKeyAndVisible()
            self.window = window
            return
        }

        let mode = AppDelegate.resolveDisplayMode()
        let onboardingUiImpl: HarnessViewController.OnboardingUiImplementation =
            AppDelegate.shouldDefaultToMvpNavigation() ? .uikit : .swiftui
        if mode == .mvpMenu || mode == .mapHome {
            window.rootViewController = UINavigationController(
                rootViewController: HarnessViewController(
                    displayMode: mode,
                    onboardingUiImplementation: onboardingUiImpl
                )
            )
        } else {
            window.rootViewController = HarnessViewController(
                displayMode: mode,
                onboardingUiImplementation: onboardingUiImpl
            )
        }
        window.makeKeyAndVisible()
        self.window = window
    }
}
