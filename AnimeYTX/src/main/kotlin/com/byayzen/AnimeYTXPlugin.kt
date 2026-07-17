// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import com.seoulentertainment.app.extractors.FileMoon
import com.seoulentertainment.app.extractors.Gofile
import com.seoulentertainment.app.extractors.Mediafire
import com.seoulentertainment.app.extractors.OkRuSSL
import com.seoulentertainment.app.extractors.PixelDrain
import com.seoulentertainment.app.extractors.VidStack
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class AnimeYTXPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AnimeYTX())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Mediafire())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(FireLoad())
        registerExtractorAPI(Ytplay())
        registerExtractorAPI(VidStack())
        registerExtractorAPI(Mytsumi())
        registerExtractorAPI(BurstCloud())
    }
}