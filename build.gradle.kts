plugins {
    base
}

allprojects {
    group = providers.gradleProperty("releaseGroup").getOrElse("io.github.chrommob")
    version = providers.gradleProperty("releaseVersion").getOrElse("2.0.0-SNAPSHOT")
}

subprojects {
    apply(plugin = "java")
    repositories { mavenCentral() }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
