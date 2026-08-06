plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.kotea.companion"
version = "1.2.0-PRE"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        androidStudio("2025.3.1.1")
//        local(file("/home/anton/.local/share/JetBrains/Toolbox/apps/android-studio/"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.android")
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
                <li><b>1.2.0-PRE</b>
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

    runIde {
        jvmArgs("-Xmx20g", "-Dandroid.sdk.analytics.disabled=true", "-Dstudio.ml.enabled=false")
    }
}
