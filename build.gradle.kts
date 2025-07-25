// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Update the Kotlin version here
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")  // Update to the latest Kotlin version
    }
}