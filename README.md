# 日次ヘルス

個人用のサイドロード Android アプリです。Health Connect に入っているその日の歩数、距離、消費カロリー、睡眠、心拍、心拍変動、運動、体重、体脂肪、食事、水分を読み、JSON で webhook に POST します。1日分の中身は `health-daily/v1`（タイムゾーン `Asia/Tokyo`）のまま、同期で送る日は `health-daily/v1-batch` にまとめて1回の POST にします（31日を超えるときだけ次のリクエストに分けます）。Play ストアには出しません。

取れなかった項目は `null` のまま送ります。権限が一部だけのときも、Health Connect が無いときも、送れる分だけ送ります。睡眠は、その日（日本時間）に終わったセッションです。自動送信は毎日 21:00（Asia/Tokyo）です。

## リクエストの形

同期1回につき POST は1回です。今日、昨日、過去7日、21:00 の自動送信は、どれも同じです。ボディは次の形です。

```json
{
  "schema": "health-daily/v1-batch",
  "days": [
    {
      "schema": "health-daily/v1",
      "date": "2026-10-18",
      "timezone": "Asia/Tokyo",
      "steps": 8123
    }
  ]
}
```

上の日オブジェクトは形を短くしたものです。実際の各要素は、これまで1日ずつ送っていた `health-daily/v1` と同じフィールドをすべて含み、`schema` も `health-daily/v1` のままです。取れなかった項目は `null` です。受信側は `days` の要素を、中身を変えずに1日分として保存できます。

- 送る日が1日でも、このバッチ形式を使います。
- `days` は `date` の昇順です。
- 同じ同期に同じ日付が複数あるときは1件にまとめ、後から用意したデータを残します。
- 31日を超えるときだけ分割します。1リクエストは最大31日で、残りは次のバッチです。
- URL と認証ヘッダーの付け方はこれまでと同じです。自動送信が通信失敗やサーバーエラーで再試行されるときも、日ごとに分けず、そのバッチ全体を送り直します。

## Actions の成果物から入れる

動作確認用のデバッグ APK です。実行のたびに別のデバッグ鍵で署名されるので、更新には使えません。インストールと更新は「リリース」です。

1. このリポジトリの **Actions** を開きます。
2. ワークフロー **Build debug APK** の、成功した実行を開きます。
3. 成果物 **health-daily-debug-apk** をダウンロードし、中の APK を端末へ転送します。
4. 提供元不明アプリのインストールを許可し、APK を入れます。

`main` への push、pull request、手動実行（workflow_dispatch）でデバッグ APK が作られます。署名のシークレットは要りません。

## リリース

Obtainium は、このリポジトリの GitHub Releases を追跡してインストールと更新をします。

1. `main` の履歴に含まれるコミットへ、タグ `vX.Y.Z`（例: `v1.2.3`）を打ちます。
2. そのタグを push します。
3. ワークフローが、タグの付いたコミットがデフォルトブランチの履歴に含まれることを確認し、通常の Release を作ります。履歴に無いタグは失敗します。

このアプリは本番用です。`vX.Y.Z` は通常リリースです。`vX.Y.Z-beta.N` はプレリリース用の形式ですが、このリポジトリでは使わず、そのタグは失敗します。

付くファイルは `health-daily-vX.Y.Z.apk` と `health-daily-vX.Y.Z.apk.sha256`（`sha256sum` の形式）だけです。

`versionName` はタグから先頭の `v` を除いた値です。`versionCode` は `X*1000000 + Y*10000 + Z*100 + 99` です。どちらも CI が渡すので、リリースのたびにソースは書き換えません。手元のビルドは versionName `1.0.0`、versionCode `1` のままです。

署名鍵は Actions のシークレット `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` です。どれか無いとリリースは失敗します。デバッグ鍵にはフォールバックしません。キーストアはリポジトリに入れません。

## 設定はアプリの中で入れる

Webhook の URL と `Authorization` ヘッダーは、このリポジトリには入っていません。インストールしたあと、アプリの設定画面で入力します。値は端末内の暗号化ストレージに保存され、ソースや送信ログには残りません。

- URL は実行時に貼ります。
- ヘッダー名の初期値は `Authorization` です。受信側が別の名前を指定したときだけ変えます。
- ヘッダー値は、渡された文字列をそのまま貼ります。アプリは接頭辞を足したり削ったりしません。空ならヘッダー自体を送りません。

## 手元でビルドする

JDK 17 以上と Android SDK（platform `android-37.2`、build-tools `36.0.0`）が必要です。SDK の場所は環境変数 `ANDROID_HOME` か、gitignore 済みの `local.properties`（`sdk.dir`）で指定します。

```bash
./gradlew test
./gradlew assembleDebug
```

APK は `app/build/outputs/apk/debug/app-debug.apk` にできます。

リリース署名は、次の四つが環境変数か Gradle プロパティ（`-P`）で揃ったときだけ付きます。`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` です。無いときの `assembleRelease` は未署名のままです。`versionName` と `versionCode` は `-PversionName=` と `-PversionCode=`、または環境変数 `VERSION_NAME` と `VERSION_CODE` で上書きできます。
