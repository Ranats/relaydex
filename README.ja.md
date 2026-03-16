<p align="center">
  <img src="assets/feature-graphic-1024x500.png" alt="Relaydex banner" />
</p>

# Relaydex

[English README](README.md)

Relaydex は、[Remodex](https://github.com/Emanuele-web04/remodex) をベースにした独立 fork です。目的はひとつに絞っています。

- Windows 上でローカル Codex を動かす
- `relaydex up` でローカル bridge を起動する
- Android からその Codex セッションを remote control する

Relaydex は local-first です。Codex 本体はホスト PC 上で動き続け、Android アプリはペア済み remote client として動作します。

## これは何か

Relaydex は、スマホ上で Codex そのものを動かすアプリではありません。

- ローカル bridge と Codex はホスト PC 上で動きます
- Android はペア済み remote client として動きます
- git 操作や workspace 変更もホスト側で実行されます

## 主な機能

- Android クライアントとホスト bridge の end-to-end encrypted pairing
- Codex、git、ファイル操作を Windows 側に残す local-first な実行モデル
- QR pairing と pairing payload 貼り付け
- 既存スレッドの表示と新規スレッド作成
- Android 上でのストリーミング出力表示
- approval prompt への応答
- saved pairing からの reconnect
- モデルと reasoning の設定

## 現在の状況

- Windows 側 bridge は `relaydex` として npm 配布済み
- Android アプリのソースコードはこのリポジトリで公開中
- Google Play での一般公開はまだ準備中

今すぐ Android クライアントを試したい場合は、`android/` から source build してください。

## Bridge のインストール

```sh
npm install -g relaydex@latest
```

Codex を動かしたいローカル project ディレクトリで実行します。

```sh
relaydex up
```

そのあと Android アプリで QR を読み込みます。

## Android アプリを source build する

Play 配布前でも、Android Studio か Gradle で自分でビルドして試せます。

1. `android/` を Android Studio で開く
2. Gradle sync を完了させる
3. Android 実機か emulator を接続する
4. `app` 構成を実行する

CLI での build:

```sh
cd android
gradlew assembleDebug
```

debug APK は通常ここに出力されます。

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## クイックスタート

1. Windows に Node.js と Codex CLI を入れる
2. bridge をインストールする
3. ローカル project ディレクトリで `relaydex up` を実行する
4. Android アプリを開く
5. QR か pairing payload で接続する
6. スレッドを開くか作成して、メッセージを送る

## コマンド

### `relaydex up`

ローカル bridge を起動し、`codex app-server` を立ち上げ、ペアリング用 QR を表示します。

### `relaydex reset-pairing`

保存済みの trusted-device state を消し、次回 `relaydex up` 時に新しい pairing を始めます。

### `relaydex resume`

利用可能であれば、最後に使っていたスレッドをローカル Codex デスクトップアプリで開きます。

### `relaydex watch [threadId]`

指定スレッドの rollout log をリアルタイムで表示します。

## アーキテクチャ

```text
[Android client]
        <-> paired relay WebSocket session <->
[relaydex bridge on host computer]
        <-> stdin/stdout JSON-RPC <->
[codex app-server]
```

Codex デスクトップアプリが利用可能な場合は、`~/.codex/sessions` の永続化済み session を読み取れます。

## プロジェクト構成

```text
remodex/
|-- phodex-bridge/                # CLI bridge package
|   |-- bin/                      # CLI entrypoints
|   `-- src/                      # Bridge runtime and handlers
|-- android/                      # Android Studio project
|   `-- app/                      # Kotlin + Compose Android client
|-- CodexMobile/                  # upstream iOS source tree。protocol reference 用
|-- relay/                        # Relay implementation
`-- assets/                       # Public graphics
```

## Android 配布について

Android アプリはまだ Google Play で一般公開していません。

現時点での公開導線は 2 つです。

1. source build して自分で試す
2. 後日の Play 配布に向けて Google Group の waitlist に参加する

waitlist:

- `https://groups.google.com/g/relaydex-android-testers`

Play 上での opt-in とインストールの導線は、後日あらためて案内します。

## フィードバック

source build を試して不具合や UX の違和感があれば、次のどちらかに送ってください。

- GitHub Issues: `https://github.com/Ranats/relaydex/issues`
- Support email: `saxophonia991@gmail.com`

あると助かる情報:

- 端末機種と Android バージョン
- QR、payload、reconnect のどれを使ったか
- 再現手順
- 期待した結果
- 実際に起きたこと
- スクリーンショットやログ

## 環境変数

bridge は `RELAYDEX_*` と legacy の `REMODEX_*` の両方を受け付けます。

| Variable | 説明 |
|----------|------|
| `RELAYDEX_RELAY` | relay URL を上書きする |
| `RELAYDEX_CODEX_ENDPOINT` | ローカル起動ではなく既存 Codex WebSocket に接続する |
| `RELAYDEX_REFRESH_ENABLED` | macOS の desktop refresh workaround を明示的に有効化する |
| `RELAYDEX_REFRESH_DEBOUNCE_MS` | refresh の debounce 時間を調整する |
| `RELAYDEX_CODEX_BUNDLE_ID` | macOS 上の Codex desktop bundle ID を上書きする |

source build や self-hosting をする場合は、暗黙の hosted default を前提にせず明示的に設定してください。

## Self-Hosting

公開リポジトリは self-host friendly な形を維持する方針です。

- ローカル relay や自前の hosted relay を使えます
- relay は transport hop であり、Codex 本体ではありません
- Codex は引き続き自分のマシン上で動きます

self-host する場合は、ホスト名、IP、資格情報などの private な値を public repository に入れないでください。

## FAQ

**Windows で動きますか？**  
はい。この fork は Windows host + Android workflow に特化しています。

**スマホ上で Codex を動かしますか？**  
いいえ。Codex はホスト PC 上で動き、スマホは paired remote client です。

**`relaydex up` を実行しているターミナルを閉じるとどうなりますか？**  
bridge が停止します。再度起動すると新しい live session が始まります。

**pairing state をリセットしたいときは？**  
`relaydex reset-pairing` を実行してから、もう一度 `relaydex up` を実行してください。

**relay を self-host できますか？**  
はい。公開 repo の想定利用方法のひとつです。

## セキュリティ

mobile client と bridge は upstream Remodex と同じ E2EE session model を使います。wire-level 互換のため、内部フィールド名に `mac` や `iphone` が残る箇所がありますが、これは protocol 上の名残であって platform 制約ではありません。

## クレジット

Relaydex は [Remodex](https://github.com/Emanuele-web04/remodex) の独立 fork です。元プロジェクトの作者は Emanuele Di Pietro です。

これは公式 Remodex アプリではなく、upstream 作者の公認やサポートを示すものではありません。

## ライセンス

[ISC](LICENSE)
