import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.kotea.companion"
version = "1.2.2-PRE"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
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
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

intellijPlatformTesting {
    runIde {
        register("runLocalIde") {
            val androidStudioLocalPath = localProperties.getProperty("androidStudio.localPath")
                ?: error(
                    "runLocalIde requires 'androidStudio.localPath=/path/to/android-studio' " +
                            "to be set in local.properties (pointing at your local Android Studio install)."
                )
            localPath = file(androidStudioLocalPath)
            useInstaller = false
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
        }

        description = """
            <b>KoTEA Companion</b> is an Android Studio plugin designed to improve Developer Experience in projects using the KoTEA architectural pattern.
            The plugin simplifies navigation by creating a contextual bridge between the points of generation (Emission) and execution (Processing) of Events and Commands.
        """.trimIndent()

        changeNotes = """
            <ul>
                <li><b>1.2.2-PRE</b>
                    <ul>
                        <li>Rework Events and Commands discovery via Update classes</li>
                    </ul>
                </li>
            </ul>
        """.trimIndent()
    }
}

tasks {

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<RunIdeTask> {
        jvmArgs("-Xmx20g", "-Dandroid.sdk.analytics.disabled=true", "-Dstudio.ml.enabled=false")

        // Disable the bundled Gemini/AI plugin: on startup it calls AnalyticsSettings before it's
        // initialized, which crashes runLocalIde with "call to AnalyticsSettings before initialization".
        // "url-assistant" depends directly on it, so it must be disabled too, otherwise the IDE
        // logs it as a plugin that failed to load due to a missing/disabled dependency.
        doFirst {
            val disabledPluginsFile = sandboxConfigDirectory.get().file("disabled_plugins.txt").asFile
            disabledPluginsFile.parentFile.mkdirs()
            disabledPluginsFile.writeText(
                """
                com.google.tools.ij.aiplugin
                com.google.urlassistant
                """.trimIndent() + "\n"
            )
        }
    }
}
