@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// f-droid foojay workaround
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "musicfy"
include(":app")
include(":providers:canvas")
include(":providers:innertube")
include(":providers:kugou")
include(":providers:lrclib")
include(":providers:kizzy")
include(":providers:lastfm")
include(":providers:betterlyrics")
include(":providers:simpmusic")
include(":providers:youlyplus")
include(":providers:shazamkit")
include(":providers:artistvideo")
include(":providers:applecanvas")
include(":providers:paxsenixlyrics")


// local newpipe extractor
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
