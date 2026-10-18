# 日次ヘルス

個人用のサイドロード Android アプリです。Health Connect に入っているその日の歩数、距離、消費カロリー、睡眠、心拍、心拍変動、運動、体重、体脂肪、食事、水分を読み、JSON（`health-daily/v1`、タイムゾーン `Asia/Tokyo`）で webhook に POST します。Play ストアには出しません。

取れなかった項目は `null` のまま送ります。権限が一部だけのときも、Health Connect が無いときも、送れる分だけ送ります。睡眠は、その日（日本時間）に終わったセッションです。自動送信は毎日 21:00（Asia/Tokyo）です。

## Actions の成果物から入れる

1. このリポジトリの **Actions** を開きます。
2. ワークフロー **Build debug APK** の、成功した実行を開きます。
3. 成果物 **health-daily-debug-apk** をダウンロードし、中の APK を端末へ転送します。
4. 提供元不明アプリのインストールを許可し、APK を入れます。

`main` への push、pull request、手動実行（workflow_dispatch）でデバッグ APK が作られます。

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
