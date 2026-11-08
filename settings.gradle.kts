pluginManagement {
    repositories {
        google()
        // Maven Central vía el espejo de Google (más estable desde redes con límite de tasa)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        maven("https://repo1.maven.org/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central vía el espejo de Google (más estable desde redes con límite de tasa)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        maven("https://repo1.maven.org/maven2/")
    }
}
rootProject.name = "KB-007"
include(":app")
