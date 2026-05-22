# Emoji and Kaomoji Policy

## 絵文字と顔文字の分離
azooKey では絵文字と顔文字を分離しています。「絵文字をもっぱら用いる人」「顔文字をもっぱら用いる人」「区別なく用いる人」がいるためです。

この分離を実現するため、絵文字と顔文字は **本体辞書にはバンドルせず**、ユーザ辞書と共にビルドされ、キーボード上で用いられています。

## システム辞書設定
Version 2.4.0 から、絵文字／顔文字辞書の有効・無効は **`AdditionalSystemDictionarySetting`** に統合され、メインアプリとキーボード拡張が共有する **App Group のユーザデフォルト** に保存されます。
これにより、キーボード拡張側も設定を直接参照でき、予測変換の候補制御に利用します。

```swift
public struct AdditionalSystemDictionarySetting: Codable {
    enum SystemDictionaryType: String, CaseIterable { case emoji, kaomoji }
    struct SystemDictionaryConfig: Codable {
        var enabled: Bool                 // デフォルト: emoji=true, kaomoji=false
        var denylist: Set<String> = []    // 候補から除外したい surface を列挙
    }
    var systemDictionarySettings: [SystemDictionaryType: SystemDictionaryConfig]
}
```

設定変更は **メインアプリ → 設定 → 絵文字と顔文字** で行います。変更は即時キーボードに反映されます。

## 絵文字変換

###  通常の変換

通常の変換候補はユーザ辞書に統合するかたちで絵文字辞書を参照します。

### 予測変換

予測変換では絵文字候補がミックスされます。設定の`denylist`が参照されるため、拒否された絵文字は表示されません。

### データ
絵文字辞書ファイルはバージョン毎の **プリビルドテキスト (`emoji_dict_E16.0.txt` など)** としてサブモジュール **`azooKey_emoji_dictionary_storage`** に格納されます。 絵文字データを有する`TextReplacer`をAzooKeyKanaKanjiConverterに渡すことで処理を実行します。

### 更新
* 新しい iOS で絵文字が追加された場合、本体アプリ起動時に **MessageView** で更新ダイアログ（例: `iOS18_4_new_emoji`）を表示します。
* 「更新」を押すと `AdditionalDictManager().userDictUpdate()` が走り、最新辞書を共有領域に配置します。

iOS / iPadOS のアップデート直後に新しい絵文字を使うには、一度本体アプリを開いて辞書更新を完了させる必要があります。

## 絵文字タブ (Ver 2.1 導入)
iOS 標準絵文字キーボードを模したタブを実装し、倍率変更など独自機能を提供しています。

### ユーザ設定による最適化
1. **肌の色バリエーションの記憶**
2. **よく使う絵文字**（単なる「最近」ではなく使用頻度でランキング）
3. **倍率の記録**

最適化データはキーボード内部に保存され、コンテナアプリケーションからは不可視です。

### 今後の改善案

* 肌の色のデフォルト設定 ／ お気に入り絵文字のピン留め  
* 縦持ち・横持ちで異なる倍率を保持

### データ
絵文字タブが参照するジャンルファイルも iOS バージョン別にプリビルドされた `emoji_genre_E16.0.txt` などを用います。

### CustardKit API
現状、絵文字タブは SwiftUI 実装でCustardKitを用いておらず、ユーザが同等のタブを自作することはできません。需要があれば API 化を検討します。
