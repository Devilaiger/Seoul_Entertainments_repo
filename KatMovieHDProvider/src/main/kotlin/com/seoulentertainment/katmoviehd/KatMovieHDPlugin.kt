package com.seoulentertainment.katmoviehd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KatMovieHDPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KatMovieHDProvider())
    }
}
