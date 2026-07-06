pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ServerAnnounce"

include(":common")
include(":platforms:server:fabric")
