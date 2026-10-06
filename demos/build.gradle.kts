// ReJact demo applications (Examples gallery, Wire, CounterExample).
dependencies {
    implementation(project(":rejact"))
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "me.chrommob.rejact.demo.Examples"
    }
}
