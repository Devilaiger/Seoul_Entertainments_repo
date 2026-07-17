version = 1

android {
    namespace = "com.seoulentertainment.sometv"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    authors = listOf("SeoulEntertainment")
    language = "en"
    description = "SomeTV Provider"
    tvTypes = listOf("movie", "series")
}
