import SwiftUI
import UIKit
import PhotosUI
import Security
import ComposeApp

// MARK: - Keychain：账号凭据

/// Keychain 实现 `CredentialVault`。
///
/// 为什么放在 Swift 而不是 Kotlin/Native：Keychain 的 API 全是 CFDictionary，
/// Kotlin 侧手搓 `CFDictionaryCreateMutable` 又长又容易错，Swift 里十几行就够了。
///
/// ⚠️ 类型名带 `Shared` 前缀：Kotlin/Native 导出 `shared` 模块的声明时会加模块前缀
/// （避免不同模块重名），所以 Swift 里看到的是 `SharedCredentialVault`。
///
/// ⚠️ Keychain 条目**不会随 App 卸载删除**，所以「清除账号」必须显式调用 clear()。
final class KeychainVault: NSObject, SharedCredentialVault {
    private let service = "com.jianke.schedule.ios"

    private func query(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }

    func read(key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func save(key: String, value: String) {
        SecItemDelete(query(key) as CFDictionary)

        var q = query(key)
        q[kSecValueData as String] = Data(value.utf8)
        // 解锁过一次之后就能读（后台续期也能用），且不随 iCloud 同步到别的设备
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }

    func remove(key: String) {
        SecItemDelete(query(key) as CFDictionary)
    }

    func clear() {
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ] as CFDictionary)
    }
}

// MARK: - 背景壁纸

/// `WallpaperHost` 的 UIKit 实现。
///
/// 照片不经过 Kotlin：选图、存盘、显示都在 Swift 侧完成，Compose 只负责把背景让出来。
/// 约定文件名为 `wallpaper.jpg`，「有没有壁纸」就等于「文件在不在」。
final class WallpaperHostImpl: NSObject, SharedWallpaperHost {
    static let fileName = "wallpaper.jpg"

    weak var presenter: UIViewController?
    var imageView: UIImageView?

    private var onDone: ((String?) -> Void)?

    private var filePath: String {
        let dir = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
        return dir + "/" + Self.fileName
    }

    func isActive() -> Bool {
        FileManager.default.fileExists(atPath: filePath)
    }

    func clear() {
        try? FileManager.default.removeItem(atPath: filePath)
        imageView?.image = nil
    }

    func pick(onDone: @escaping (String?) -> Void) {
        guard let presenter else {
            onDone(nil)
            return
        }
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1

        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        self.onDone = onDone
        presenter.present(picker, animated: true)
    }
}

extension WallpaperHostImpl: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)

        guard let provider = results.first?.itemProvider,
              provider.canLoadObject(ofClass: UIImage.self) else {
            finish(nil)
            return
        }

        provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
            guard let self else { return }
            guard let image = object as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.92) else {
                DispatchQueue.main.async { self.finish(nil) }
                return
            }
            DispatchQueue.main.async {
                do {
                    try data.write(to: URL(fileURLWithPath: self.filePath))
                    self.imageView?.image = image
                    self.finish(Self.fileName)
                } catch {
                    self.finish(nil)
                }
            }
        }
    }

    private func finish(_ name: String?) {
        onDone?(name)
        onDone = nil
    }
}

// MARK: - 容器：UIImageView 在底、Compose 在上

/// 根容器把两张图层叠起来：
/// 最底下是壁纸（UIKit），上面是透明的 Compose 界面。
final class RootViewController: UIViewController {
    private let wallpaper = WallpaperHostImpl()
    private let imageView = UIImageView()

    private lazy var composeVC: UIViewController = MainViewControllerKt.MainViewController(
        vault: KeychainVault(),
        wallpaper: wallpaper
    )

    override func viewDidLoad() {
        super.viewDidLoad()

        imageView.frame = view.bounds
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(imageView)

        wallpaper.imageView = imageView
        wallpaper.presenter = self

        if wallpaper.isActive() {
            let dir = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
            imageView.image = UIImage(contentsOfFile: dir + "/" + WallpaperHostImpl.fileName)
        }

        addChild(composeVC)
        composeVC.view.frame = view.bounds
        composeVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        // 关键：Compose 层必须透明，否则壁纸永远看不见
        composeVC.view.backgroundColor = .clear
        view.addSubview(composeVC.view)
        composeVC.didMove(toParent: self)
    }
}

// MARK: - SwiftUI 入口

@main
struct GuangXiaoKeApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .ignoresSafeArea(.keyboard)
        }
    }
}

struct RootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        RootViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
