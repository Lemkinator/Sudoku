plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.android.gms.oss-licenses-plugin")
}

fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
fun getProperty(key: String): String? = rootProject.findProperty(key)?.toString() ?: System.getenv(key.toEnvVarStyle())

android {
    namespace = "de.lemke.sudoku"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lemke.sudoku"
        minSdk = 26
        targetSdk = 36
        versionCode = 54
        versionName = "3.4.3"
    }

    @Suppress("UnstableApiUsage")
    androidResources.localeFilters += listOf("en", "de", "es", "es-rES")

    signingConfigs {
        create("release") {
            getProperty("releaseStoreFile").apply {
                if (isNullOrEmpty()) {
                    logger.warn("Release signing configuration not found. Using debug signing config.")
                } else {
                    logger.lifecycle("Using release signing configuration from: $this")
                    storeFile = rootProject.file(this)
                    storePassword = getProperty("releaseStorePassword")
                    keyAlias = getProperty("releaseKeyAlias")
                    keyPassword = getProperty("releaseKeyPassword")
                }
            }
        }
    }

    buildTypes {
        all {
            signingConfig = signingConfigs.getByName(if (getProperty("releaseStoreFile").isNullOrEmpty()) "debug" else "release")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Sudoku (Debug)")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    //SESL Android Jetpack
    implementation("sesl.androidx.core:core:1.16.0+1.0.15-sesl7+rev0")
    implementation("sesl.androidx.core:core-ktx:1.16.0+1.0.0-sesl7+rev0")
    implementation("sesl.androidx.appcompat:appcompat:1.7.1+1.0.47000-sesl7+rev0")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.12-sesl7+rev0")
    implementation("sesl.androidx.picker:picker-basic:1.0.16+1.0.16-sesl7+rev0")
    //SESL Material Components + Design Lib + Icons
    implementation("sesl.com.google.android.material:material:1.12.0+1.0.39-sesl7+rev5")
    implementation("io.github.tribalfs:oneui-design:0.6.6+oneui7")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("io.github.lemkinator:common-utils:0.8.16")

    implementation("de.sfuhrm:sudoku:5.0.3")
    implementation("io.kjson:kjson:9.9")
    implementation("net.pwall.json:json-kotlin-schema:0.56")
    implementation("androidx.asynclayoutinflater:asynclayoutinflater:1.1.0")
    implementation("com.airbnb.android:lottie:6.6.7")
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")

    implementation("androidx.core:core-splashscreen:1.2.0-beta02")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")
}

configurations.implementation {
    //Exclude official android jetpack modules
    exclude("androidx.core", "core")
    exclude("androidx.core", "core-ktx")
    exclude("androidx.customview", "customview")
    exclude("androidx.coordinatorlayout", "coordinatorlayout")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager2", "viewpager2")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.preference", "preference")
    exclude("androidx.recyclerview", "recyclerview")
    exclude("androidx.slidingpanelayout", "slidingpanelayout")
    exclude("androidx.swiperefreshlayout", "swiperefreshlayout")

    //Exclude official material components lib
    exclude("com.google.android.material", "material")
}