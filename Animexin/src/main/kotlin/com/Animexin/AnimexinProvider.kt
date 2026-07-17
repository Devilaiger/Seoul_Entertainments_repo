package com.Animexin

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.Dailymotion

@CloudstreamPlugin
class AnimexinProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Animexin())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Dailymotion())
    }
}