import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.kotea.companion"
version = "1.4.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html
        androidStudio("2025.3.1.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        // for MavenDependencyUtil
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.android")
    }

    testImplementation("junit:junit:4.13.2")
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

        changeNotes = """
            <ul>
                <li><b>1.4.1</b>
                    <ul>
                        <li>Fix: Library types such as Unit or Any are no longer treated as Events, Commands or News</li>
                        <li>Fix: Constructing an Event, Command or News is always an emission, even inside an Update class</li>
                        <li>Fix: Checking against an Event or News (is, as, ==, when branches) is a processing site, wherever it is</li>
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
