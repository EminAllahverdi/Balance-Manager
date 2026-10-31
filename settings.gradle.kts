pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "BalanceManager"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:data")
// include(":feature:home") // added as the module lands
// include(":feature:transaction") // added as the module lands
// include(":feature:history") // added as the module lands
// include(":feature:accounts") // added as the module lands
// include(":feature:catalog") // added as the module lands
// include(":feature:settings") // added as the module lands
// include(":widget") // added as the module lands
// include(":importer:moneymanager") // added as the module lands
