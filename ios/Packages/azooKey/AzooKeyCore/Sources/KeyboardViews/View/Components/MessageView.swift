//
//  MessageView.swift
//  Keyboard
//
//  Created by ensan on 2021/01/29.
//  Copyright © 2021 ensan. All rights reserved.
//

import SwiftUI

@MainActor
struct MessageView<ID: MessageIdentifierProtocol>: View {
    private let data: MessageData<ID>
    @Binding private var manager: MessageManager<ID>
    @EnvironmentObject private var variableStates: VariableStates
    @Environment(\.userActionManager) private var action

    init(data: MessageData<ID>, manager: Binding<MessageManager<ID>>) {
        self.data = data
        self._manager = manager
    }

    @ViewBuilder
    private func secondaryButton(_ style: MessageData<ID>.MessageSecondaryButtonStyle) -> some View {
        switch style {
        case let .details(urlString):
            HStack {
                Spacer()
                Button("詳細") {
                    self.action.registerAction(.openApp(urlString), variableStates: variableStates)
                }
                Spacer()
                Divider()
            }
        case .later:
            HStack {
                Spacer()
                Button("後で") {
                    self.manager.done(data.id)
                }
                Spacer()
                Divider()
            }
        case .OK:
            HStack {
                Spacer()
                Button("了解") {
                    self.manager.done(data.id)
                }
                Spacer()
                Divider()
            }
        }
    }

    @ViewBuilder
    private func primaryButton(_ style: MessageData<ID>.MessagePrimaryButtonStyle) -> some View {
        switch style {
        case let .openContainerURL(text, url, autoDone):
            HStack {
                Spacer()
                Button {
                    self.action.registerAction(.openApp(url), variableStates: variableStates)
                    if autoDone {
                        self.manager.done(data.id)
                    }
                } label: {
                    Text(text).bold()
                }
                Spacer()
            }
        case .OK:
            HStack {
                Spacer()
                Button {
                    self.manager.done(data.id)
                } label: {
                    Text("了解").bold()
                }
                Spacer()
            }
        }
    }

    var body: some View {
        ZStack {
            GeometryReader { reader in
                Color.black.opacity(0.5)
                RoundedRectangle(cornerRadius: 10)
                    .fill(.white)
                    .frame(width: reader.size.width * 0.8, height: reader.size.height * 0.8)
                    .overlay(alignment: .center) {
                        VStack(spacing: 4) {
                            Text(data.title)
                                .font(.title.bold())
                                .padding(.top)
                                .foregroundStyle(.black)
                            ScrollView {
                                Text(data.description)
                                    .padding(.horizontal)
                                    .foregroundStyle(.black)
                                    .lineLimit(nil)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            VStack(spacing: 0) {
                                Divider()
                                switch data.button {
                                case .one(let style):
                                    primaryButton(style)
                                case .two(primary: let primaryStyle, secondary: let secondaryStyle):
                                    HStack {
                                        secondaryButton(secondaryStyle)
                                        primaryButton(primaryStyle)
                                    }
                                }
                            }
                            .frame(maxHeight: reader.size.height * 0.2)
                        }
                    }
                    .offset(x: reader.size.width * 0.1, y: reader.size.height * 0.1)
            }
        }
    }

}
