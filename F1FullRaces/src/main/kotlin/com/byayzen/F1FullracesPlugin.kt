// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import android.content.Context
import com.seoulentertainment.app.extractors.Gofile
import com.seoulentertainment.app.extractors.MixDrop
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class F1FullracesPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(F1Fullraces())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(com.byayzen.MixDrop())
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropCh())
        registerExtractorAPI(MixDropTo())
        registerExtractorAPI(MixDrop977())
    }
}