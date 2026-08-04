rootProject.name = "CloudstreamPlugins"

// Automatically include any subdirectory that contains a build.gradle.kts file
rootDir.listFiles()?.forEach { dir ->
    if (dir.isDirectory && File(dir, "build.gradle.kts").exists()) {
        val name = dir.name
        if (name != "Movix" && name != "LayarKaca" && name != "LayarKacaProvider") {
            include(name)
        }
    }
}
