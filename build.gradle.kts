plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.github.j4sp3rtm"
// Overridable from CI via -PpluginVersion=<x> (the release workflow derives it from the git tag).
version = (findProperty("pluginVersion") as String?) ?: "1.6.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1.1")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            // No upper bound: omit the until-build attribute so the plugin stays
            // compatible with 2025.1 and all later builds.
            untilBuild = provider { null }
        }
    }

    // Signing — required by JetBrains Marketplace. Secrets come from the environment,
    // never committed. See https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
    signing {
        certificateChainFile = providers.environmentVariable("CERTIFICATE_CHAIN_FILE")
            .map { file(it) }.orNull
        privateKeyFile = providers.environmentVariable("PRIVATE_KEY_FILE")
            .map { file(it) }.orNull
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD").orNull
    }

    // Plugin verification — runs the JetBrains Plugin Verifier (the Marketplace gate)
    // against the IDEs matched by the since/until build range.
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.1")
        }
    }

    // Publishing — `./gradlew publishPlugin` uploads the signed zip to the Marketplace.
    // PUBLISH_TOKEN is a personal access token from https://plugins.jetbrains.com/author/me/tokens
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN").orNull
        // Releases on "default"; pre-releases (e.g. 1.1.0-beta) auto-route to "beta".
        channels = listOf(
            if (version.toString().contains("-")) "beta" else "default"
        )
    }
}
intellijPlatformTesting {
    runIde {
        register("runWebStorm") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.WebStorm
            version = "2025.1.1"
        }
    }
}

// Wipes the sandbox `config` directory (settings + PropertiesComponent flags such as the
// onboarding "shown" flag) while keeping `system/` caches so startup stays fast. Lets you
// re-test first-run behavior repeatedly. Run with:  ./gradlew runIde -PfreshSandbox
tasks.register<Delete>("wipeSandboxConfig") {
    delete(
        fileTree(layout.buildDirectory.dir("idea-sandbox")) {
            include("**/config/**")
        }
    )
}
if (project.hasProperty("freshSandbox")) {
    tasks.named("runIde") { dependsOn("wipeSandboxConfig") }
}
