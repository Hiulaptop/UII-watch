plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release version lives here and nowhere else. versionCode must rise with every upload to Google Play, so it is
// derived: major * 1_000_000 + minor * 10_000 + patch * 100 + build. A second upload of the same version is
// `-Puii.build=1` (up to 99); 0.0.1 is code 100, 0.1.0 is 10_000, 1.0.0 is 1_000_000.
val appVersionName = "0.0.1"
val appBuild = ((project.findProperty("uii.build") as String?) ?: "0").toInt()
require(appBuild in 0..99) { "uii.build must be between 0 and 99 (got $appBuild)" }
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 1_000_000 + minor * 10_000 + patch * 100
} + appBuild

// Debug builds only: point the emulator at a local server (-Puii.apiBaseUrl=http://10.0.2.2:3000) and shorten or lengthen
// the "I'm fine" window for manual testing (-Puii.fallWindowMs=90000). Release builds ignore both on purpose.
val debugApiBaseUrl = (project.findProperty("uii.apiBaseUrl") as String?) ?: "https://uii.studev.net"
val debugFallWindowMs = (project.findProperty("uii.fallWindowMs") as String?) ?: "20000"

// Release talks to production over HTTPS and nothing else. -Puii.releaseApiBaseUrl exists for a future staging host
// and is refused unless it is https.
val releaseApiBaseUrl = (project.findProperty("uii.releaseApiBaseUrl") as String?) ?: "https://uii.studev.net"
require(releaseApiBaseUrl.startsWith("https://")) { "uii.releaseApiBaseUrl must start with https:// (got a non-HTTPS URL)" }
val RELEASE_FALL_WINDOW_MS = "20000"

// Signing material lives outside the repository: user-level gradle.properties or environment variables.
fun signingSetting(name: String, env: String) =
    providers.gradleProperty("uii.$name").orElse(providers.environmentVariable(env)).orNull
val keystoreFile = signingSetting("keystoreFile", "UII_KEYSTORE_FILE")
val keystorePassword = signingSetting("keystorePassword", "UII_KEYSTORE_PASSWORD")
val signingKeyAlias = signingSetting("keyAlias", "UII_KEY_ALIAS")
val signingKeyPassword = signingSetting("keyPassword", "UII_KEY_PASSWORD")
val missingSigning = listOf(
    "uii.keystoreFile / UII_KEYSTORE_FILE" to keystoreFile,
    "uii.keystorePassword / UII_KEYSTORE_PASSWORD" to keystorePassword,
    "uii.keyAlias / UII_KEY_ALIAS" to signingKeyAlias,
    "uii.keyPassword / UII_KEY_PASSWORD" to signingKeyPassword,
).filter { it.second.isNullOrBlank() }.map { it.first }

android {
    namespace = "com.example.watchtest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "net.studev.uii"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (missingSigning.isEmpty()) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            buildConfigField("long", "FALL_CANCEL_WINDOW_MS", "${debugFallWindowMs}L")
            // Plain HTTP is allowed in debug builds only, so the emulator can reach a local server.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            buildConfigField("long", "FALL_CANCEL_WINDOW_MS", "${RELEASE_FALL_WINDOW_MS}L")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            if (missingSigning.isEmpty()) signingConfig = signingConfigs.getByName("release")
            // Shrinking takes the release from tens of MB to a few; the keep rules are in src/main/keepRules.
            optimization {
                enable = true
            }
        }
    }
    // Test aid: the release build's shrinking, but debug-signed and pointed at a local server, so the shrunk code can be
    // exercised end to end (the real release only talks to the production https host). Never distributed.
    buildTypes.create("shrunk") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
        isDebuggable = true   // the app only allows plain http to a local server in debuggable builds
        buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        buildConfigField("long", "FALL_CANCEL_WINDOW_MS", "${debugFallWindowMs}L")
        manifestPlaceholders["usesCleartextTraffic"] = "true"
    }
    sourceSets.getByName("shrunk") {
        manifest.srcFile("src/debug/AndroidManifest.xml")
        java.directories.add("src/debug/java")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.health:health-services-client:1.1.0")
    implementation("androidx.work:work-runtime:2.12.0")
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.wear.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    // The Android SDK ships only stubs of org.json; unit tests on the JVM need the real implementation.
    testImplementation("org.json:json:20250517")
}

// A release must never be debug-signed or left unsigned by accident: fail with the names of what is missing.
val requireReleaseSigning by tasks.registering {
    val missing = missingSigning
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException("Release signing is not configured. Missing: " + missing.joinToString("; ") +
                ". Put them in ~/.gradle/gradle.properties or the environment (see docs/install.md).")
        }
    }
}
tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach { dependsOn(requireReleaseSigning) }
