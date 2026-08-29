package com.seoulentertainment.bollyseoul

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BollySeoulPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BollySeoulProvider())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
    }
}
