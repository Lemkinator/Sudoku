plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.android.gms.oss-licenses-plugin")
}

val releaseStoreFile: String? by rootProject
val releaseStorePassword: String? by rootProject
val releaseKeyAlias: String? by rootProject
val releaseKeyPassword: String? by rootProject

android {
    namespace = "de.lemke.sudoku"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lemke.sudoku"
        minSdk = 26
        targetSdk = 36
        versionCode = 50
        versionName = "3.3.7"
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    @Suppress("UnstableApiUsage")
    androidResources.localeFilters += listOf("en", "de", "es", "es-rES")

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
            signingConfig = signingConfigs.getByName(if (releaseStoreFile.isNullOrEmpty()) "debug" else "release")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Sudoku (Debug)")
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    //SESL6(OneUI 6) Android Jetpack
    implementation("sesl.androidx.core:core:1.15.0+1.0.11-sesl6+rev0")
    implementation("sesl.androidx.core:core-ktx:1.15.0+1.0.0-sesl6+rev0")
    implementation("sesl.androidx.appcompat:appcompat:1.7.0+1.0.34-sesl6+rev8")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.4-sesl6+rev3")
    //SESL6(OneUI 6) Samsung
    implementation("sesl.androidx.picker:picker-basic:1.0.17+1.0.17-sesl6+rev2")
    //SESL6(OneUI 6) Material Components + Design Lib + Icons
    implementation("sesl.com.google.android.material:material:1.12.0+1.0.23-sesl6+rev3")
    implementation("io.github.tribalfs:oneui-design:0.5.13+oneui6")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("io.github.lemkinator:common-utils:0.8.2")

    implementation("de.sfuhrm:sudoku:5.0.1")
    implementation("io.kjson:kjson:9.7")
    implementation("net.pwall.json:json-kotlin-schema:0.56")
    implementation("androidx.asynclayoutinflater:asynclayoutinflater:1.1.0")
    implementation("com.airbnb.android:lottie:6.6.6")
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")

    implementation("androidx.core:core-splashscreen:1.2.0-beta02")
    //noinspection GradleDependency until https://issuetracker.google.com/u/0/issues/342671895 is fixed
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
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