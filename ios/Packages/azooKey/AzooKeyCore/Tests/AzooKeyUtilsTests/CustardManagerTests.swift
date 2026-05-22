@testable import AzooKeyUtils
import CustardKit
import KeyboardViews
import XCTest

final class CustardManagerTests: XCTestCase {
    func testSaveCustardCreatesTabBarWhenAbsent() throws {
        var manager = CustardManager.load()
        manager.removeTabBar(identifier: 0)
        let custard = Custard.errorMessage
        try manager.saveCustard(custard: custard, metadata: .init(origin: .userMade), updateTabBar: true)
        let fileManager = FileManager.default
        let container = fileManager.containerURL(forSecurityApplicationGroupIdentifier: SharedStore.appGroupKey)!
        let fileURL = container.appendingPathComponent("custard/tabbar_0.tabbar")
        XCTAssertTrue(fileManager.fileExists(atPath: fileURL.path))
        let data = try Data(contentsOf: fileURL)
        let tabbar = try JSONDecoder().decode(TabBarData.self, from: data)
        XCTAssertTrue(tabbar.items.contains { $0.actions == [.moveTab(.custom(custard.identifier))] })
    }
}
