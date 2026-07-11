plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// Gradle's Windows test worker misreads non-ASCII classpath entries from an
// argument file. Keep generated classes in an ASCII-safe location by default.
val lingqiBuildRoot = file(
    System.getenv("LINGQI_BUILD_ROOT")
        ?: "${System.getProperty("user.home")}/.lingqi-build"
)

layout.buildDirectory.set(lingqiBuildRoot.resolve("root"))
subprojects {
    layout.buildDirectory.set(lingqiBuildRoot.resolve(name))
}
