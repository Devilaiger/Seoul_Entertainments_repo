package com.Animekhor

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.Dailymotion
import com.seoulentertainment.app.extractors.EmturbovidExtractor
import com.seoulentertainment.app.extractors.Mp4Upload

@CloudstreamPlugin
class AnimenosubProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Animekhor())
        registerMainAPI(Donghuaword())
        registerExtractorAPI(embedwish())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(PlayerDonghuaworld())
        registerExtractorAPI(P2pstream())
        registerExtractorAPI(Donghuaplanet())
    }
}