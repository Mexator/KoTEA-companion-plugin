plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "org.example"
version = "1.0.0"

repositories {
    maven("https://artifactory.tcsbank.ru/artifactory/maven-proxy")
    maven("https://artifactory.tcsbank.ru/artifactory/maven-all/")
    maven("https://artifactory.tcsbank.ru/artifactory/maven-intellij-dependencies-proxy/")
    maven("https://nexus.tcsbank.ru/repository/mvn-intellij-plugins-proxy/")
    maven("https://nexus.tcsbank.ru/repository/mvn-intellij-repository/")
    intellijPlatform {
        androidStudioInstallers {
            url = uri("https://artifactory.tcsbank.ru/artifactory/raw-android-studio-proxy")
        }
        releases {
            url = uri("https://nexus.tcsbank.ru/repository/mvn-intellij-repository/release")
        }
        localPlatformArtifacts()
        intellijDependencies()
        marketplace()
    }
}

dependencies {
    intellijPlatform {
        androidStudio("2025.3.1.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.android")
    }

    dependencies {
        implementation("com.posthog.java:posthog:1.2.0")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    runIde {
        jvmArgs("-Xmx20g")
    }
}