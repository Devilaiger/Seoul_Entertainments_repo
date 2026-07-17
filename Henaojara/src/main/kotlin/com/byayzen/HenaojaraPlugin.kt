// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import com.seoulentertainment.app.extractors.FileMoon
import com.seoulentertainment.app.extractors.MixDrop
import com.seoulentertainment.app.extractors.Mp4Upload
import com.seoulentertainment.app.extractors.StreamWishExtractor
import com.seoulentertainment.app.extractors.Voe
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class HenaojaraPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Henaojara())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(Voe())
    }
}