pluginManagement {
    repositories {
        // maven.google.com 在某些网络环境下不可达，优先使用 dl.google.com 官方镜像
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        google()
        mavenCentral()
    }
}
rootProject.name = "GhAndroid"
include(":app")
