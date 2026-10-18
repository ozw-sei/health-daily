# 日次ヘルス / Health Daily

個人用のサイドロード Android アプリです。Health Connect からその日の健康データを読み、JSON で webhook に POST します。Play ストアには出しません。受信側は習慣トラッカー用の webhook です。

A personal sideloaded Android app. It reads one day of Health Connect data and POSTs JSON to a webhook. It is not published on the Play Store. The receiver is a habit-tracker webhook.

- minSdk 28 / targetSdk 37（compileSdk は安定版プラットフォーム 37.2）
- Kotlin, Jetpack Compose, WorkManager, EncryptedSharedPreferences
- パッケージ `jp.healthsync.daily`

compileSdk は 2026-10 時点の安定版プラットフォーム `android-37.2`、targetSdk は API 37 です。

compileSdk is the stable `android-37.2` platform (October 2026). targetSdk is API 37.

---

## 日本語

### 何を読むか

Fitbit、Pixel Watch、asken、MyFitnessPal、EufyLife など、Health Connect に書かれたデータをアプリ名では絞りません。許可された種類だけ読み、書けなかった項目は `null` のまま送ります。

| データ | Health Connect | JSON |
| --- | --- | --- |
| 歩数 | Steps | `steps` |
| 距離 | Distance | `distance_m` |
| 活動消費カロリー | ActiveCaloriesBurned | `active_kcal` |
| 総消費カロリー（基礎代謝を含む） | TotalCaloriesBurned | `total_kcal` |
| ワークアウト | ExerciseSession | `exercise` |
| 睡眠（ステージ付き） | SleepSession | `sleep` |
| 心拍 | HeartRate | `heart_rate.avg/min/max` |
| 安静時心拍 | RestingHeartRate | `heart_rate.resting` |
| 心拍変動 RMSSD | HeartRateVariabilityRmssd | `hrv_rmssd_ms` |
| 体重 | Weight | `weight_kg` |
| 体脂肪率 | BodyFat | `body_fat_pct` |
| 食事（エネルギー、たんぱく質、脂質、炭水化物） | Nutrition | `nutrition` |
| 水分 | Hydration | `hydration_ml` |

歩数・距離・消費カロリー・心拍の平均/最小/最大は Health Connect の集計（複数ソースの重複を除いた値）です。食事と水分は、その日に開始した記録を合計します。同じ食事を asken と MyFitnessPal の両方に入れると二重に数えます。体重と体脂肪はその日の最後の1件です。

睡眠は、終了時刻がその日付（日本時間）に入るセッションです。前の夜に寝て朝起きた睡眠は、起きた日の要約に入ります。ステージがあるときは深い/浅い/REM/覚醒を分単位で合計し、覚醒は `total_min` に含めません。ステージが無いセッションは長さだけを `total_min` にし、ステージ欄は `null` です。

ワークアウトは開始時刻がその日のものだけです。`type` は Health Connect の種別名（例: `RUNNING`）です。

`sources` は、その日の要約に寄与したアプリのパッケージ名です。

### いつ送るか

- 自動: 毎日 21:00（Asia/Tokyo）頃。送るのはその実行日。Doze やオフラインで遅れても、予定していた日付を送ります。
- 失敗: 通信エラー、HTTP 408 / 429 / 5xx は 15 分起点の指数バックオフで最大 5 回。URL 未設定や 401 などの 4xx は再試行しません。
- 手動: 「今日を今すぐ送信」「昨日を送信」「過去7日を送信」（今日を含む7日、1日1 POST、古い日から）。

21:00 の送信はアプリが前面に無いので、Health Connect の「バックグラウンドでの読み取り」が必要です。未許可のときは手動送信（アプリを開いた状態）は動き、自動送信は拒否されます。

### 認証ヘッダー

受信側は標準の HTTP `Authorization` ヘッダーで認証します。オーナーは渡された値を **1つの文字列としてそのまま** 貼ります。

- ヘッダー名の初期値は `Authorization`。相手が別の名前を指定したときだけ変えます。
- 値の例: `Bearer sk_live_…` や、相手が渡したトークン全体。アプリは `Bearer` を足したり削ったりしません。
- 値が空ならヘッダーを付けません。
- URL・ヘッダー名・値は EncryptedSharedPreferences（鍵 AES-256-SIV、値 AES-256-GCM）に保存します。送信ログにヘッダー値は残しません。

### ビルド

JDK 17 以上と Android SDK（platform `android-37.2`、build-tools 36.0.0）が必要です。`ANDROID_HOME` か `local.properties` の `sdk.dir` を設定します。

```bash
./gradlew test
./gradlew assembleDebug
```

デバッグ APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions（`.github/workflows/debug-apk.yml`）でも、push のたびに同じ debug APK をアーティファクト `health-daily-debug` として残します。

### インストールと権限

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Android 13 以前では Health Connect（`com.google.android.apps.healthdata`）をインストールします。Android 14 以降はシステムの一部です。アプリのボタンからも開けます。
2. 「日次ヘルス」を開き、「権限をリクエスト」を押します。
3. 歩数、距離、活動/総消費カロリー、エクササイズ、睡眠、心拍、安静時心拍、心拍変動、体重、体脂肪、栄養、水分、バックグラウンド読み取りを許可します。一部だけでも、許可された分だけ送ります。
4. Webhook URL を入れ、ヘッダー名は `Authorization` のまま、受信側の値を貼って「設定を保存」します。
5. 「今日を今すぐ送信」で試し、画面下の履歴に HTTP ステータスが出ることを確認します。

自宅 LAN の `http://` webhook も送れます（このアプリはクリアテキストを許可しています）。可能なら `https://` を使ってください。

---

## English

### What it reads

The app does not filter by Fitbit, Pixel Watch, asken, MyFitnessPal, or EufyLife. Anything those apps (or others) have written to Health Connect is eligible. Types the owner did not grant are left `null`.

| Data | Health Connect | JSON |
| --- | --- | --- |
| Steps | Steps | `steps` |
| Distance | Distance | `distance_m` |
| Active calories | ActiveCaloriesBurned | `active_kcal` |
| Total calories, including BMR | TotalCaloriesBurned | `total_kcal` |
| Workouts | ExerciseSession | `exercise` |
| Sleep, with stages | SleepSession | `sleep` |
| Heart rate | HeartRate | `heart_rate.avg/min/max` |
| Resting heart rate | RestingHeartRate | `heart_rate.resting` |
| Heart-rate variability RMSSD | HeartRateVariabilityRmssd | `hrv_rmssd_ms` |
| Weight | Weight | `weight_kg` |
| Body fat | BodyFat | `body_fat_pct` |
| Food (energy, protein, fat, carbs) | Nutrition | `nutrition` |
| Hydration | Hydration | `hydration_ml` |

Steps, distance, calorie totals, and heart-rate avg/min/max use Health Connect aggregates, which dedupe overlapping sources. Nutrition and hydration sum records that **start** on that Tokyo date, so logging the same meal in both asken and MyFitnessPal counts twice. Weight and body fat are the latest sample on that date.

Sleep is every session whose **end** falls on that Tokyo date, so last night's sleep belongs to the morning you woke up. With stages, deep / light / REM / awake are summed in minutes and awake is not part of `total_min`. Sessions with no stages contribute only their length to `total_min`; the stage fields stay `null`.

Exercise is included when its **start** falls on that date. `type` is the Health Connect name, such as `RUNNING`.

`sources` lists the package names that contributed to the summary.

### When it sends

- Automatic: around 21:00 Asia/Tokyo every day, for that calendar date. If Doze or a lack of network delays the job, it still sends the date that was scheduled, not "whatever day it is when the retry lands".
- Retries: network errors and HTTP 408 / 429 / 5xx use exponential backoff starting at 15 minutes, up to 5 attempts. A missing URL and other 4xx responses (including 401) are not retried.
- Manual: "Send today now", "Send yesterday", and "Send the last 7 days" (today plus the previous 6 days, one POST per day, oldest first).

The 21:00 job runs in the background, so Health Connect's background-read permission is required. Manual sends work while the app is open without it. The automatic job does not.

### Auth header

The receiver authenticates with a normal HTTP `Authorization` header. The owner pastes the provided value as **one string**.

- The header name defaults to `Authorization`. Change it only if the receiver names a different header.
- Example values: `Bearer sk_live_…`, or whatever full token string was issued. The app does not add or strip a `Bearer` prefix.
- An empty value means the header is omitted.
- The URL, header name, and header value are stored in EncryptedSharedPreferences (AES-256-SIV keys, AES-256-GCM values). The send log never stores the header value.

### Build

Requires JDK 17+ and the Android SDK (platform `android-37.2` and build-tools 36.0.0). Set `ANDROID_HOME`, or `sdk.dir` in `local.properties`.

```bash
./gradlew test
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/debug-apk.yml`) runs the same steps on every push and uploads the debug APK as the `health-daily-debug` artifact.

### Install and grant access

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. On Android 13 and earlier, install Health Connect (`com.google.android.apps.healthdata`). On Android 14 and later it is part of the system. The button in the app opens the store listing.
2. Open 日次ヘルス and tap the permission request.
3. Grant steps, distance, active calories, total calories, exercise, sleep, heart rate, resting heart rate, heart-rate variability, weight, body fat, nutrition, hydration, and background read. A partial grant is fine: only granted types are sent.
4. Enter the webhook URL, leave the header name as `Authorization`, paste the receiver's value, and save.
5. Tap "Send today now" and check the log at the bottom for the HTTP status.

`http://` webhooks on a home LAN are allowed (cleartext is enabled). Prefer `https://` when you have it.

---

## Schema `health-daily/v1`

One POST per day. `Content-Type: application/json; charset=utf-8`. Missing measurements are JSON `null`, not omitted. `exercise` is `null` when there were no sessions (not `[]`). `sources` is always an array. Whole numbers are written without a trailing `.0`. Times are ISO-8601 with the offset stored on the record, or `+09:00` when the record has none. The `date` and the bucketing rules use `Asia/Tokyo`.

1日1回の POST です。欠測はキーを残したまま `null` です。運動が無い日の `exercise` は `[]` ではなく `null` です。`sources` は常に配列です。時刻は記録に付いたオフセットの ISO-8601 で、オフセットが無いときは `+09:00` です。日付の区切りは日本時間です。

```json
{
  "schema": "health-daily/v1",
  "date": "2026-10-18",
  "timezone": "Asia/Tokyo",
  "steps": 8123,
  "distance_m": 6200.5,
  "active_kcal": 340.2,
  "total_kcal": 2140,
  "sleep": {
    "start": "2026-10-17T23:40:00+09:00",
    "end": "2026-10-18T07:10:00+09:00",
    "total_min": 420,
    "deep_min": 110,
    "light_min": 190,
    "rem_min": 120,
    "awake_min": 30
  },
  "heart_rate": { "resting": 55, "avg": 80, "min": 60, "max": 100 },
  "hrv_rmssd_ms": 45,
  "exercise": [
    {
      "type": "RUNNING",
      "start": "2026-10-18T09:00:00+09:00",
      "end": "2026-10-18T09:32:00+09:00",
      "duration_min": 32,
      "title": "朝ラン"
    }
  ],
  "weight_kg": 70.4,
  "body_fat_pct": 18.3,
  "nutrition": { "kcal": 700, "protein_g": 40.5, "fat_g": 20, "carbs_g": 55 },
  "hydration_ml": 1800,
  "sources": ["com.eufylife.android", "com.fitbit.FitbitMobile", "com.google.android.apps.fitness"]
}
```

| Field | Meaning / 意味 |
| --- | --- |
| `schema` | Always `health-daily/v1`. |
| `date` | Civil date in Asia/Tokyo, `YYYY-MM-DD`. |
| `timezone` | Always `Asia/Tokyo`. |
| `steps` | Aggregate step count. / 集計歩数。 |
| `distance_m` | Meters, 1 decimal. / メートル、小数1桁。 |
| `active_kcal` | Active kilocalories, 1 decimal. |
| `total_kcal` | Total kilocalories burned, including basal, 1 decimal. Not food. / 食事ではなく消費カロリー。 |
| `sleep.start` / `sleep.end` | Earliest start and latest end of sessions that ended on `date`. |
| `sleep.total_min` | Asleep minutes. Excludes awake when stages exist. |
| `sleep.deep_min` / `light_min` / `rem_min` / `awake_min` | Stage minutes. `0` when stages exist but that bucket is empty. All four are `null` when no session had stages. Unspecified asleep time is only in `total_min`. |
| `heart_rate.resting` | Latest resting bpm on that date. |
| `heart_rate.avg` / `min` / `max` | Health Connect heart-rate aggregate for that date. Avg from the aggregate is a whole number. |
| `hrv_rmssd_ms` | Average RMSSD (ms) of samples on that date, 1 decimal. |
| `exercise[].type` | Health Connect exercise name, for example `RUNNING`. Unknown ids become `UNKNOWN_<n>`. |
| `exercise[].duration_min` | Full session length in rounded minutes. |
| `exercise[].title` | Session title, or `null`. |
| `weight_kg` | Latest weight that day, 2 decimals. |
| `body_fat_pct` | Latest body-fat percentage that day, 1 decimal. |
| `nutrition.kcal` / `protein_g` / `fat_g` / `carbs_g` | Sum of meals that started that day. A macro that never appeared is `null`. The object itself is `null` when there is no meal. |
| `hydration_ml` | Sum of drinks that started that day, rounded to a whole milliliter. |
| `sources` | Sorted unique package names. Empty array when nothing contributed. |

Minutes are rounded half-up from the summed duration of each bucket, not truncated per stage.

分はステージごとの切り捨てではなく、バケツごとに合計した秒を四捨五入します。
