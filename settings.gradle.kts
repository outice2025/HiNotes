pluginManagement {
    repositories {
        // dl.google.com serves the Google Maven repository directly, and these mirrors serve
        // Maven Central; both were verified reachable from the build host. The canonical
        // hosts are kept as fallbacks.
        maven {
            name = "GoogleMaven"
            url = uri("https://dl.google.com/dl/android/maven2/")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven {
            name = "AliyunPublic"
            url = uri("https://maven.aliyun.com/repository/public/")
        }
        maven {
            name = "HuaweiPublic"
            url = uri("https://repo.huaweicloud.com/repository/maven/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "GoogleMaven"
            url = uri("https://dl.google.com/dl/android/maven2/")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven {
            name = "AliyunPublic"
            url = uri("https://maven.aliyun.com/repository/public/")
        }
        maven {
            name = "HuaweiPublic"
            url = uri("https://repo.huaweicloud.com/repository/maven/")
        }
        mavenCentral()
    }
}

rootProject.name = "HiNotes"
include(":app")
