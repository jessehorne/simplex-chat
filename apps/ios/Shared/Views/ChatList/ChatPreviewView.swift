//
//  ChatNavLabel.swift
//  SimpleX
//
//  Created by Evgeny Poberezkin on 28/01/2022.
//  Copyright © 2022 SimpleX Chat. All rights reserved.
//

import SwiftUI
import SimpleXChat

struct ChatPreviewView: View {
    @EnvironmentObject var chatModel: ChatModel
    @ObservedObject var chat: Chat
    @Binding var progressByTimeout: Bool
    @Environment(\.colorScheme) var colorScheme
    var darkGreen = Color(red: 0, green: 0.5, blue: 0)

    @AppStorage(DEFAULT_PRIVACY_SHOW_CHAT_PREVIEWS) private var showChatPreviews = true

    var body: some View {
        let cItem = chat.chatItems.last
        return HStack(spacing: 8) {
            ZStack(alignment: .bottomTrailing) {
                ChatInfoImage(chat: chat)
                    .frame(width: 63, height: 63)
                chatPreviewImageOverlayIcon()
                    .padding([.bottom, .trailing], 1)
            }
            .padding(.leading, 4)

            VStack(spacing: 0) {
                HStack(alignment: .top) {
                    chatPreviewTitle()
                    Spacer()
                    (cItem?.timestampText ?? formatTimestampText(chat.chatInfo.updatedAt))
                        .font(.subheadline)
                        .frame(minWidth: 60, alignment: .trailing)
                        .foregroundColor(.secondary)
                        .padding(.top, 4)
                }
                .padding(.bottom, 4)
                .padding(.horizontal, 8)

                ZStack(alignment: .topTrailing) {
                    chatMessagePreview(cItem)
                    chatStatusImage()
                        .padding(.top, 26)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(.trailing, 8)
                
                Spacer()
            }
            .frame(maxHeight: .infinity)
        }
        .padding(.bottom, -8)
    }

    @ViewBuilder private func chatPreviewImageOverlayIcon() -> some View {
        switch chat.chatInfo {
        case let .direct(contact):
            if !contact.active {
                inactiveIcon()
            } else {
                EmptyView()
            }
        case let .group(groupInfo):
            switch (groupInfo.membership.memberStatus) {
            case .memLeft: inactiveIcon()
            case .memRemoved: inactiveIcon()
            case .memGroupDeleted: inactiveIcon()
            default: EmptyView()
            }
        default:
            EmptyView()
        }
    }

    @ViewBuilder private func inactiveIcon() -> some View {
        Image(systemName: "multiply.circle.fill")
            .foregroundColor(.secondary.opacity(0.65))
            .background(Circle().foregroundColor(Color(uiColor: .systemBackground)))
    }

    @ViewBuilder private func chatPreviewTitle() -> some View {
        let t = Text(chat.chatInfo.chatViewName).font(.title3).fontWeight(.bold)
        switch chat.chatInfo {
        case let .direct(contact):
            previewTitle(contact.verified == true ? verifiedIcon + t : t)
        case let .group(groupInfo):
            let v = previewTitle(t)
            switch (groupInfo.membership.memberStatus) {
            case .memInvited: v.foregroundColor(chat.chatInfo.incognito ? .indigo : .accentColor)
            case .memAccepted: v.foregroundColor(.secondary)
            default: v
            }
        default: previewTitle(t)
        }
    }

    private func previewTitle(_ t: Text) -> some View {
        t.lineLimit(1).frame(alignment: .topLeading)
    }

    private var verifiedIcon: Text {
        (Text(Image(systemName: "checkmark.shield")) + Text(" "))
            .foregroundColor(.secondary)
            .baselineOffset(1)
            .kerning(-2)
    }

    private func chatPreviewLayout(_ text: Text, draft: Bool = false) -> some View {
        ZStack(alignment: .topTrailing) {
            let t = text
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .padding(.leading, 8)
                .padding(.trailing, 36)
            if !showChatPreviews && !draft {
                t.privacySensitive(true).redacted(reason: .privacy)
            } else {
                t
            }
            let s = chat.chatStats
            if s.unreadCount > 0 || s.unreadChat {
                unreadCountText(s.unreadCount)
                    .font(.caption)
                    .foregroundColor(.white)
                    .padding(.horizontal, 4)
                    .frame(minWidth: 18, minHeight: 18)
                    .background(chat.chatInfo.ntfsEnabled ? Color.accentColor : Color.secondary)
                    .cornerRadius(10)
            } else if !chat.chatInfo.ntfsEnabled {
                Image(systemName: "speaker.slash.fill")
                    .foregroundColor(.secondary)
            } else if chat.chatInfo.chatSettings?.favorite ?? false {
                Image(systemName: "star.fill")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 18, height: 18)
                    .padding(.trailing, 1)
                    .foregroundColor(.secondary.opacity(0.65))
            }
        }
    }

    private func messageDraft(_ draft: ComposeState) -> Text {
        let msg = draft.message
        return image("rectangle.and.pencil.and.ellipsis", color: .accentColor)
                + attachment()
                + messageText(msg, parseSimpleXMarkdown(msg), nil, preview: true)

        func image(_ s: String, color: Color = Color(uiColor: .tertiaryLabel)) -> Text {
            Text(Image(systemName: s)).foregroundColor(color) + Text(" ")
        }

        func attachment() -> Text {
            switch draft.preview {
            case let .filePreview(fileName, _): return image("doc.fill") + Text(fileName) + Text(" ")
            case .mediaPreviews: return image("photo")
            case let .voicePreview(_, duration): return image("play.fill") + Text(durationText(duration))
            default: return Text("")
            }
        }
    }

    func chatItemPreview(_ cItem: ChatItem) -> Text {
        let itemText = cItem.meta.itemDeleted == nil ? cItem.text : NSLocalizedString("marked deleted", comment: "marked deleted chat item preview text")
        let itemFormattedText = cItem.meta.itemDeleted == nil ? cItem.formattedText : nil
        return messageText(itemText, itemFormattedText, cItem.memberDisplayName, icon: attachment(), preview: true)

        func attachment() -> String? {
            switch cItem.content.msgContent {
            case .file: return "doc.fill"
            case .image: return "photo"
            case .video: return "video"
            case .voice: return "play.fill"
            default: return nil
            }
        }
    }

    @ViewBuilder private func chatMessagePreview(_ cItem: ChatItem?) -> some View {
        if chatModel.draftChatId == chat.id, let draft = chatModel.draft {
            chatPreviewLayout(messageDraft(draft), draft: true)
        } else if let cItem = cItem {
            chatPreviewLayout(itemStatusMark(cItem) + chatItemPreview(cItem))
        } else {
            switch (chat.chatInfo) {
            case let .direct(contact):
                if !contact.ready {
                    if contact.nextSendGrpInv {
                        chatPreviewInfoText("send direct message")
                    } else if contact.active {
                        chatPreviewInfoText("connecting…")
                    }
                }
            case let .group(groupInfo):
                switch (groupInfo.membership.memberStatus) {
                case .memInvited: groupInvitationPreviewText(groupInfo)
                case .memAccepted: chatPreviewInfoText("connecting…")
                default: EmptyView()
                }
            default: EmptyView()
            }
        }
    }

    @ViewBuilder private func groupInvitationPreviewText(_ groupInfo: GroupInfo) -> some View {
        groupInfo.membership.memberIncognito
        ? chatPreviewInfoText("join as \(groupInfo.membership.memberProfile.displayName)")
        : chatPreviewInfoText("you are invited to group")
    }

    @ViewBuilder private func chatPreviewInfoText(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44, alignment: .topLeading)
            .padding([.leading, .trailing], 8)
            .padding(.bottom, 4)
    }

    private func itemStatusMark(_ cItem: ChatItem) -> Text {
        switch cItem.meta.itemStatus {
        case .sndErrorAuth:
            return Text(Image(systemName: "multiply"))
                .font(.caption)
                .foregroundColor(.red) + Text(" ")
        case .sndError:
            return Text(Image(systemName: "exclamationmark.triangle.fill"))
                .font(.caption)
                .foregroundColor(.yellow) + Text(" ")
        default: return Text("")
        }
    }

    @ViewBuilder private func chatStatusImage() -> some View {
        switch chat.chatInfo {
        case let .direct(contact):
            if contact.active {
                switch (chatModel.contactNetworkStatus(contact)) {
                case .connected: incognitoIcon(chat.chatInfo.incognito)
                case .error:
                    Image(systemName: "exclamationmark.circle")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 17, height: 17)
                        .foregroundColor(.secondary)
                default:
                    ProgressView()
                }
            } else {
                incognitoIcon(chat.chatInfo.incognito)
            }
        case .group:
            if progressByTimeout {
                ProgressView()
            } else {
                incognitoIcon(chat.chatInfo.incognito)
            }
        default:
            incognitoIcon(chat.chatInfo.incognito)
        }
    }
}

@ViewBuilder func incognitoIcon(_ incognito: Bool) -> some View {
    if incognito {
        Image(systemName: "theatermasks")
            .resizable()
            .scaledToFit()
            .frame(width: 22, height: 22)
            .foregroundColor(.secondary)
    } else {
        EmptyView()
    }
}

func unreadCountText(_ n: Int) -> Text {
    Text(n > 999 ? "\(n / 1000)k" : n > 0 ? "\(n)" : "")
}

struct ChatPreviewView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.direct,
                chatItems: []
            ), progressByTimeout: Binding.constant(false))
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.direct,
                chatItems: [ChatItem.getSample(1, .directSnd, .now, "hello", .sndSent(sndProgress: .complete))]
            ), progressByTimeout: Binding.constant(false))
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.direct,
                chatItems: [ChatItem.getSample(1, .directSnd, .now, "hello", .sndSent(sndProgress: .complete))],
                chatStats: ChatStats(unreadCount: 11, minUnreadItemId: 0)
            ), progressByTimeout: Binding.constant(false))
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.direct,
                chatItems: [ChatItem.getSample(1, .directSnd, .now, "hello", .sndSent(sndProgress: .complete), itemDeleted: .deleted(deletedTs: .now))]
            ), progressByTimeout: Binding.constant(false))
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.direct,
                chatItems: [ChatItem.getSample(1, .directSnd, .now, "hello", .sndSent(sndProgress: .complete))],
                chatStats: ChatStats(unreadCount: 3, minUnreadItemId: 0)
            ), progressByTimeout: Binding.constant(false))
            ChatPreviewView(chat: Chat(
                chatInfo: ChatInfo.sampleData.group,
                chatItems: [ChatItem.getSample(1, .directSnd, .now, "Lorem ipsum dolor sit amet, d. consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.")],
                chatStats: ChatStats(unreadCount: 11, minUnreadItemId: 0)
            ), progressByTimeout: Binding.constant(false))
        }
        .previewLayout(.fixed(width: 360, height: 78))
    }
}
