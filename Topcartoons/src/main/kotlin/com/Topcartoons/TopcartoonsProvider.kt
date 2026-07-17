package com.Topcartoons

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TopcartoonsProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Topcartoons())
    }
}