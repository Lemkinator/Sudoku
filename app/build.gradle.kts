plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    //id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
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
        versionCode = 29
        versionName = "3.0.6"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
        //ksp {arg("room.schemaLocation", "$projectDir/schemas") }

        resourceConfigurations += listOf("en", "de")

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
    }
}

configurations.configureEach {
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.fragment", module = "fragment")
}

dependencies {
    implementation("io.github.oneuiproject:design:1.2.6")
    implementation("io.github.oneuiproject.sesl:appcompat:1.4.0")
    implementation("io.github.oneuiproject.sesl:material:1.5.0")
    implementation("io.github.oneuiproject.sesl:preference:1.1.0")
    implementation("io.github.oneuiproject.sesl:recyclerview:1.4.0")
    implementation("io.github.oneuiproject.sesl:picker-basic:1.2.0")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("de.sfuhrm:sudoku:5.0.1")
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("io.kjson:kjson:6.1")
    implementation("net.pwall.json:json-kotlin-schema:0.40")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-animation:1.0.0-beta01")
    implementation("com.google.android.gms:play-services-games-v2:17.0.0")
    implementation("com.google.android.play:core-ktx:1.8.1")
    implementation("androidx.asynclayoutinflater:asynclayoutinflater:1.0.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    //noinspection KaptUsageInsteadOfKsp
    kapt("androidx.room:room-compiler:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    //kotlin 1.8 vs 1.7, kotlin-stdlib-jdk8 vs kotlin-stdlib-jdk7
    //https://github.com/facebook/react-native/issues/35979
    //https://kotlinlang.org/docs/whatsnew18.html#usage-of-the-latest-kotlin-stdlib-version-in-transitive-dependencies
    //https://stackoverflow.com/questions/67744002/hilt-unsupported-metadata-version-in-kotlin

    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.9.0")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")

    //noinspection GradleDependency
    implementation("com.google.dagger:hilt-android:2.42")
    //noinspection GradleDependency
    kapt("com.google.dagger:hilt-compiler:2.42")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}