plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.detekt).apply(false)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
