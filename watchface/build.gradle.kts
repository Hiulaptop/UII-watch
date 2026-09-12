plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.watchtest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.watchtest"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    enableKotlin = false
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.wear:wear:1.3.0")
    testImplementation("junit:junit:4.13.2")
}
