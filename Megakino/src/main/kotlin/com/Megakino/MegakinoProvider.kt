package com.Megakino

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.Voe

@CloudstreamPlugin
class MegakinoProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Megakino())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Gxplayer())
    }
}