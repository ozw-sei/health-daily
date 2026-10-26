plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionName / -PversionCode (or VERSION_NAME / VERSION_CODE).
// Local builds keep these defaults; do not edit them per release.
fun propOrEnv(vararg names: String): String? {
    for (name in names) {
        val fromProperty = providers.gradleProperty(name).orNull
        if (fromProperty != null) {
            val value = fromProperty.trim()
            require(value.isNotEmpty()) { "$name is set but blank" }
            return value
        }
        val fromEnv = providers.environmentVariable(name).orNull
        if (fromEnv != null) {
            val value = fromEnv.trim()
            require(value.isNotEmpty()) { "$name is set but blank" }
            return value
        }
    }
    return null
}

val releaseVersionName = propOrEnv("versionName", "VERSION_NAME") ?: "1.0.0"
val releaseVersionCodeRaw = propOrEnv("versionCode", "VERSION_CODE") ?: "1"
val releaseVersionCode = releaseVersionCodeRaw.toIntOrNull()
    ?: error("versionCode must be an integer, got '$releaseVersionCodeRaw'")
require(releaseVersionCode > 0) { "versionCode must be positive, got $releaseVersionCode" }

// Release signing is applied only when every value is present (env or -P).
// CI decodes ANDROID_KEYSTORE_BASE64 to ANDROID_KEYSTORE_PATH under RUNNER_TEMP.
// Absent values leave the release build unsigned, not signed with the debug key.
val keystorePath = propOrEnv("ANDROID_KEYSTORE_PATH")
val keystorePassword = propOrEnv("ANDROID_KEYSTORE_PASSWORD")
val keyAlias = propOrEnv("ANDROID_KEY_ALIAS")
val keyPassword = propOrEnv("ANDROID_KEY_PASSWORD")
val signingParts = listOf(keystorePath, keystorePassword, keyAlias, keyPassword)
val releaseSigning =
    when (signingParts.count { it != null }) {
        0 -> null
        signingParts.size ->
            ReleaseSigning(
                path = keystorePath!!,
                storePassword = keystorePassword!!,
                alias = keyAlias!!,
                keyPassword = keyPassword!!,
            )
        else ->
            error(
                "Release signing is incomplete. Set ANDROID_KEYSTORE_PATH, " +
                    "ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and " +
                    "ANDROID_KEY_PASSWORD together, as environment variables or Gradle properties.",
            )
    }
val releaseStoreFile = releaseSigning?.let { file(it.path) }

android {
    namespace = "jp.healthsync.daily"
    // Platform android-37.2 is the newest stable SDK. Compose 1.12 requires compileSdk 37+.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "jp.healthsync.daily"
        minSdk = 28
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signing.storePassword
                keyAlias = signing.alias
                keyPassword = signing.keyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = releaseSigning?.let { signingConfigs.getByName("release") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}

class ReleaseSigning(
    val path: String,
    val storePassword: String,
    val alias: String,
    val keyPassword: String,
)
