package com.anidb

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AniDbPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AniDb())
    }
}
