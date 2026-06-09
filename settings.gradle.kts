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

// F-Droid doesn't support foojay-resolver plugin
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


// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that musicfy and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
