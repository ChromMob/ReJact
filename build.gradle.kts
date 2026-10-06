plugins {
    java
}

group = "me.chrommob"
version = "1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
    }

    // Cross-compile to Java 17 bytecode on whatever JDK the build runs on.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
