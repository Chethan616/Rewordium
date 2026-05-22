// swift-tools-version: 6.2
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let swiftSettings: [SwiftSetting] = [
    .enableUpcomingFeature("ExistentialAny"),
    .interoperabilityMode(.Cxx),
]

let package = Package(
    name: "AzooKeyCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        // Products define the executables and libraries a package produces, and make them visible to other packages.
        .library(
            name: "SwiftUIUtils",
            targets: ["SwiftUIUtils"]
        ),
        .library(
            name: "KeyboardThemes",
            targets: ["KeyboardThemes"]
        ),
        .library(
            name: "KeyboardViews",
            targets: ["KeyboardViews"]
        ),
        .library(
            name: "AzooKeyUtils",
            targets: ["AzooKeyUtils"]
        ),
        .library(
            name: "KeyboardExtensionUtils",
            targets: ["KeyboardExtensionUtils"]
        ),
    ],
    dependencies: [
        // Dependencies declare other packages that this package depends on.
        // MARK: You must specify version which results reproductive and stable result
        // MARK: `_: .upToNextMinor(Version)` or `exact: Version` or `revision: Version`.
        // MARK: For develop branch, you can use `revision:` specification.
        // MARK: For main branch, you must use `upToNextMinor` specification.
        //
        // REWORDIUM-LOCAL CHANGE: `ZenzaiCPU` trait removed. Zenzai is AzooKey's
        // neural Japanese converter; it depends on a llama.cpp binary XCFramework
        // that fails to resolve on Codemagic CI (the artifact zip's internal
        // name doesn't match the binary target name in the upstream pinned
        // revision). Without the trait, AzooKey falls back to the statistical
        // marisa-trie converter — still fully functional Japanese input, just
        // less context-aware suggestions. Restore the trait when (a) we upgrade
        // to a fixed AzooKeyKanaKanjiConverter revision OR (b) we host the
        // llama.cpp xcframework ourselves. See AZOOKEY_IOS_INTEGRATION.md §6.
        .package(url: "https://github.com/azooKey/AzooKeyKanaKanjiConverter", revision: "1def030b6697fb3811f2ae642719811db6b70c3e", traits: []),
        .package(url: "https://github.com/azooKey/CustardKit", revision: "7bddc14eb3f8f0145c6f3a4fea20cf394f8104e8"),
    ],
    targets: [
        .target(
            name: "SwiftUIUtils",
            dependencies: [
                .product(name: "SwiftUtils", package: "AzooKeyKanaKanjiConverter")
            ],
            resources: [],
            swiftSettings: swiftSettings
        ),
        .target(
            name: "KeyboardThemes",
            dependencies: [
                "SwiftUIUtils",
                .product(name: "SwiftUtils", package: "AzooKeyKanaKanjiConverter"),
            ],
            resources: [],
            swiftSettings: swiftSettings
        ),
        .target(
            name: "KeyboardViews",
            dependencies: [
                "SwiftUIUtils",
                "KeyboardThemes",
                "KeyboardExtensionUtils",
                .product(name: "KanaKanjiConverterModule", package: "AzooKeyKanaKanjiConverter"),
                .product(name: "CustardKit", package: "CustardKit"),
            ],
            resources: [],
            swiftSettings: swiftSettings
        ),
        .target(
            name: "AzooKeyUtils",
            dependencies: [
                "KeyboardThemes",
                "KeyboardViews",
            ],
            resources: [],
            swiftSettings: swiftSettings
        ),
        .target(
            name: "KeyboardExtensionUtils",
            dependencies: [
                .product(name: "CustardKit", package: "CustardKit"),
                .product(name: "KanaKanjiConverterModule", package: "AzooKeyKanaKanjiConverter"),
            ],
            resources: [],
            swiftSettings: swiftSettings
        ),
        .testTarget(
            name: "KeyboardExtensionUtilsTests",
            dependencies: [
                "KeyboardExtensionUtils",
            ],
            swiftSettings: swiftSettings
        ),
        .testTarget(
            name: "AzooKeyUtilsTests",
            dependencies: [
                "AzooKeyUtils",
            ],
            swiftSettings: swiftSettings
        ),
    ]
)
