package com.phisher98

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.FileMoon
import com.seoulentertainment.app.extractors.FilemoonV2
import com.seoulentertainment.app.extractors.Krakenfiles
import com.seoulentertainment.app.extractors.StreamTape
import com.seoulentertainment.app.extractors.Voe

@CloudstreamPlugin
class AnimeDekhoPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeDekhoProvider())
        //registerMainAPI(OnepaceProvider())
        registerMainAPI(HindiSubAnime())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Vidmolynet())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Techinmind())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FileMoonNL())
        registerExtractorAPI(Krakenfiles())
        registerExtractorAPI(Voe())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(Cloudy())
        registerExtractorAPI(vidcloudupns())
        registerExtractorAPI(Animedekhoco())
        registerExtractorAPI(Blakiteapi())
        registerExtractorAPI(ascdn21())
        registerExtractorAPI(Abyass())
    }
}
