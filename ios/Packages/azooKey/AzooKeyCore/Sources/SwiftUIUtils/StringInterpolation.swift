//
//  StringInterpolation.swift
//  azooKey
//
//  Created by ensan on 2020/12/25.
//  Copyright © 2020 ensan. All rights reserved.
//

import Foundation
import SwiftUI

public extension LocalizedStringKey.StringInterpolation {
    mutating func appendInterpolation(_ value: LocalizedStringKey) {
        self.appendInterpolation(Text(value))
    }

    mutating func appendInterpolation(_ value: LocalizedStringKey, color: Color) {
        self.appendInterpolation(Text(value).foregroundStyle(color))
    }

    mutating func appendInterpolation(monospaced value: LocalizedStringKey) {
        self.appendInterpolation(Text(value).font(.system(.body, design: .monospaced)))
    }

    mutating func appendInterpolation(systemImage name: String) {
        self.appendInterpolation(Text("\(Image(systemName: name))"))
    }

    mutating func appendInterpolation(systemImage name: String, color: Color) {
        self.appendInterpolation(Text("\(Image(systemName: name))").foregroundStyle(color))
    }
}
