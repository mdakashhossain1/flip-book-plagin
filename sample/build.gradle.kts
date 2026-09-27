plugins {
    alias(libs.plugins.android.application)
}

val appVersion = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt")).asText.get().trim()

android {
    namespace = "com.scoreplus.flipbook.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scoreplus.flipbook.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
        versionName = appVersion
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
