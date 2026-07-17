package com.DoraBash

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.FileMoon

@CloudstreamPlugin
class DoraBashProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(DoraBash())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonIN())
        registerExtractorAPI(FileMoon())
    }
}