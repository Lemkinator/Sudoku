plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    //id("com.google.devtools.ksp")
}

val releaseStoreFile: String? by rootProject
val releaseStorePassword: String? by rootProject
val releaseKeyAlias: String? by rootProject
val releaseKeyPassword: String? by rootProject

android {
    namespace = "de.lemke.sudoku"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.lemke.sudoku"
        minSdk = 26
        targetSdk = 34
        versionCode = 32
        versionName = "3.0.9"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
        //ksp {arg("room.schemaLocation", "$projectDir/schemas") }

        resourceConfigurations += listOf("en", "de", "es", "es-rES")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseStoreFile?.also {
                storeFile = rootProject.file(it)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        all {
            signingConfig =
                if (releaseStoreFile.isNullOrEmpty()) {
                    signingConfigs.getByName("debug")
                } else {
                    signingConfigs.getByName("release")
                }
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

configurations.configureEach {
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.core", "core")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.recyclerview", "recyclerview")
}

dependencies {
    implementation("io.github.oneuiproject:design:1.2.6")
    implementation("io.github.oneuiproject.sesl:appcompat:1.4.0")
    implementation("io.github.oneuiproject.sesl:material:1.5.0")
    implementation("io.github.oneuiproject.sesl:preference:1.1.0")
    implementation("io.github.oneuiproject.sesl:recyclerview:1.4.1")
    implementation("io.github.oneuiproject.sesl:picker-basic:1.2.0")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("de.sfuhrm:sudoku:5.0.1")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("io.kjson:kjson:7.6")
    implementation("net.pwall.json:json-kotlin-schema:0.47")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.gms:play-services-games-v2:20.0.0")
    implementation("com.google.android.play:core:1.10.3")
    implementation("com.google.android.play:core-ktx:1.8.1")
    implementation("androidx.asynclayoutinflater:asynclayoutinflater:1.0.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    //noinspection KaptUsageInsteadOfKsp
    kapt("androidx.room:room-compiler:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.9.0")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")

    //noinspection GradleDependency
    implementation("com.google.dagger:hilt-android:2.42")
    //noinspection GradleDependency
    kapt("com.google.dagger:hilt-compiler:2.42")
}