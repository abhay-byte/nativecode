pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FluxLinux"
include(":app")

// Termux embedded submodules
include(":modules:termux-app:terminal-emulator")
include(":modules:termux-app:terminal-view")
include(":modules:termux-app:termux-shared")

// Termux X11 display submodule
include(":modules:termux-x11:app")
include(":modules:termux-x11:shell-loader")
include(":modules:termux-x11:shell-loader:stub")



