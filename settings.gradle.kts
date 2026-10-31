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
include(":feature:home")
include(":feature:transaction")
include(":feature:history")
include(":feature:insights")
include(":feature:accounts")
include(":feature:catalog")
include(":feature:settings")
// include(":widget") // added as the module lands
// include(":importer:moneymanager") // added as the module lands
