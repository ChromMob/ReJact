plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// ReJact v2 framework. Zero third-party dependencies.
//
// Spec pipeline:
//   spec/mdn (MDN browser-compat-data) + spec/overlay.json
//     -> SpecFromMdn -> build/generated/spec/elements.json
//     -> SpecGen     -> build/generated/rejact/{java,resources}
// Runtime: rejact-core.js + generated rejact-bindings.js -> /rejact-runtime.js on the classpath.

val htmlRef = layout.projectDirectory.file("spec/mdn/html.json")
val overlayFile = layout.projectDirectory.file("spec/overlay.json")
val coreJs = layout.projectDirectory.file("src/main/resources/rejact-core.js")

val generatedSpec = layout.buildDirectory.file("generated/spec/elements.json")
val genOut = layout.buildDirectory.dir("generated/rejact")
val genJava = genOut.map { it.dir("java") }
val genBindings = genOut.map { it.file("resources/rejact-bindings.js") }
val specgenClasses = layout.buildDirectory.dir("classes/java/specgen")
val runtimeJs = layout.buildDirectory.file("generated/runtime/rejact-runtime.js")

val compileSpecgen = tasks.register<JavaCompile>("compileSpecgen") {
    setSource(
        listOf(
            "src/main/java/me/chrommob/rejact/Json.java",
            "src/main/java/me/chrommob/rejactgen/SpecFromMdn.java",
            "src/main/java/me/chrommob/rejactgen/SpecGen.java",
        )
    )
    classpath = files()
    destinationDirectory.set(specgenClasses)
    options.encoding = "UTF-8"
    options.release.set(21)
}

val generateSpec = tasks.register<JavaExec>("generateSpec") {
    group = "build"
    description = "Generate the element/event spec from MDN browser-compat-data + overlay"
    dependsOn(compileSpecgen)
    classpath = files(specgenClasses)
    mainClass.set("me.chrommob.rejactgen.SpecFromMdn")
    inputs.file(htmlRef)
    inputs.file(overlayFile)
    outputs.file(generatedSpec)
    args(
        htmlRef.asFile.absolutePath,
        overlayFile.asFile.absolutePath,
        generatedSpec.get().asFile.absolutePath,
    )
}

val generateRejact = tasks.register<JavaExec>("generateRejact") {
    group = "build"
    description = "Generate typed tags/events and JS bindings from the MDN-derived spec"
    dependsOn(generateSpec)
    classpath = files(specgenClasses)
    mainClass.set("me.chrommob.rejactgen.SpecGen")
    inputs.file(generatedSpec)
    outputs.dir(genOut)
    args(
        generatedSpec.get().asFile.absolutePath,
        genOut.get().asFile.absolutePath,
    )
}

val assembleRuntime = tasks.register("assembleRuntime") {
    group = "build"
    description = "Assemble the fixed client runtime JS (core + generated bindings)"
    dependsOn(generateRejact)
    val out = runtimeJs
    val bindings = genBindings
    inputs.file(coreJs)
    inputs.file(bindings)
    outputs.file(out)
    doLast {
        val raw = coreJs.asFile.readText(Charsets.UTF_8) +
            "\n" +
            bindings.get().asFile.readText(Charsets.UTF_8)
        val outFile = out.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(raw, Charsets.UTF_8)
        logger.lifecycle("rejact-runtime.js ${outFile.length()} bytes")
    }
}

sourceSets {
    main {
        java.srcDir(genJava)
        java.exclude("me/chrommob/rejactgen/**")
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateRejact)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(assembleRuntime)
    from(runtimeJs)
    // rejact-core.js is a build input only; the shipped artifact is the assembled runtime.
    exclude("rejact-core.js")
}

// Sources and API docs must include the generated public tag/event classes.
tasks.named("sourcesJar") { dependsOn(generateRejact) }
tasks.javadoc {
    dependsOn(generateRejact)
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        isNoTimestamp = true
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from("spec/mdn/LICENSE") { into("META-INF"); rename { "LICENSE-MDN" } }
}
tasks.jar {
    manifest.attributes["Automatic-Module-Name"] = "me.chrommob.rejact"
    manifest.attributes["Implementation-Version"] = project.version
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "rejact"
            pom {
                name.set("ReJact")
                description.set("Server-rendered reactive web applications in Java")
                url.set("https://github.com/chrommob/rejact")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("chrommob")
                        name.set("ChromMob")
                        url.set("https://github.com/chrommob")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/chrommob/rejact.git")
                    developerConnection.set("scm:git:ssh://git@github.com/chrommob/rejact.git")
                    url.set("https://github.com/chrommob/rejact")
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
        providers.gradleProperty("publishUrl").orNull?.let { target ->
            maven {
                name = "release"
                url = uri(target)
                credentials {
                    username = providers.environmentVariable("MAVEN_USERNAME").orNull
                    password = providers.environmentVariable("MAVEN_PASSWORD").orNull
                }
            }
        }
    }
}

val signingKey = providers.environmentVariable("SIGNING_KEY")
signing {
    isRequired = providers.gradleProperty("requireSigning").map(String::toBoolean).getOrElse(false)
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("SIGNING_PASSWORD").orNull)
    }
    sign(publishing.publications["mavenJava"])
}

// Only this version's files belong in a Central Portal bundle; omit repository metadata.
tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Build a signed deployment bundle for the Maven Central Portal"
    dependsOn("check", "publishMavenJavaPublicationToStagingRepository")
    val releaseVersion = project.version.toString()
    val coordinatePath = "${project.group.toString().replace('.', '/')}/rejact/$releaseVersion"
    val staging = layout.buildDirectory.dir("staging-deploy")
    from(staging) { include("$coordinatePath/**") }
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("rejact-${project.version}-central.zip")
    doFirst {
        check(signingKey.isPresent) { "Set SIGNING_KEY to prepare a signed Central bundle" }
        check(!releaseVersion.endsWith("SNAPSHOT")) { "Central requires a releaseVersion without SNAPSHOT" }
        val directory = staging.get().dir(coordinatePath).asFile
        for (suffix in listOf(".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")) {
            val artifact = directory.resolve("rejact-$releaseVersion$suffix")
            check(artifact.isFile && artifact.resolveSibling(artifact.name + ".asc").isFile) {
                "Missing artifact or signature for ${artifact.name}; set SIGNING_KEY and SIGNING_PASSWORD"
            }
        }
    }
}
