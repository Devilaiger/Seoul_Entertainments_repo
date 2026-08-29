package com.seoulentertainment.animeseoul

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeSeoulPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeSeoulProvider())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
    }
}
