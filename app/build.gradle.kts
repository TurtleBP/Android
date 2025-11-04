plugins {
    id("com.android.application")
    // Nếu project có code Kotlin, thêm: id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.pro.milkteaapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pro.milkteaapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Dùng Java 17 đồng nhất (AGP 8.x hỗ trợ)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ---- Android UI / Jetpack ----
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Navigation
    implementation("androidx.navigation:navigation-fragment:2.9.5")
    implementation("androidx.navigation:navigation-ui:2.9.5")

    // ---- Firebase (quản lý phiên bản qua BoM) ----
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // optional nếu dùng analytics:
    implementation("com.google.firebase:firebase-analytics")

    // ---- Bên thứ 3 ----
    // Glide
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("com.google.firebase:firebase-storage:22.0.1")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.5")

    // Lottie
    implementation("com.airbnb.android:lottie:6.7.1")

    // CircleImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Gson
    implementation("com.google.code.gson:gson:2.13.2")

    // ---- Test ----
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
