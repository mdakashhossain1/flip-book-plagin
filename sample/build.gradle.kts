plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.scoreplus.flipbook.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scoreplus.flipbook.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "pdf"
    }
}

dependencies {
    implementation(project(":flipbook"))
}
