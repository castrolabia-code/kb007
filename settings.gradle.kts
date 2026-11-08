pluginManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2/") // Maven Central (espejo principal)
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://repo1.maven.org/maven2/") // Maven Central (espejo principal)
    }
}
rootProject.name = "KB-007"
include(":app")
