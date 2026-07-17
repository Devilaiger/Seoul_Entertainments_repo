rootProject.name = "CloudstreamPlugins"

// Automatically include any subdirectory that contains a build.gradle.kts file
rootDir.listFiles()?.forEach { dir ->
    if (dir.isDirectory && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}
