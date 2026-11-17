import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: the state machine has no Android dependency, so it is tested on the plain JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // ./gradlew :hvac-core:test -DupdateDocs=true regenerates docs/hvac-state-machine.puml
    systemProperty("updateDocs", System.getProperty("updateDocs") ?: "false")
}
