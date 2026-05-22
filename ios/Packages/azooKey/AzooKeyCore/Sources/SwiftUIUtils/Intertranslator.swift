//
//  Intertranslator.swift
//  azooKey
//
//  Created by ensan on 2021/04/28.
//  Copyright © 2021 ensan. All rights reserved.
//

import Foundation
public protocol Intertranslator<First, Second>: Sendable {
    associatedtype First
    associatedtype Second

    func convert(_ first: First) -> Second
    func convert(_ second: Second) -> First
}

public struct IntStringConversion: Intertranslator {
    public typealias First = Int
    public typealias Second = String

    public var defaultValue: Int = 1

    public func convert(_ first: Int) -> String {
        String(first)
    }
    public func convert(_ second: String) -> Int {
        Int(second) ?? self.defaultValue
    }
}

public extension Intertranslator where Self == IntStringConversion {
    static func intStringConversion(defaultValue: Int = 1) -> Self {
        IntStringConversion(defaultValue: defaultValue)
    }
}

private struct ReversedIntertranslator<I: Intertranslator>: Intertranslator {
    typealias First = I.Second
    typealias Second = I.First

    private let intertranslator: I

    init(_ intertranslator: I) {
        self.intertranslator = intertranslator
    }

    func convert(_ first: First) -> Second {
        intertranslator.convert(first)
    }

    func convert(_ second: Second) -> First {
        intertranslator.convert(second)
    }
}

public extension Intertranslator {
    func reversed() -> some Intertranslator<Self.Second, Self.First> {
        ReversedIntertranslator(self)
    }
}
