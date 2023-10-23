@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    compileSdkVersion(33)

    defaultConfig {
        applicationId = "chat.simplex.app"
        minSdkVersion(26)
        targetSdkVersion(33)
        // !!!
        // skip version code after release to F-Droid, as it uses two version codes
        versionCode = (extra["android.version_code"] as String).toInt()
        versionName = extra["android.version_name"] as String

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
        manifestPlaceholders["app_name"] = "@string/app_name"
        manifestPlaceholders["provider_authorities"] = "chat.simplex.app.provider"
        manifestPlaceholders["extract_native_libs"] = rootProject.extra["compression.level"] as Int != 0
    }

    buildTypes {
        debug {
            applicationIdSuffix = rootProject.extra["application_id.suffix"] as String
            isDebuggable = rootProject.extra["enable_debuggable"] as Boolean
            manifestPlaceholders["app_name"] = rootProject.extra["app.name"] as String
            // Provider can"t be the same for different apps on the same device
            manifestPlaceholders["provider_authorities"] = "chat.simplex.app${rootProject.extra["application_id.suffix"]}.provider"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility =  JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        freeCompilerArgs += "-opt-in=androidx.compose.ui.text.ExperimentalTextApi"
        freeCompilerArgs += "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        freeCompilerArgs += "-opt-in=com.google.accompanist.insets.ExperimentalAnimatedInsets"
        freeCompilerArgs += "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.InternalSerializationApi"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
    externalNativeBuild {
        cmake {
            path(File("../common/src/commonMain/cpp/android/CMakeLists.txt"))
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = rootProject.extra["compression.level"] as Int != 0
    }
    android.sourceSets["main"].assets.setSrcDirs(listOf("../common/src/commonMain/resources/assets"))
    val isRelease = gradle.startParameter.taskNames.find { it.toLowerCase().contains("release") } != null
    val isBundle = gradle.startParameter.taskNames.find { it.toLowerCase().contains("bundle") } != null
    //    if (isRelease) {
    // Comma separated list of languages that will be included in the apk
    android.defaultConfig.resConfigs(
        "en",
        "ar",
        "bg",
        "cs",
        "de",
        "es",
        "fi",
        "fr",
        "it",
        "iw",
        "ja",
        "nl",
        "pl",
        "pt-rBR",
        "ru",
        "th",
        "uk",
        "zh-rCN"
    )
    //    }
    if (isBundle) {
        defaultConfig.ndk.abiFilters("arm64-v8a", "armeabi-v7a")
    } else {
        splits {
            abi {
                isEnable = true
                reset()
                if (isRelease) {
                    include("arm64-v8a", "armeabi-v7a")
                } else {
                    include("arm64-v8a", "armeabi-v7a")
                    isUniversalApk = false
                }
            }
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.core:core-ktx:1.7.0")
    //implementation("androidx.compose.ui:ui:${rootProject.extra["compose.version"] as String}")
    //implementation("androidx.compose.material:material:$compose_version")
    //implementation("androidx.compose.ui:ui-tooling-preview:$compose_version")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    implementation("androidx.lifecycle:lifecycle-process:2.4.1")
    implementation("androidx.activity:activity-compose:1.5.0")
    val work_version = "2.7.1"
    implementation("androidx.work:work-runtime-ktx:$work_version")
    implementation("androidx.work:work-multiprocess:$work_version")

    implementation("com.jakewharton:process-phoenix:2.1.2")

    //implementation("androidx.compose.material:material-icons-extended:$compose_version")
    //implementation("androidx.compose.ui:ui-util:$compose_version")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    //androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
    debugImplementation("androidx.compose.ui:ui-tooling:${rootProject.extra["compose.version"] as String}")
}

tasks {
    val compressApk by creating {
        doLast {
            val isRelease = gradle.startParameter.taskNames.find { it.toLowerCase().contains("release") } != null
            val buildType: String = if (isRelease) "release" else "debug"
            val javaHome = System.getProperties()["java.home"] ?: org.gradle.internal.jvm.Jvm.current().javaHome
            val sdkDir = android.sdkDirectory.absolutePath
            var keyAlias = ""
            var keyPassword = ""
            var storeFile = ""
            var storePassword = ""
            if (project.properties["android.injected.signing.key.alias"] != null) {
                keyAlias = project.properties["android.injected.signing.key.alias"] as String
                keyPassword = project.properties["android.injected.signing.key.password"] as String
                storeFile = project.properties["android.injected.signing.store.file"] as String
                storePassword = project.properties["android.injected.signing.store.password"] as String
            } else {
                try {
                    val gradleConfig = android.signingConfigs.getByName(buildType)
                    keyAlias = gradleConfig.keyAlias!!
                    keyPassword = gradleConfig.keyPassword!!
                    storeFile = gradleConfig.storeFile!!.absolutePath
                    storePassword = gradleConfig.storePassword!!
                } catch (e: UnknownDomainObjectException) {
                    // There is no signing config for current build type, can"t sign the apk
                    println("No signing configs for this build type: $buildType")
                    return@doLast
                }
            }
            lateinit var outputDir: File
            named(if (isRelease) "packageRelease" else "packageDebug") {
                outputDir = outputs.files.files.last()
            }
            exec {
                workingDir("../../../scripts/android")
                setEnvironment(mapOf("JAVA_HOME" to "$javaHome"))
                commandLine = listOf(
                    "./compress-and-sign-apk.sh",
                    "${rootProject.extra["compression.level"]}",
                    "$outputDir",
                    "$sdkDir",
                    "$storeFile",
                    "$storePassword",
                    "$keyAlias",
                    "$keyPassword"
                )
            }

            if (project.properties["android.injected.signing.key.alias"] != null && buildType == "release") {
                File(outputDir, "android-release.apk").renameTo(File(outputDir, "simplex.apk"))
                File(outputDir, "android-armeabi-v7a-release.apk").renameTo(File(outputDir, "simplex-armv7a.apk"))
                File(outputDir, "android-arm64-v8a-release.apk").renameTo(File(outputDir, "simplex.apk"))
            }
            // View all gradle properties set
            // project.properties.each { k, v -> println "$k -> $v" }
        }
    }

    // Don"t do anything if no compression is needed
    if (rootProject.extra["compression.level"] as Int != 0) {
        whenTaskAdded {
            if (name == "packageDebug") {
                finalizedBy(compressApk)
            } else if (name == "packageRelease") {
                finalizedBy(compressApk)
            }
        }
    }
}
