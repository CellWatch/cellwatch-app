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
        window.rootViewController = HarnessViewController(displayMode: AppDelegate.resolveDisplayMode())
        window.makeKeyAndVisible()
        self.window = window
    }
}
