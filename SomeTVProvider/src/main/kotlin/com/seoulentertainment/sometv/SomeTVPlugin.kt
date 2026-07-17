package com.seoulentertainment.sometv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SomeTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SomeTVProvider())
    }
}

