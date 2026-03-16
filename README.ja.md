<p align="center">
  <img src="assets/feature-graphic-1024x500.png" alt="Relaydex banner" />
</p>

# Relaydex

[English README](README.md)

Relaydex は、[Remodex](https://github.com/Emanuele-web04/remodex) をベースにした独立 fork です。目的はひとつに絞っています。

- Windows 上でローカル Codex を動かす
- `relaydex up` でローカル bridge を起動する
- Android からその Codex セッションを remote control する

Codex 本体はホスト PC 上で動き続け、Android アプリはペア済みリモートクライアントとして動作します。

## このリポジトリに含まれるもの

- `phodex-bridge/`: npm 配布用の CLI bridge。`relaydex` として配布予定
- `android/`: Android クライアント本体
- `CodexMobile/`: upstream iOS 実装。プロトコル参照と互換確認用

現状の既定フローでは、relay として `api.phodex.app` を利用します。必要なら互換 relay の self-host も可能です。

## クレジットと立場

Relaydex は [Remodex](https://github.com/Emanuele-web04/remodex) の独立 fork です。元プロジェクトの作者は Emanuele Di Pietro です。

- これは公式 Remodex アプリではありません
- upstream 作者の公認やサポートを示すものではありません
- Windows host + Android remote-control 体験に特化して再構成しています

## これは何か

Relaydex は、スマホ上で Codex そのものを動かすアプリではありません。

- ローカル bridge と Codex はホスト PC 上で動きます
- Android はペア済み remote client として動きます
- git 操作や workspace 変更もホスト側で実行されます

## 現在の配布状況

- Windows 側 bridge は `relaydex` として npm 配布
- Android アプリは Google Play のクローズドテスト中
- 現在のクローズドテスト版は無料でインストール可能
- Android 本番版は、クローズドテスト終了後に有料化する予定です

想定している通常の利用手順は次のとおりです。

```sh
npm install -g relaydex
relaydex up
```

そのあと Android アプリで QR を読み込みます。

## Windows + Android クイックスタート

[Docs/WINDOWS_ANDROID_QUICKSTART.md](Docs/WINDOWS_ANDROID_QUICKSTART.md) を参照してください。

短い流れは次のとおりです。

1. Windows に Node.js と Codex CLI を入れる
2. `relaydex` をインストールする
3. ローカル project ディレクトリで `relaydex up` を実行する
4. Android アプリを開く
5. QR か pairing payload で接続する

## Android クローズドテスト参加方法

Relaydex は現在、Google Play のクローズドテスト中です。

参加手順:

1. テスター用 Google グループに参加する  
   `https://groups.google.com/g/relaydex-android-testers`
2. Play のテスト参加リンクを開いて opt-in する  
   `https://play.google.com/apps/testing/io.relaydex.android`
3. Play から現在のクローズドテスト版をインストールする  
   `https://play.google.com/store/apps/details?id=io.relaydex.android`
4. 一度アプリを起動し、できれば実際にホストとペアリングしてみる
5. 14 日間は opt-in 状態とインストール状態を維持する
6. バグや導線の問題があればフィードバックする

補足:

- 現在のクローズドテスト版は無料です
- 将来の本番 Android 版は有料化予定です
- Android アプリは remote client だけで、Codex は自分の PC 上で動かします
- これは公式 Remodex アプリではありません

## フィードバックの送り先

テスター向けの基本導線:

- クローズドテスト案内ページ: `https://ranats.github.io/relaydex/closed-test.html`
- Play opt-in: `https://play.google.com/apps/testing/io.relaydex.android`
- Play インストールページ: `https://play.google.com/store/apps/details?id=io.relaydex.android`
- GitHub issue フォーム: `https://github.com/Ranats/relaydex/issues/new?template=closed-test-feedback.yml`

おすすめのフィードバック内容:

- 端末機種と Android バージョン
- `relaydex up` で接続できたか
- QR / payload のどちらで接続したか
- どの画面で詰まったか
- 再現手順
- スクリーンショット

GitHub が使いにくい場合は、Play 上の非公開フィードバックかサポートメールでも問題ありません。

## Android アプリでできること

現在の Android クライアントが対応している機能:

- QR pairing
- pairing payload の手入力
- スレッド一覧
- 既存スレッドの表示
- 新規スレッド作成
- プロンプト送信
- ストリーミング出力表示
- approval prompt への応答
- saved pairing からの reconnect
- モデルと reasoning 設定

## セキュリティ

モバイル client と bridge は、upstream Remodex と同じ E2EE セッションモデルを使います。wire 互換を保つため、内部フィールド名に `mac` や `iphone` が残る箇所がありますが、これはプロトコル上の名残であり、実際の platform 制約ではありません。

## 公開前に見ておく資料

- [Docs/ANDROID_FORK_GUIDE.md](Docs/ANDROID_FORK_GUIDE.md)
- [Docs/CLOSED_TEST_PLAN.md](Docs/CLOSED_TEST_PLAN.md)
- [Docs/WINDOWS_ANDROID_QUICKSTART.md](Docs/WINDOWS_ANDROID_QUICKSTART.md)
- [Docs/LAUNCH_COPY.md](Docs/LAUNCH_COPY.md)
- [Docs/PLAY_STORE_COPY.md](Docs/PLAY_STORE_COPY.md)
- [Docs/PLAY_CONSOLE_SETUP.md](Docs/PLAY_CONSOLE_SETUP.md)
- [Docs/PRIVACY_POLICY.md](Docs/PRIVACY_POLICY.md)
- [Docs/TESTER_RECRUITMENT_COPY.md](Docs/TESTER_RECRUITMENT_COPY.md)
- [Docs/RELEASE_CHECKLIST.md](Docs/RELEASE_CHECKLIST.md)

## ライセンス

[ISC](LICENSE)
