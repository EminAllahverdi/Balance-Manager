import java.util.Properties

plugins {
    alias(libs.plugins.bm.android.application)
    alias(libs.plugins.bm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Never committed. CI writes its own from secrets, and without one everything is signed with the debug key so it still builds.
val keystore: Properties? = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
    ?.let { text -> Properties().apply { load(text.reader()) } }

// One number that only goes up, so a build always installs over an older one, whichever machine made it.
val commitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "com.eminallahverdi.balance"

    defaultConfig {
        applicationId = "com.eminallahverdi.balance"
        versionCode = commitCount
        versionName = providers.gradleProperty("appVersion").getOrElse("1.0.0")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        keystore?.let { key ->
            create("cibim") {
                storeFile = rootProject.file(key.getProperty("storeFile"))
                storePassword = key.getProperty("storePassword")
                keyAlias = key.getProperty("keyAlias")
                keyPassword = key.getProperty("keyPassword")
            }
        }
    }
    val signing = signingConfigs.findByName("cibim") ?: signingConfigs.getByName("debug")

    flavorDimensions += "environment"
    productFlavors {
        // The app to try things in, beside the real one, with its own name, icon and data.
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev.$commitCount"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
        debug {
            // The same key as release, so a build from the IDE and one from CI can replace each other, and Drive knows both.
            signingConfig = signing
        }
    }
}

androidComponents {
    // Prod is only ever a real release: this is the build that holds the real data.
    beforeVariants(selector().withFlavor("environment" to "prod").withBuildType("debug")) { it.enable = false }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:backup"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:home"))
    implementation(project(":feature:transaction"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:recurring"))
    implementation(project(":feature:loans"))
    implementation(project(":feature:confirm"))
    implementation(project(":widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime.ktx)
}
