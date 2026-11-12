import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
val releaseVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val signingValues = listOf("TRAKT_KEYSTORE_PATH","TRAKT_STORE_PASSWORD","TRAKT_KEY_ALIAS","TRAKT_KEY_PASSWORD").associateWith { System.getenv(it).orEmpty() }
val releaseReady = signingValues.values.all { it.isNotBlank() }
val debugKeyPath = System.getenv("TRAKT_DEBUG_KEYSTORE_PATH").orEmpty()
val debugKeyPassword = System.getenv("TRAKT_DEBUG_PASSWORD").orEmpty()
android {
 namespace = "com.traktbridge.app"
 compileSdk = 35
 buildFeatures { compose = true }
 defaultConfig {
    applicationId = "com.traktbridge.app"
    minSdk = 26
    targetSdk = 35
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = releaseVersion.getProperty("VERSION_CODE").toInt()
    versionName = releaseVersion.getProperty("VERSION_NAME")
 }
 if (debugKeyPath.isNotBlank() && debugKeyPassword.isNotBlank()) {
    signingConfigs.getByName("debug").apply {
        storeFile = file(debugKeyPath)
        storePassword = debugKeyPassword
        keyAlias = "traktbridge-debug"
        keyPassword = debugKeyPassword
    }
 }
 if (releaseReady) {
    signingConfigs {
        create("stableRelease") {
            storeFile = file(signingValues.getValue("TRAKT_KEYSTORE_PATH"))
            storePassword = signingValues.getValue("TRAKT_STORE_PASSWORD")
            keyAlias = signingValues.getValue("TRAKT_KEY_ALIAS")
            keyPassword = signingValues.getValue("TRAKT_KEY_PASSWORD")
        }
    }
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("stableRelease")
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.work:work-runtime-ktx:2.9.1")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:core:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
