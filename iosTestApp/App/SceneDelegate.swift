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
        // Product navigation graph. Opt-in while it is built out screen by
        // screen; the harness keeps its own launch modes untouched.
        if CommandLine.arguments.contains("-CellWatchProductShell") {
            let shell = ProductShell()
            window.rootViewController = shell.rootViewController
            window.makeKeyAndVisible()
            self.productShell = shell
            self.window = window
            return
        }

        // Design-system review surface, outside the product navigation graph.
        if CommandLine.arguments.contains("-CellWatchComponentGallery") {
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
