package com.cncverse

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Xon : Plugin() {
    override fun load(context: Context) {
        XonProvider.context = context
        val provider = XonProvider()
        registerMainAPI(provider)
    }
}
