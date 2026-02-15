import UIKit

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

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
        let mode = AppDelegate.resolveDisplayMode()
        let onboardingUiImpl: HarnessViewController.OnboardingUiImplementation =
            AppDelegate.shouldDefaultToMvpNavigation() ? .uikit : .swiftui
        if mode == .mvpMenu {
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
