package com.Donghuastream

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.Dailymotion
import com.seoulentertainment.app.extractors.Geodailymotion

@CloudstreamPlugin
class DonghuastreamProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Donghuastream())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Geodailymotion())
        registerExtractorAPI(Ultrahd())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(PlayStreamplay())
    }
}