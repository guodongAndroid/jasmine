import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.guodong.android.jasmine"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(
        variant = "release",
        sourcesJar = true,
        publishJavadocJar = true
    ))

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("com.sunxiaodou.android", "jasmine", libs.versions.jasminePublish.get())

    pom {
        name.set("Jasmine")
        description.set("A lightweight Netty-based MQTT Broker for Android developed using Kotlin.")
        inceptionYear.set("2025")
        url.set("https://github.com/guodongAndroid/jasmine")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("guodongAndroid")
                name.set("guodongAndroid")
                url.set("https://github.com/guodongAndroid/")
            }
        }

        scm {
            url.set("https://github.com/guodongAndroid/jasmine")
            connection.set("scm:git:git://github.com/guodongAndroid/jasmine.git")
            developerConnection.set("scm:git:ssh://git@github.com/guodongAndroid/jasmine.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "Local"
            url = rootProject.uri("repo")
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)

    implementation(libs.netty.common)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.codec.mqtt)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)

    testImplementation(libs.junit)
}