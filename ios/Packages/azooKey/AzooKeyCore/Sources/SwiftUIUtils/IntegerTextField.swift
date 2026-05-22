//
//  IntegerTextField.swift
//  azooKey
//
//  Created by ensan on 2023/03/24.
//  Copyright © 2023 ensan. All rights reserved.
//

import SwiftUI

public struct IntegerTextField: View {
    public init(_ titleKey: LocalizedStringKey, text: Binding<String>, range: ClosedRange<Int> = .min ... .max) {
        self.titleKey = titleKey
        self._text = text
        self.range = range
    }

    private let titleKey: LocalizedStringKey
    private let range: ClosedRange<Int>
    @Binding private var text: String

    public var body: some View {
        HStack {
            TextField(titleKey, text: $text)
                .onChange(of: text) { (_, newValue) in
                    if let value = Int(newValue) {
                        if range.upperBound < value {
                            text = "\(range.upperBound)"
                        } else if value < range.lowerBound {
                            text = "\(range.lowerBound)"
                        }
                    }
                }
            Stepper {
                EmptyView()
            } onIncrement: {
                if let value = Int(text) {
                    if range.upperBound > value {
                        text = "\(value + 1)"
                    }
                }
            } onDecrement: {
                if let value = Int(text) {
                    if range.lowerBound < value {
                        text = "\(value - 1)"
                    }
                }
            }
        }
    }
}
