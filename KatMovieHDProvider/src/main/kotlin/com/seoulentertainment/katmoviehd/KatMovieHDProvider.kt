package com.seoulentertainment.katmoviehd

import com.seoulentertainment.app.*
import com.seoulentertainment.app.utils.*
import org.jsoup.nodes.Element
import android.util.Log
import com.lagradost.nicehttp.NiceResponse
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.json.JSONObject
import org.json.JSONArray
import com.seoulentertainment.app.network.WebViewResolver

class KatMovieHDProvider : MainAPI() {
    companion object {
        private var resolvedUrl: String? = null
        private val domains = listOf(
            "https://katdrama.net",
            "https://katdrama.org",
            "https://new.katmoviehd.top",
            "https://katmoviehd.to",
            "https://katmoviehd.sx",
            "https://katmoviehd.nl"
        )

        private suspend fun resolveUrl(force: Boolean = false): String {
            if (!force) {
                resolvedUrl?.let { return it }
            }
            for (domain in domains) {
                try {
                    val response = app.get(domain, timeout = 5000L)
                    if (response.code == 200) {
                        val text = response.text
                        if (text.contains("wp-content") || text.contains("wp-includes")) {
                            resolvedUrl = domain
                            Log.i("KatMovieHD", "Successfully resolved working domain: $domain")
                            return domain
                        } else {
                            Log.w("KatMovieHD", "Domain check for $domain returned 200 but signature check failed (e.g. parked domain or ISP block page)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("KatMovieHD", "Domain check failed for $domain: ${e.message}")
                }
            }
            val fallback = domains.first()
            resolvedUrl = fallback
            Log.w("KatMovieHD", "All domains failed or timed out. Falling back to default: $fallback")
            return fallback
        }

        private fun String.fixUrl(resolved: String): String {
            for (domain in domains) {
                if (this.startsWith(domain)) {
                    return this.replaceFirst(domain, resolved)
                }
            }
            return this
        }
    }

    private suspend fun safeGet(url: String): NiceResponse {
        var resolved = resolveUrl()
        var targetUrl = url.fixUrl(resolved)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9"
        )
        return try {
            val res = app.get(targetUrl, headers = headers, referer = resolved)
            if (res.code == 200) {
                res
            } else {
                Log.w("KatMovieHD", "GET request to $targetUrl failed with code ${res.code}. Forcing domain re-resolution.")
                resolved = resolveUrl(force = true)
                app.get(url.fixUrl(resolved), headers = headers, referer = resolved)
            }
        } catch (e: Exception) {
            Log.w("KatMovieHD", "GET request to $targetUrl failed with exception: ${e.message}. Forcing domain re-resolution.")
            resolved = resolveUrl(force = true)
            app.get(url.fixUrl(resolved), headers = headers, referer = resolved)
        }
    }

    override var mainUrl: String
        get() = resolvedUrl ?: "https://new.katmoviehd.top"
        set(value) {
            resolvedUrl = value
        }

    override var name = "KatMovieHD"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage: List<MainPageData>
        get() = mainPageOf(
            "$mainUrl/" to "Home",
            "$mainUrl/category/movies/" to "Movies",
            "$mainUrl/category/tv-shows/" to "TV Shows",
            "$mainUrl/category/hindi-dubbed/" to "Hindi Dubbed",
            "$mainUrl/category/korean-series/" to "Korean Series"
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) request.data
                  else {
                      if (request.data.endsWith("/")) "${request.data}page/$page/"
                      else "${request.data}/page/$page/"
                  }
        val doc = safeGet(url).document
        val items = doc.select("ul.recent-posts > li, li.post, article, div.post, div.post-cards > article, div.post-grid > article, div.movies-grid > a")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    private fun isTvSeries(title: String): Boolean {
        return title.contains("Season", true) || 
               title.contains("S0", true) ||
               title.contains("Complete", true) || 
               title.contains("Episode", true) || 
               title.contains("Series", true) ||
               title.contains("Drama", true) ||
               title.contains("Show", true) ||
               title.contains("Kdrama", true) ||
               title.contains("Jdrama", true) ||
               Regex("""(?i)\bs\d+\b""").containsMatchIn(title) ||
               Regex("""(?i)\bep\s*\d+\b""").containsMatchIn(title)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: (if (tagName() == "a") this else null) ?: return null
        val href = aTag.attr("href") ?: return null
        if (href.isBlank()) return null
        
        val title = selectFirst("h2.entry-title, h1.entry-title, h2, h3")?.text() 
            ?: selectFirst("img")?.attr("alt") 
            ?: text()
            ?: return null
        if (title.isBlank()) return null
        
        var poster = selectFirst("img")?.attr("src")
        if (poster.isNullOrBlank() || !poster.startsWith("http")) {
            poster = selectFirst("img")?.attr("data-src")
        }
        
        val tvType = if (isTvSeries(title)) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        
        return newMovieSearchResponse(title.replace("Download ", ""), href, tvType) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = safeGet(url).document
        return doc.select("ul.recent-posts > li, li.post, article, div.post, div.post-cards > article, div.post-grid > article, div.movies-grid > a")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = safeGet(url).document
        
        var title = doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")
            ?: doc.selectFirst("h1.entry-title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: doc.selectFirst("h2.entry-title")?.text()
            ?: ""
        
        title = title.replaceFirst(Regex("(?i)^Download\\s+"), "").trim()
        if (title.equals("Loading...", ignoreCase = true) || title.isBlank()) {
            title = doc.selectFirst("h1.entry-title")?.text() ?: ""
        }
        
        var poster = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
            ?: doc.selectFirst("div.entry-content img")?.let { img ->
                img.attr("data-lazy-src").takeIf { !it.isNullOrBlank() }
                    ?: img.attr("data-src").takeIf { !it.isNullOrBlank() }
                    ?: img.attr("src")
            }
            
        poster = poster?.trim()?.let { p ->
            when {
                p.startsWith("//") -> "https:$p"
                p.startsWith("/") -> "${mainUrl.removeSuffix("/")}$p"
                else -> p
            }
        }
        
        val description = doc.selectFirst("div.single-main-content p, div.entry-content p")?.text()
            ?: doc.selectFirst("meta[property=\"og:description\"]")?.attr("content")
            ?: ""
            
        val tags = doc.select("div.entry-categories a, div.entry-tags a, div.single-tags a, a[rel=\"category tag\"]").map { it.text() }
        
        val type = if (isTvSeries(title)) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        // Extract quality links in the "Single Episodes Link" or single links section
        val qualityLinks = mutableListOf<Pair<String, String>>()
        doc.select("div.single-main-content a[href], div.entry-content a[href]").forEach { a ->
            val href = a.attr("href") ?: return@forEach
            val text = a.text().trim()
            val parentText = a.parent()?.text() ?: ""
            
            val isQualityLink = text.contains("480p", true) || 
                                 text.contains("720p", true) || 
                                 text.contains("1080p", true) || 
                                 text.contains("2160p", true) ||
                                 text.contains("4K", true) ||
                                 text.contains("Single", true) ||
                                 text.contains("Episode", true)
                                 
            val isPack = text.contains("Pack", true) || 
                         text.contains("Zip", true) || 
                         text.contains("Complete", true) ||
                         parentText.contains("Pack", true) ||
                         parentText.contains("Zip", true)
                         
            val matchesHref = !href.startsWith("#") && 
                              !href.contains("facebook.com") && 
                              !href.contains("twitter.com") && 
                              !href.contains("telegram", ignoreCase = true)
                              
            if (isQualityLink && !isPack && matchesHref) {
                var cleanName = text
                val qualityMatch = Regex("""(?i)\b(480p|720p|1080p|2160p|4k)\b""").find(text)
                if (qualityMatch != null) {
                    cleanName = qualityMatch.value.uppercase()
                }
                qualityLinks.add(cleanName to href)
            }
        }

        if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            
            if (qualityLinks.isNotEmpty()) {
                val parsedEpisodes = coroutineScope {
                    qualityLinks.map { (quality, packUrl) ->
                        async {
                            try {
                                val packDoc = safeGet(packUrl).document
                                val episodeUrls = mutableListOf<Pair<String, String>>() // name to url
                                
                                // Walk the DOM in-order to find episode headings and associate links
                                var currentEpisodeNum: Int? = null
                                val contentElements = packDoc.select("div.single-main-content *, div.entry-content *")
                                contentElements.forEach { el ->
                                    val text = el.text().trim()
                                    if (el.tagName() in listOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "span", "strong") && text.length < 60) {
                                        if (text.contains("Ep", ignoreCase = true) || text.contains("Episode", ignoreCase = true)) {
                                            val epNum = getEpisodeNumber(text)
                                            if (epNum != null) {
                                                currentEpisodeNum = epNum
                                            }
                                        }
                                    }
                                    
                                    if (el.tagName() == "a" && currentEpisodeNum != null) {
                                        val href = el.attr("href") ?: return@forEach
                                        val name = el.text().trim()
                                        val isValidProvider = href.contains("/locked?redirect=") || 
                                                              href.contains("/file/") || 
                                                              href.contains("/drive/") ||
                                                              href.contains("hubcloud") ||
                                                              href.contains("gdflix") ||
                                                              href.contains("send.cm") ||
                                                              href.contains("send.now")
                                                              
                                        if (isValidProvider && !href.startsWith("#")) {
                                            val epName = "Episode $currentEpisodeNum"
                                            episodeUrls.add(epName to href)
                                        }
                                    }
                                }
                                
                                // Fallback: if the in-order DOM parsing returned nothing, run the legacy parser
                                if (episodeUrls.isEmpty()) {
                                    packDoc.select("a[href]").forEach { a ->
                                        val href = a.attr("href") ?: return@forEach
                                        val name = a.text().trim()
                                        if (href.contains("/locked?redirect=") || href.contains("/file/") || href.contains("/drive/")) {
                                            episodeUrls.add(name to href)
                                        }
                                    }
                                }
                                
                                quality to episodeUrls
                            } catch (e: Exception) {
                                Log.e("KatMovieHD", "Failed to parse pack $packUrl: ${e.message}")
                                null
                            }
                        }
                    }.mapNotNull { it.await() }
                }

                // Group by episode number
                val epMap = mutableMapOf<Int, MutableList<QualityEpisode>>()

                parsedEpisodes.forEach { (quality, episodeUrls) ->
                    episodeUrls.forEach { (name, url) ->
                        val epNum = getEpisodeNumber(name)
                        if (epNum != null) {
                            val list = epMap.getOrPut(epNum) { mutableListOf() }
                            list.add(QualityEpisode(quality, url))
                        }
                    }
                }

                epMap.keys.sorted().forEach { epNum ->
                    val list = epMap[epNum] ?: return@forEach
                    val jsonArray = JSONArray()
                    list.forEach { qEp ->
                        val obj = JSONObject()
                        obj.put("quality", qEp.quality)
                        obj.put("url", qEp.url)
                        jsonArray.put(obj)
                    }
                    val dataString = jsonArray.toString()
                    episodes.add(newEpisode(dataString) {
                        this.name = "Episode $epNum"
                        this.episode = epNum
                        this.season = 1
                    })
                }
            }

            // Fallback to legacy parser if no quality pack links or grouping failed
            if (episodes.isEmpty()) {
                val epLinks = doc.select("div.single-main-content a[href], div.entry-content a[href]")
                var epCounter = 1
                for (a in epLinks) {
                    val href = a.attr("href") ?: continue
                    val text = a.text().trim()
                    if (text.contains("Episode", true) || text.contains("Ep ", true) || text.contains("Ep.", true)) {
                        val epMatch = Regex("""(?i)(?:Episode|Ep\.?|Ep)\s*(\d+)""").find(text)
                        val epNum = epMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: epCounter++
                        
                        episodes.add(newEpisode(href) {
                            this.name = text
                            this.episode = epNum
                            this.season = 1
                        })
                    }
                }
            }
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
            // Movie
            if (qualityLinks.isNotEmpty()) {
                val jsonArray = JSONArray()
                qualityLinks.forEach { (quality, href) ->
                    val obj = JSONObject()
                    obj.put("quality", quality)
                    obj.put("url", href)
                    jsonArray.put(obj)
                }
                return newMovieLoadResponse(title, url, TvType.Movie, listOf(EpisodeLink(jsonArray.toString()))) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = tags
                }
            }

            // Fallback to legacy movie links parser
            val links = mutableListOf<MovieLink>()
            val aTags = doc.select("div.single-main-content a[href], div.entry-content a[href]")
            for (a in aTags) {
                val href = a.attr("href") ?: continue
                if (href.isBlank() || href.startsWith("#") || href.contains(mainUrl) || href.contains("javascript:") || href.contains("facebook.com") || href.contains("twitter.com")) {
                    continue
                }
                val text = a.text().trim()
                links.add(MovieLink(name = text.ifEmpty { "Download Link" }, url = href))
            }
            return newMovieLoadResponse(title, url, TvType.Movie, links.map { EpisodeLink(it.url) }) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.i("KatMovieHD", "loadLinks called with data: $data")
        
        val urlsToResolve = mutableListOf<Pair<String, String>>() // url to quality
        
        try {
            if (data.startsWith("[") && data.endsWith("]")) {
                val jsonArray = JSONArray(data)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val url = obj.getString("url")
                    val quality = obj.optString("quality", "1080P")
                    
                    if (url.contains("/pack/")) {
                        val packDoc = app.get(url).document
                        if (url.contains("links.kmhd")) {
                            packDoc.select("a[href]").forEach { a ->
                                val href = a.attr("href") ?: return@forEach
                                if (href.contains("/locked?redirect=") || href.contains("/file/")) {
                                    val absoluteHref = if (href.startsWith("/")) {
                                        val uri = URI(url)
                                        "${uri.scheme}://${uri.host}$href"
                                    } else {
                                        href
                                    }
                                    urlsToResolve.add(absoluteHref to quality)
                                }
                            }
                        } else if (url.contains("gdflix", ignoreCase = true)) {
                            packDoc.select("a[href*=\"/file/\"]").forEach { a ->
                                val href = a.attr("href") ?: return@forEach
                                val absoluteHref = if (href.startsWith("/")) {
                                    val uri = URI(url)
                                    "${uri.scheme}://${uri.host}$href"
                                } else {
                                    href
                                }
                                urlsToResolve.add(absoluteHref to quality)
                            }
                        }
                    } else if (url.contains("links.dramashindi.online/archives/")) {
                        val packDoc = app.get(url).document
                        val contentElements = packDoc.select("div.single-main-content a[href], div.entry-content a[href]")
                        contentElements.forEach { el ->
                            val href = el.attr("href") ?: return@forEach
                            val isValidProvider = href.contains("/locked?redirect=") || 
                                                   href.contains("/file/") || 
                                                   href.contains("/drive/") ||
                                                   href.contains("hubcloud") ||
                                                   href.contains("gdflix") ||
                                                   href.contains("send.cm") ||
                                                   href.contains("send.now")
                                                   
                            if (isValidProvider && !href.startsWith("#")) {
                                val absoluteHref = if (href.startsWith("/")) {
                                    val uri = URI(url)
                                    "${uri.scheme}://${uri.host}$href"
                                } else {
                                    href
                                }
                                urlsToResolve.add(absoluteHref to quality)
                            }
                        }
                    } else {
                        val absoluteUrl = if (url.startsWith("/")) {
                            // If the initial URL itself is relative (fallback)
                            val mainUri = URI(mainUrl)
                            "${mainUri.scheme}://${mainUri.host}$url"
                        } else {
                            url
                        }
                        urlsToResolve.add(absoluteUrl to quality)
                    }
                }
            } else if (data.startsWith("http")) {
                urlsToResolve.add(data to "1080P")
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Failed parsing data JSON: ${e.message}")
            if (data.startsWith("http")) {
                urlsToResolve.add(data to "1080P")
            }
        }
        
        if (urlsToResolve.isEmpty()) {
            Log.w("KatMovieHD", "No URLs to resolve")
            return false
        }
        
        coroutineScope {
            urlsToResolve.map { (url, quality) ->
                async {
                    try {
                        resolveEpisodeFile(url, quality, subtitleCallback, callback)
                    } catch (e: Exception) {
                        Log.e("KatMovieHD", "Error resolving file $url: ${e.message}")
                    }
                }
            }.forEach { it.await() }
        }
        
        return true
    }

    private suspend fun resolveEpisodeFile(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var fileUrl = url
        
        // Route Type 1: B64-Bypass Shortlink
        if (url.contains("/locked?redirect=")) {
            val encodedRedirect = url.substringAfter("redirect=")
            val decodedPath = base64Decode(encodedRedirect)
            if (decodedPath.isNotEmpty()) {
                val uri = URI(url)
                val base = "${uri.scheme}://${uri.host}"
                fileUrl = base.trimEnd('/') + "/" + decodedPath.trimStart('/')
            }
        }
        
        Log.i("KatMovieHD", "Resolved unlocked page URL: $fileUrl")
        
        val isShortlinkPage = fileUrl.contains("links.kmhd.eu/file/") || 
                              (fileUrl.contains("/file/") && 
                               !fileUrl.contains("gdflix", ignoreCase = true) && 
                               !fileUrl.contains("hubcloud", ignoreCase = true) &&
                               !fileUrl.contains("gofile.io", ignoreCase = true))
        
        if (isShortlinkPage) {
            val doc = app.get(fileUrl).document
            // Scrape all provider buttons
            doc.select("a[href]").forEach { a ->
                val href = a.attr("href") ?: return@forEach
                val text = a.text().lowercase()
                
                val absoluteHref = if (href.startsWith("/")) {
                    val uri = URI(fileUrl)
                    "${uri.scheme}://${uri.host}$href"
                } else {
                    href
                }
                
                when {
                    absoluteHref.contains("gdflix", ignoreCase = true) || text.contains("gdflix") -> {
                        resolveGDFlix(absoluteHref, quality, subtitleCallback, callback)
                    }
                    absoluteHref.contains("hubcloud", ignoreCase = true) || text.contains("hubcloud") -> {
                        resolveHubCloud(absoluteHref, quality, subtitleCallback, callback)
                    }
                    absoluteHref.contains("send.cm", ignoreCase = true) || absoluteHref.contains("send.now", ignoreCase = true) || text.contains("send.cm") || text.contains("send.now") -> {
                        resolveSendCm(absoluteHref, quality, callback)
                    }
                    else -> {
                        loadExtractor(absoluteHref, subtitleCallback, callback)
                    }
                }
            }
        } else {
            // Direct provider links parsed from pack pages
            val lowerUrl = fileUrl.lowercase()
            when {
                lowerUrl.contains("gdflix") -> {
                    resolveGDFlix(fileUrl, quality, subtitleCallback, callback)
                }
                lowerUrl.contains("hubcloud") -> {
                    resolveHubCloud(fileUrl, quality, subtitleCallback, callback)
                }
                lowerUrl.contains("send.cm") || lowerUrl.contains("send.now") -> {
                    resolveSendCm(fileUrl, quality, callback)
                }
                else -> {
                    loadExtractor(fileUrl, subtitleCallback, callback)
                }
            }
        }
    }

    private suspend fun resolveGDFlix(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.i("KatMovieHD", "Resolving GDFlix URL: $url")
        val doc = try {
            app.get(url).document
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Failed to fetch GDFlix page: ${e.message}")
            return
        }
        
        val buttons = doc.select("a.btn, a[href]")
        buttons.forEach { a ->
            val href = a.attr("href") ?: return@forEach
            val text = a.text().trim()
            
            when {
                // Instant DL [10GBPS]
                text.contains("Instant", ignoreCase = true) -> {
                    resolveGDFlixInstant(href, quality, callback)
                }
                
                // Cloud Download [R2]
                text.contains("R2", ignoreCase = true) || text.contains("Cloud Download", ignoreCase = true) -> {
                    callback(newExtractorLink("GDFlix [R2]", "GDFlix [R2]", href) {
                        this.quality = getQualityFromName(quality)
                    })
                }
                
                // Fast Cloud / Zipdisk
                text.contains("Fast Cloud", ignoreCase = true) || text.contains("Zipdisk", ignoreCase = true) -> {
                    val absoluteUrl = if (href.startsWith("/")) {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}$href"
                    } else {
                        href
                    }
                    resolveGDFlixFastCloud(absoluteUrl, quality, callback)
                }
                
                // Fallback to GoFile / MultiUp Mirror!
                text.contains("Mirror", ignoreCase = true) || 
                text.contains("Gofile", ignoreCase = true) || 
                text.contains("Multi", ignoreCase = true) || 
                href.contains("mirror", ignoreCase = true) || 
                href.contains("goflix", ignoreCase = true) || 
                href.contains("gofile", ignoreCase = true) ||
                href.contains("multi", ignoreCase = true) -> {
                    val absoluteGofileUrl = if (href.startsWith("/")) {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}$href"
                    } else {
                        href
                    }
                    resolveGDFlixGoFile(absoluteGofileUrl, subtitleCallback, callback)
                }
            }
        }
    }

    private suspend fun resolveGDFlixInstant(url: String, quality: String, callback: (ExtractorLink) -> Unit) {
        try {
            var targetUrl = url
            if (!url.contains("fastcdn-dl.pages.dev")) {
                val resp = app.get(url, allowRedirects = true)
                targetUrl = resp.url
            }
            if (targetUrl.contains("url=")) {
                val directUrl = java.net.URLDecoder.decode(targetUrl.substringAfter("url="), "UTF-8")
                callback(newExtractorLink("GDFlix [Instant DL]", "GDFlix [Instant DL]", directUrl) {
                    this.quality = getQualityFromName(quality)
                })
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "GDFlix: Instant DL resolution failed: ${e.message}")
        }
    }

    private suspend fun resolveGDFlixFastCloud(url: String, quality: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url).document
            val dlBtn = doc.selectFirst("a:contains(CLOUD RESUME DOWNLOAD)")
                     ?: doc.selectFirst("a:contains(Download)")
            val href = dlBtn?.attr("href")
            if (!href.isNullOrBlank()) {
                val absoluteUrl = if (href.startsWith("/")) {
                    val uri = URI(url)
                    "${uri.scheme}://${uri.host}$href"
                } else {
                    href
                }
                callback(newExtractorLink("GDFlix [Fast Cloud]", "GDFlix [Fast Cloud]", absoluteUrl) {
                    this.quality = getQualityFromName(quality)
                })
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "GDFlix: Fast Cloud resolution failed: ${e.message}")
        }
    }

    private suspend fun resolveGDFlixGoFile(url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, allowRedirects = true).document
            var gofileUrl = doc.select("a[href]").firstOrNull { 
                it.attr("href").contains("gofile.io") 
            }?.attr("href")
            
            if (gofileUrl.isNullOrBlank()) {
                gofileUrl = doc.select("a[href]").firstOrNull {
                    it.text().contains("gofile", ignoreCase = true) || it.attr("href").contains("gofile", ignoreCase = true)
                }?.attr("href")
            }
            
            if (!gofileUrl.isNullOrBlank()) {
                var realGofileUrl = if (gofileUrl.startsWith("/")) {
                    val uri = URI(url)
                    "${uri.scheme}://${uri.host}$gofileUrl"
                } else {
                    gofileUrl
                }
                
                if (!realGofileUrl.contains("gofile.io")) {
                    try {
                        val resp = app.get(realGofileUrl, allowRedirects = true)
                        realGofileUrl = resp.url
                    } catch (e: Exception) {
                        Log.e("KatMovieHD", "Failed to follow GoFile redirect: ${e.message}")
                    }
                }
                
                if (realGofileUrl.contains("gofile.io")) {
                    loadExtractor(realGofileUrl, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "GDFlix: GoFile resolution failed: ${e.message}")
        }
    }

    private suspend fun resolveHubCloud(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.i("KatMovieHD", "Resolving HubCloud URL: $url")
        try {
            val doc = app.get(url).document
            
            fun parseHubCloudButtons(document: org.jsoup.nodes.Document): Map<String, String> {
                val urls = mutableMapOf<String, String>()
                document.select("a[href]").forEach { a ->
                    val href = a.attr("href") ?: return@forEach
                    val text = a.text().lowercase()
                    when {
                        text.contains("10gbps") -> urls["HubCloud [10Gbps]"] = href
                        text.contains("pixel") -> urls["Pixel"] = href
                        text.contains("fslv2") -> urls["HubCloud [FSLv2]"] = href
                        text.contains("fsl") -> urls["HubCloud [FSL]"] = href
                        text.contains("buzz") -> urls["HubCloud [Buzz]"] = href
                    }
                }
                return urls
            }
            
            var buttons = parseHubCloudButtons(doc)
            var refererUrl = url
            
            if (buttons.isEmpty()) {
                var genUrl: String? = null
                val priorities = listOf(
                    "generate download link",
                    "generate download",
                    "generate direct download link",
                    "generate link",
                    "click here to download",
                    "download"
                )
                for (keyword in priorities) {
                    val match = doc.select("a, button").firstOrNull { el ->
                        val text = el.text().lowercase()
                        text.contains(keyword) && !el.attr("href").isNullOrBlank()
                    }
                    if (match != null) {
                        genUrl = match.attr("href")
                        break
                    }
                }
                
                if (!genUrl.isNullOrBlank()) {
                    val absoluteGenUrl = if (genUrl.startsWith("/")) {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}$genUrl"
                    } else if (!genUrl.startsWith("http")) {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}/${genUrl.trimStart('/')}"
                    } else {
                        genUrl
                    }
                    
                    Log.i("KatMovieHD", "Fetching genUrl: $absoluteGenUrl")
                    val genDoc = app.get(absoluteGenUrl, referer = url).document
                    buttons = parseHubCloudButtons(genDoc)
                    refererUrl = absoluteGenUrl
                }
            }
            
            buttons.forEach { (serverName, serverUrl) ->
                if (serverName == "Pixel") {
                    var realPixeldrainUrl = serverUrl
                    if (serverUrl.contains("hubcloud") || !serverUrl.contains("pixeldrain")) {
                        try {
                            val absoluteServerUrl = if (serverUrl.startsWith("/")) {
                                val uri = URI(refererUrl)
                                "${uri.scheme}://${uri.host}$serverUrl"
                            } else {
                                serverUrl
                            }
                            val resp = app.get(absoluteServerUrl, allowRedirects = true)
                            realPixeldrainUrl = resp.url
                        } catch (e: Exception) {
                            Log.e("KatMovieHD", "Failed to follow Pixeldrain redirect: ${e.message}")
                        }
                    }
                    if (!realPixeldrainUrl.isNullOrBlank() && (realPixeldrainUrl.contains("pixeldrain") || realPixeldrainUrl.contains("/u/"))) {
                        val directPixeldrain = realPixeldrainUrl.replace("/u/", "/api/file/").replace("pixeldrain.com", "pixeldrain.dev")
                        callback(newExtractorLink("Pixeldrain", "Pixeldrain [HubCloud]", directPixeldrain) {
                            this.quality = getQualityFromName(quality)
                        })
                    }
                } else {
                    try {
                        val absoluteServerUrl = if (serverUrl.startsWith("/")) {
                            val uri = URI(refererUrl)
                            "${uri.scheme}://${uri.host}$serverUrl"
                        } else {
                            serverUrl
                        }
                        
                        val resp = app.get(absoluteServerUrl, referer = refererUrl, allowRedirects = true)
                        val finalUrl = resp.url
                        
                        val contentType = resp.headers["Content-Type"]?.lowercase() ?: ""
                        val contentDisposition = resp.headers["Content-Disposition"]?.lowercase() ?: ""
                        val contentLength = resp.headers["Content-Length"]?.toLongOrNull() ?: 0L
                        
                        val isDirectLink = contentType.contains("video") || 
                                           contentType.contains("octet-stream") || 
                                           contentDisposition.contains("attachment") ||
                                           contentLength > 5_000_000L
                        
                        if (isDirectLink) {
                            callback(newExtractorLink(serverName, serverName, finalUrl) {
                                this.quality = getQualityFromName(quality)
                            })
                        } else {
                            val finalDoc = resp.document
                            var playUrl = finalDoc.selectFirst("a:contains(Download Here)")?.attr("href")
                            if (playUrl.isNullOrBlank()) {
                                finalDoc.select("a[href]").forEach { a ->
                                    val href = a.attr("href") ?: return@forEach
                                    val name = a.text().trim()
                                    if (name.contains("Download", ignoreCase = true) || href.contains(".mkv", ignoreCase = true) || href.contains(".mp4", ignoreCase = true)) {
                                        playUrl = href
                                    }
                                }
                            }
                            if (!playUrl.isNullOrBlank()) {
                                val absolutePlayUrl = if (playUrl!!.startsWith("/")) {
                                    val uri = URI(finalUrl)
                                    "${uri.scheme}://${uri.host}$playUrl"
                                } else {
                                    playUrl!!
                                }
                                callback(newExtractorLink(serverName, serverName, absolutePlayUrl) {
                                    this.quality = getQualityFromName(quality)
                                })
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("KatMovieHD", "Failed to resolve $serverName: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "HubCloud resolution failed: ${e.message}")
        }
    }

    private suspend fun resolveSendCm(
        url: String,
        quality: String,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.i("KatMovieHD", "Resolving Send.cm URL via WebView: $url")
        try {
            val webview = WebViewResolver(
                interceptUrl = Regex(".*send\\.(now|cm)/.*"),
                additionalUrls = listOf(Regex(".*\\.m3u8.*"), Regex(".*\\.mp4.*")),
                useOkhttp = false
            )
            val (_, collectedRequests) = webview.resolveUsingWebView(url)
            val streamRequest = collectedRequests.firstOrNull { 
                it.url.toString().contains(".m3u8") || it.url.toString().contains(".mp4") 
            }
            if (streamRequest != null) {
                val streamUrl = streamRequest.url.toString()
                val headers = streamRequest.headers.names().associateWith { streamRequest.header(it).orEmpty() }
                callback(
                    newExtractorLink(
                        "Send.cm", 
                        "Send.cm", 
                        streamUrl
                    ) {
                        this.referer = url
                        this.headers = headers
                        this.quality = getQualityFromName(quality)
                    }
                )
                Log.i("KatMovieHD", "Successfully resolved Send.cm stream: $streamUrl")
            } else {
                Log.w("KatMovieHD", "Send.cm: No stream link captured in WebView requests")
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Send.cm resolution failed: ${e.message}")
        }
    }

    private fun base64Decode(str: String): String {
        return try {
            String(android.util.Base64.decode(str, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            try {
                String(android.util.Base64.decode(str, android.util.Base64.URL_SAFE))
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun getEpisodeNumber(text: String): Int? {
        return Regex("""(?i)(?:episode|ep\.?|e)\s*(\d+)""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(\d{1,2})\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    data class MovieLink(
        val name: String,
        val url: String
    )

    data class EpisodeLink(
        val source: String
    )

    data class QualityEpisode(
        val quality: String,
        val url: String
    )
}
