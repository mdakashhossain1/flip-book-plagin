import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "com.github.mdakashhossain1"
version = providers.gradleProperty("version").orElse("android-v0.2.2").get()

android {
    namespace = "com.scoreplus.flipbook"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "flip-book-plagin"
            version = project.version.toString()

            pom {
                name.set("ScorePlus Flipbook")
                description.set("Native Android PDF flipbook viewer with page-turn animation, zoom, and thumbnails.")
                url.set("https://github.com/mdakashhossain1/flip-book-plagin")
                scm {
                    url.set("https://github.com/mdakashhossain1/flip-book-plagin")
                    connection.set("scm:git:https://github.com/mdakashhossain1/flip-book-plagin.git")
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
