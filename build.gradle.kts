plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "me.chrommob"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

//Set main class for fat jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "me.chrommob.Main"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.sksamuel.scrimage:scrimage-core:4.1.2")
    implementation("com.sksamuel.scrimage:scrimage-webp:4.1.2")    
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("io.javalin:javalin:6.1.3")
    implementation("org.slf4j:slf4j-simple:2.0.10")
    implementation("in.wilsonl.minifyhtml:minify-html:0.10.8")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}