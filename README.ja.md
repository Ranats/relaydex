<p align="center">
  <img src="assets/feature-graphic-1024x500.png" alt="Relaydex banner" />
</p>

# Relaydex

[![npm version](https://img.shields.io/npm/v/relaydex)](https://www.npmjs.com/package/relaydex)
[![License: ISC](https://img.shields.io/badge/License-ISC-blue.svg)](LICENSE)
[Follow on X](https://x.com/Ranats85)

[English README](README.md)

Relaydex は [Remodex](https://github.com/Emanuele-web04/remodex) をベースにした independent fork で、次のワークフローに絞って開発しています。

- Windows 上でローカル Codex を動かす
- `relaydex up` でローカル bridge を起動する
- Android からその Codex セッションを remote control する

Relaydex は local-first です。Codex はホスト PC 上で動き続け、スマホは安全にペアリングされた remote client として動作します。

## 目次

- [概要](#概要)
- [主な機能](#主な機能)
- [現在の状況](#現在の状況)
- [Bridge のインストール](#bridge-のインストール)
- [Android アプリを source build する](#android-アプリを-source-build-する)
- [クイックスタート](#クイックスタート)
- [コマンド](#コマンド)
- [アーキテクチャ](#アーキテクチャ)
- [リポジトリ構成](#リポジトリ構成)
- [Android の公開状況](#android-の公開状況)
- [フィードバック](#フィードバック)
- [環境変数](#環境変数)
- [Self-Hosting](#self-hosting)
- [FAQ](#faq)
- [セキュリティ](#セキュリティ)
- [クレジット](#クレジット)
- [ライセンス](#ライセンス)

## 概要

Relaydex は、スマホ上で Codex 自体を動かすアプリではありません。

- ホスト PC 上で local bridge と local Codex runtime が動きます
- Android アプリは paired remote client として接続します
- git 操作や workspace 変更は引き続きホスト側で実行されます

## 主な機能

- Android クライアントと host bridge の end-to-end encrypted pairing
- Codex、git、ファイル操作を Windows 側に残す local-first workflow
- QR pairing と pairing payload の貼り付け
- Android から既存スレッドを開く、新規スレッドを作る
- Android 上で Codex の出力をストリーミング表示
- approval prompt への応答
- saved pairing からの reconnect
- Android 上での model / reasoning 設定

## 現在の状況

- Windows host bridge は `relaydex` として npm 公開済みです
- Android アプリのソースコードはこのリポジトリで公開しています
- Google Play での一般公開はまだ準備中です

今すぐ Android クライアントを試したい場合は、`android/` から source build してください。

## Bridge のインストール

```sh
npm install -g relaydex@latest
```

Codex を動かしたいローカル project directory で次を実行します。

```sh
relaydex up
```

その後、Android アプリで QR を読み込んでペアリングします。

## Android アプリを source build する

Play 配布前でも、Android Studio や Gradle から自分でビルドして試せます。

1. `android/` を Android Studio で開く
2. Gradle sync の完了を待つ
3. Android 端末を接続するか emulator を起動する
4. `app` 構成で実行する

CLI からビルドする場合:

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
2. bridge package をインストールする
3. ローカル project directory で `relaydex up` を実行する
4. Android アプリを開く
5. QR を読むか、QR 下の pairing payload を貼り付ける
6. スレッドを開くか作成し、メッセージを送る

## コマンド

### `relaydex up`

ローカル bridge を起動し、`codex app-server` を立ち上げ、新しい pairing QR を表示します。

### `relaydex reset-pairing`

保存済みの trusted-device state を消し、次の `relaydex up` で新しい pairing を始められるようにします。

### `relaydex resume`

可能な場合は、最後に使っていたスレッドをローカル Codex desktop app で開き直します。

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

必要に応じて、desktop Codex app は `~/.codex/sessions` に保存された session も参照できます。

## リポジトリ構成

```text
remodex/
|-- phodex-bridge/                # CLI bridge package
|   |-- bin/                      # CLI entrypoints
|   `-- src/                      # Bridge runtime and handlers
|-- android/                      # Android Studio project
|   `-- app/                      # Kotlin + Compose Android client
|-- CodexMobile/                  # Upstream iOS source tree kept for protocol reference
|-- relay/                        # Relay implementation
`-- assets/                       # Public graphics
```

## Android の公開状況

Android アプリはまだ Google Play で一般公開していません。

現時点での公開導線は 2 つです。

1. source build して自分で試す
2. 後日の Play rollout に向けて waitlist に参加する

推奨 waitlist ページ:

- [英語の waitlist ページ](https://ranats.github.io/relaydex/waitlist.html)
- [日本語の waitlist ページ](https://ranats.github.io/relaydex/waitlist-ja.html)

後日の Play rollout に備えておきたい場合は、Google Groups で `relaydex-android-testers` を検索して参加しておいても大丈夫です。

- [Google Groups](https://groups.google.com/)

実際の Play opt-in とインストール手順は、後日案内します。

## フィードバック

source build を試して、バグ、pairing 問題、reconnect 問題、UI 上の分かりにくさなどがあれば、次から報告してください。

- GitHub Issues: `https://github.com/Ranats/relaydex/issues`
- X: `https://x.com/Ranats85`

あると助かる情報:

- 端末名と Android バージョン
- QR、payload、reconnect のどれで接続したか
- 再現手順
- 期待した結果
- 実際に起きた結果
- スクリーンショットやログ

## 環境変数

bridge は `RELAYDEX_*` と legacy の `REMODEX_*` の両方を受け付けます。

| Variable | 説明 |
|----------|------|
| `RELAYDEX_RELAY` | relay URL を上書きする |
| `RELAYDEX_CODEX_ENDPOINT` | ローカル起動の代わりに既存 Codex WebSocket に接続する |
| `RELAYDEX_REFRESH_ENABLED` | macOS desktop refresh workaround を明示的に有効化する |
| `RELAYDEX_REFRESH_DEBOUNCE_MS` | refresh の debounce 時間を調整する |
| `RELAYDEX_CODEX_BUNDLE_ID` | macOS 上の Codex desktop bundle ID を上書きする |

source build や self-hosting をする場合は、hosted default を前提にせず必要に応じて設定してください。

## Self-Hosting

この公開リポジトリは self-host friendly な形を意図しています。

- ローカル relay や自前 hosted relay を使えます
- relay は transport hop にすぎません
- Codex 自体は自分のマシン上で動きます

self-host する場合は、private な host 名、IP、認証情報を public repository に入れないでください。

## FAQ

**Windows で動きますか？**  
はい。この fork は Windows host + Android workflow に特化しています。

**スマホ上で Codex 自体が動くのですか？**  
いいえ。Codex はホスト PC 上で動き、スマホは paired remote client です。

**`relaydex up` を実行しているターミナルを閉じるとどうなりますか？**  
bridge が停止します。新しい live session を作るには再度起動してください。

**pairing state を消したい場合は？**  
`relaydex reset-pairing` を実行してから、もう一度 `relaydex up` を実行してください。

**relay を self-host できますか？**  
はい。それもこの公開リポジトリの想定ユースケースのひとつです。

## セキュリティ

mobile client と bridge は upstream Remodex と同じ end-to-end encrypted session model を使っています。wire-level 互換性をできるだけ維持しているため、内部 field 名には `mac` や `iphone` が残っていますが、これは protocol 上の名残であり、実際の platform 制約ではありません。

## クレジット

Relaydex は [Remodex](https://github.com/Emanuele-web04/remodex) の independent fork です。元のプロジェクトは Emanuele Di Pietro によって作られました。

このリポジトリは公式 Remodex アプリではなく、upstream 作者の公認やサポートを示すものではありません。

## ライセンス

[ISC](LICENSE)
