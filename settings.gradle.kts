rootProject.name = "CloudstreamPlugins"

// Explicitly include only active and supported providers to keep the build clean and fast
val activePlugins = listOf(
    "CineStream",
    "HDrezkaProvider",
    "KatMovieHDProvider",
    "MovieLinkBDProvider",
    "MPlayerProvider"
)

activePlugins.forEach { include(it) }

