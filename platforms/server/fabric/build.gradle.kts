import org.gradle.api.attributes.Usage

plugins {
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
    `maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

val fabric_api_version = providers.gradleProperty("fabric_api_version").get()
val yacl_version = providers.gradleProperty("yacl_version").get()

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven("https://maven.isxander.dev/releases") {
        name = "Xander Maven"
    }
}

// Dependencies added here are bundled into the mod jar (with their full transitive
// dependency graph) via the Shadow plugin. Loom's own `include` (jar-in-jar) mechanism
// is intentionally NOT used for this, because it does not resolve transitive
// dependencies: it would only bundle :common itself, not okhttp/jackson/etc. that
// :common depends on, which is exactly what was causing the runtime crash.
val shade: Configuration = configurations.create("shade") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
}

dependencies {
    implementation(project(":common"))
    shade(project(":common"))

    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

    // Fabric API.
    implementation("net.fabricmc.fabric-api:fabric-api:${fabric_api_version}")

    // Yet Another Config Library.
    implementation("dev.isxander:yet-another-config-lib:${yacl_version}")
}

tasks.shadowJar {
    val projectName = "server-announce-fabric"
    inputs.property("projectName", projectName)
    archiveBaseName.set(projectName)
    // Empty classifier: this is the primary, "real" artifact that gets installed on a
    // server, so it should have the normal file name (no "-shadow"/"-all" suffix).
    archiveClassifier.set("")

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }

    // Only merge in :common and its own third-party dependencies (okhttp, jackson, ...).
    // Without this, shadowJar would default to the whole runtime classpath and also
    // bundle Minecraft, Fabric Loader, Fabric API and YACL into the mod jar.
    configurations = listOf(shade)

    // Avoid duplicate/broken entries when merging multiple library jars together.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()

    // Relocate the bundled libraries so they can't collide with a different version of
    // the same library bundled by some other mod on the same server/classpath.
    relocate("okhttp3", "me.samuelh2005.server_announce.shadow.okhttp3")
    relocate("okio", "me.samuelh2005.server_announce.shadow.okio")
    relocate("tools.jackson", "me.samuelh2005.server_announce.shadow.tools.jackson")
    relocate("com.fasterxml.jackson", "me.samuelh2005.server_announce.shadow.com.fasterxml.jackson")
}

// Minecraft 26.2 is unobfuscated, so Loom runs in "non-remapping" mode here: there is
// no separate remap step, and the plain `jar` task's output *is* the final mod jar
// (no `remapJar` task exists to redirect, unlike on older, obfuscated Minecraft
// versions). So instead of pointing a remapJar task at the shaded jar, shadowJar
// itself is configured (below, alongside the plain jar task) to be the artifact that
// actually ships, containing :common's classes plus its bundled dependencies.
tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val version = version
    val fabric_api_version = fabric_api_version
    val yacl_version = yacl_version

    inputs.property("version", version)
    inputs.property("fabric_api_version", fabric_api_version)
    inputs.property("yacl_version", yacl_version)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to version,
                "fabric_api_version" to fabric_api_version,
                "yacl_version" to yacl_version
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    val projectName = "server-announce-fabric"
    inputs.property("projectName", projectName)
    archiveBaseName.set(projectName)
    // shadowJar (below) is the primary artifact that actually gets shipped/installed,
    // since it's the one that bundles :common and its dependencies. This plain jar
    // (just the mod's own compiled classes, no dependencies) is kept around under a
    // distinct classifier purely so it doesn't collide with shadowJar's output file.
    archiveClassifier.set("slim")

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

tasks.named<Jar>("sourcesJar") {
    val projectName = "server-announce-fabric"
    inputs.property("projectName", projectName)
    archiveBaseName.set(projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

// configure the maven publication
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            // Publish the shaded jar (contains :common + its dependencies), not the
            // plain "java" component, which would only be the dependency-free jar.
            artifact(tasks.shadowJar)
            artifact(tasks.named("sourcesJar"))
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
