package com.anikage

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.BasePlugin

@CloudstreamPlugin
class AnikagePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AnikageProvider())
    }
}
