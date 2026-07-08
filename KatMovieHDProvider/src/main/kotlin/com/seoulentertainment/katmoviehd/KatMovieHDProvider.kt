package com.seoulentertainment.katmoviehd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import android.util.Log
import com.lagradost.nicehttp.NiceResponse
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.json.JSONObject
import org.json.JSONArray
import com.lagradost.cloudstream3.network.WebViewResolver


class KatMovieHDProvider : MainAPI() {
    companion object {
        private var resolvedUrl: String? = null
        private val domains = listOf(
            "https://katdrama.net",
            "https://katdrama.org",
            "https://new.katmoviehd.top",
            "https://katmoviehd.to",
            "https://katmoviehd.sx",
            "https://katmoviehd.nl",
            "https://new.katdrama.my",
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
                                        val epNum = getEpisodeNumber(text)
                                        if (epNum != null) {
                                            currentEpisodeNum = epNum
                                        }
                                    }
                                    
                                    if (el.tagName() == "a" && currentEpisodeNum != null) {
                                        val href = el.attr("href") ?: return@forEach
                                        val name = el.text().trim()
                                        val isValidProvider = href.contains("/locked?redirect=") || 
                                                              href.contains("/file/") || 
                                                              href.contains("/drive/") ||
                                                              href.contains("hubcloud", ignoreCase = true) ||
                                                              href.contains("gdflix", ignoreCase = true) ||
                                                              href.contains("send.cm", ignoreCase = true) ||
                                                              href.contains("send.now", ignoreCase = true) ||
                                                              href.contains("sendcm", ignoreCase = true)
                                                              
                                        if (isValidProvider && !href.startsWith("#")) {
                                            val epName = "Episode $currentEpisodeNum"
                                            val absoluteHref = if (href.startsWith("/")) {
                                                val uri = URI(packUrl)
                                                "${uri.scheme}://${uri.host}$href"
                                            } else {
                                                href
                                            }
                                            episodeUrls.add(epName to absoluteHref)
                                        }
                                    }
                                }
                                
                                // Fallback: if the in-order DOM parsing returned nothing, run the legacy parser
                                 if (episodeUrls.isEmpty()) {
                                     packDoc.select("a[href]").forEach { a ->
                                         val href = a.attr("href") ?: return@forEach
                                         val name = a.text().trim()
                                         val isValidProvider = href.contains("/locked?redirect=") || 
                                                               href.contains("/file/") || 
                                                               href.contains("/drive/") ||
                                                               href.contains("hubcloud", ignoreCase = true) ||
                                                               href.contains("gdflix", ignoreCase = true) ||
                                                               href.contains("send.cm", ignoreCase = true) ||
                                                               href.contains("send.now", ignoreCase = true) ||
                                                               href.contains("sendcm", ignoreCase = true)
                                         if (isValidProvider) {
                                             val absoluteHref = if (href.startsWith("/")) {
                                                 val uri = URI(packUrl)
                                                 "${uri.scheme}://${uri.host}$href"
                                             } else {
                                                 href
                                             }
                                             episodeUrls.add(name to absoluteHref)
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
                        val (_, packDoc, _) = getDocumentWithWebViewFallback(url)
                        if (packDoc != null) {
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
                        }
                    } else if (url.contains("links.dramashindi.online/archives/")) {
                        val (_, packDoc, _) = getDocumentWithWebViewFallback(url)
                        if (packDoc != null) {
                            val contentElements = packDoc.select("div.single-main-content a[href], div.entry-content a[href]")
                            contentElements.forEach { el ->
                                val href = el.attr("href") ?: return@forEach
                                val isValidProvider = href.contains("/locked?redirect=") || 
                                                       href.contains("/file/") || 
                                                       href.contains("/drive/") ||
                                                       href.contains("hubcloud", ignoreCase = true) ||
                                                       href.contains("gdflix", ignoreCase = true) ||
                                                       href.contains("send.cm", ignoreCase = true) ||
                                                       href.contains("send.now", ignoreCase = true) ||
                                                       href.contains("sendcm", ignoreCase = true)
                                                       
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
                        }
                    } else {
                        val absoluteUrl = if (url.startsWith("/")) {
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
        
        val qualityPriority = mapOf("2160P" to 4, "4K" to 4, "1080P" to 3, "720P" to 2, "480P" to 1)
        val sortedUrls = urlsToResolve.sortedByDescending { (_, quality) ->
            qualityPriority[quality.uppercase()] ?: 0
        }
        val uniqueUrls = sortedUrls.distinctBy { it.first }
        
        val seenStreamUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val deduplicatedCallback: (ExtractorLink) -> Unit = { link ->
            val cleanUrl = link.url.trim().lowercase()
            if (seenStreamUrls.add(cleanUrl)) {
                callback(link)
            } else {
                Log.i("KatMovieHD", "Deduplicated duplicate stream link: ${link.url}")
            }
        }
        
        coroutineScope {
            uniqueUrls.map { (url, quality) ->
                async {
                    try {
                        resolveEpisodeFile(url, quality, subtitleCallback, deduplicatedCallback)
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
        
        val isShortlinkPage = (fileUrl.contains("links.kmhd", ignoreCase = true) || 
                               fileUrl.contains("dramashindi", ignoreCase = true) ||
                               fileUrl.contains("kmhd", ignoreCase = true) || 
                               fileUrl.contains("desilinks", ignoreCase = true) ||
                               fileUrl.contains("/file/", ignoreCase = true)) && 
                               !fileUrl.contains("gdflix", ignoreCase = true) && 
                               !fileUrl.contains("hubcloud", ignoreCase = true) &&
                               !fileUrl.contains("gofile.io", ignoreCase = true) &&
                               !fileUrl.contains("send.cm", ignoreCase = true) &&
                               !fileUrl.contains("send.now", ignoreCase = true) &&
                               !fileUrl.contains("pixeldrain", ignoreCase = true)
        
        if (isShortlinkPage) {
            val (_, doc, _) = getDocumentWithWebViewFallback(fileUrl)
            if (doc != null) {
                // Scrape all provider buttons
                doc.select("a[href]").forEach { a ->
                val href = a.attr("href") ?: return@forEach
                val text = a.text().lowercase()
                
                var resolvedHref = if (href.startsWith("/")) {
                    val uri = URI(fileUrl)
                    "${uri.scheme}://${uri.host}$href"
                } else {
                    href
                }
                
                if (resolvedHref.contains("/redirect/") || resolvedHref.contains("kmhd.eu/route")) {
                    resolvedHref = resolveRedirectUrl(resolvedHref)
                }
                
                when {
                    resolvedHref.contains("gdflix", ignoreCase = true) || text.contains("gdflix") -> {
                        resolveGDFlix(resolvedHref, quality, subtitleCallback, callback)
                    }
                    resolvedHref.contains("hubcloud", ignoreCase = true) || text.contains("hubcloud") -> {
                        resolveHubCloud(resolvedHref, quality, subtitleCallback, callback)
                    }
                    resolvedHref.contains("send.cm", ignoreCase = true) || resolvedHref.contains("send.now", ignoreCase = true) || text.contains("send.cm") || text.contains("send.now") -> {
                        resolveSendCm(resolvedHref, quality, callback)
                    }
                    resolvedHref.contains("gofile.io", ignoreCase = true) || text.contains("gofile") -> {
                        resolveGoFile(resolvedHref, quality, callback)
                    }
                    resolvedHref.contains("pixeldrain", ignoreCase = true) || text.contains("pixeldrain") -> {
                        resolvePixeldrain(resolvedHref, quality, callback)
                    }
                    else -> {
                        loadExtractor(resolvedHref, subtitleCallback, callback)
                    }
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
                lowerUrl.contains("gofile.io") || lowerUrl.contains("gofile") -> {
                    resolveGoFile(fileUrl, quality, callback)
                }
                lowerUrl.contains("pixeldrain") -> {
                    resolvePixeldrain(fileUrl, quality, callback)
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
        val (_, doc, _) = getDocumentWithWebViewFallback(url)
        if (doc == null) {
            Log.e("KatMovieHD", "Failed to fetch GDFlix page: returned null document")
            return
        }
        Log.i("KatMovieHD", "GDFlix page anchors: " + doc.select("a[href]").map { "[${it.text().trim()}] -> ${it.attr("href")}" }.toString())
        
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
                    callback(newKatExtractorLink("GDFlix [R2]", "GDFlix [R2]", href) {
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
                    resolveGDFlixGoFile(absoluteGofileUrl, quality, subtitleCallback, callback)
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
                callback(newKatExtractorLink("GDFlix [Instant DL]", "GDFlix [Instant DL]", directUrl) {
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
                callback(newKatExtractorLink("GDFlix [Fast Cloud]", "GDFlix [Fast Cloud]", absoluteUrl) {
                    this.quality = getQualityFromName(quality)
                })
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "GDFlix: Fast Cloud resolution failed: ${e.message}")
        }
    }

    private suspend fun resolveGoFile(url: String, quality: String, callback: (ExtractorLink) -> Unit) {
        Log.i("KatMovieHD", "Resolving GoFile URL via WebView: $url with quality $quality")
        try {
            var capturedUrl: String? = null
            var capturedHeaders: Map<String, String>? = null
            
            val q = quality.lowercase()
            val script = """
                (function() {
                    var checkFiles = setInterval(function() {
                        var targetQuality = "$q";
                        var bestLink = null;
                        var anchors = Array.from(document.querySelectorAll('a[href*="download"], a[href*="srv-"]'));
                        
                        for (var i = 0; i < anchors.length; i++) {
                            var a = anchors[i];
                            var text = a.textContent.toLowerCase() + " " + a.href.toLowerCase();
                            var rowText = a.parentElement ? a.parentElement.textContent.toLowerCase() : "";
                            if (text.indexOf(targetQuality) !== -1 || rowText.indexOf(targetQuality) !== -1) {
                                bestLink = a;
                                break;
                            }
                        }
                        
                        if (!bestLink && anchors.length > 0) {
                            bestLink = anchors[0];
                        }
                        
                        if (bestLink && !bestLink.dataset.clicked) {
                            bestLink.dataset.clicked = 'true';
                            bestLink.click();
                            clearInterval(checkFiles);
                        }
                    }, 500);
                })();
            """.trimIndent()

            val webview = WebViewResolver(
                interceptUrl = Regex("this-is-a-dummy-regex-pattern-that-does-not-match"),
                additionalUrls = listOf(
                    Regex(".*srv-.*\\.gofile\\.io/download/.*"),
                    Regex(".*\\.gofile\\.io/download/.*")
                ),
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                useOkhttp = false,
                script = script,
                timeout = 20000L
            )
            
            webview.resolveUsingWebView(
                url = url,
                requestCallBack = { request ->
                    val requestUrl = request.url.toString()
                    Log.i("KatMovieHD", "GoFile WebView captured request: $requestUrl")
                    if (requestUrl.contains("gofile.io/download") || requestUrl.contains("srv-")) {
                        capturedUrl = requestUrl
                        capturedHeaders = request.headers.names().associateWith { request.header(it).orEmpty() }
                        true // Destroy webview
                    } else {
                        false
                    }
                }
            )
            
            if (!capturedUrl.isNullOrBlank()) {
                val fileName = capturedUrl!!.substringAfterLast("/").substringBefore("?").ifBlank { "GoFile Video" }
                
                // Extract WebView cookies for authentication
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie("https://gofile.io") ?: cookieManager.getCookie(url)
                
                callback(
                    newKatExtractorLink(
                        "Gofile", 
                        "[GoFile] $fileName", 
                        capturedUrl!!,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        val headersMap = mutableMapOf<String, String>()
                        if (capturedHeaders != null) {
                            headersMap.putAll(capturedHeaders!!)
                        }
                        if (!cookies.isNullOrBlank()) {
                            headersMap["Cookie"] = cookies
                        }
                        this.headers = headersMap
                        this.quality = getQualityFromName(fileName)
                    }
                )
                Log.i("KatMovieHD", "Successfully resolved GoFile stream via WebView: $capturedUrl with cookies: $cookies")
            } else {
                Log.w("KatMovieHD", "GoFile: WebView didn't capture download link. Trying static extraction fallback.")
                val extractor = Gofile()
                extractor.getUrl(url, referer = url, subtitleCallback = {}, callback = { link ->
                    runBlocking {
                        callback(newKatExtractorLink(link.source, link.name, link.url, link.type) {
                            this.quality = link.quality
                            this.referer = link.referer
                            this.headers = link.headers
                        })
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Failed to resolve GoFile: ${e.message}")
        }
    }

    private suspend fun resolveGDFlixGoFile(url: String, quality: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.i("KatMovieHD", "resolveGDFlixGoFile called for URL: $url")
        try {
            val (_, doc, _) = getDocumentWithWebViewFallback(url)
            if (doc == null) {
                Log.e("KatMovieHD", "resolveGDFlixGoFile page returned null document")
                return
            }
            val anchors = doc.select("a[href]")
            Log.i("KatMovieHD", "resolveGDFlixGoFile found ${anchors.size} anchors")
            
            anchors.forEach { a ->
                var href = a.attr("href") ?: return@forEach
                val text = a.text().lowercase()
                
                val isGofile = href.contains("gofile.io") || text.contains("gofile")
                val isMegaup = href.contains("megaup.net") || text.contains("megaup")
                val is1fichier = href.contains("1fichier.com") || text.contains("1fichier")
                val isSendcm = href.contains("send.cm") || href.contains("sendcm") || href.contains("send.now")
                val isPixeldrain = href.contains("pixeldrain") || text.contains("pixeldrain")
                
                if (isGofile || isMegaup || is1fichier || isSendcm || isPixeldrain) {
                    var absoluteUrl = if (href.startsWith("/")) {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}$href"
                    } else {
                        href
                    }
                    
                    if (!absoluteUrl.contains("gofile.io") && !absoluteUrl.contains("megaup.net") && !absoluteUrl.contains("1fichier.com") && !absoluteUrl.contains("send.cm") && !absoluteUrl.contains("sendcm") && !absoluteUrl.contains("send.now") && !absoluteUrl.contains("pixeldrain")) {
                        absoluteUrl = resolveRedirectUrl(absoluteUrl)
                    }
                    
                    Log.i("KatMovieHD", "Loading extractor for mirror: $absoluteUrl")
                    if (absoluteUrl.contains("gofile.io") || absoluteUrl.contains("gofile")) {
                        resolveGoFile(absoluteUrl, quality, callback)
                    } else if (absoluteUrl.contains("send.cm") || absoluteUrl.contains("sendcm") || absoluteUrl.contains("send.now")) {
                        resolveSendCm(absoluteUrl, quality, callback)
                    } else if (absoluteUrl.contains("pixeldrain")) {
                        resolvePixeldrain(absoluteUrl, quality, callback)
                    } else {
                        loadExtractor(absoluteUrl, subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "GDFlix: GoFile resolution failed: ${e.message}")
        }
    }

    private suspend fun resolvePixeldrain(url: String, quality: String, callback: (ExtractorLink) -> Unit) {
        // Ignored due to 6GB bandwidth cap limit
    }

    private fun extractDirectLink(url: String): String? {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("link=") -> {
                val raw = url.substringAfter("link=")
                val cleaned = if (raw.contains("&")) raw.substringBefore("&") else raw
                try {
                    java.net.URLDecoder.decode(cleaned, "UTF-8")
                } catch (e: Exception) {
                    cleaned
                }
            }
            lowerUrl.contains("url=") -> {
                val raw = url.substringAfter("url=")
                val cleaned = if (raw.contains("&")) raw.substringBefore("&") else raw
                try {
                    java.net.URLDecoder.decode(cleaned, "UTF-8")
                } catch (e: Exception) {
                    cleaned
                }
            }
            else -> null
        }
    }

    private fun isDirectResponse(resp: NiceResponse): Boolean {
        val contentType = resp.headers["Content-Type"]?.lowercase() ?: ""
        val contentDisposition = resp.headers["Content-Disposition"]?.lowercase() ?: ""
        val contentLength = resp.headers["Content-Length"]?.toLongOrNull() ?: 0L
        return contentType.contains("video") || 
               contentType.contains("octet-stream") || 
               contentDisposition.contains("attachment") ||
               contentLength > 5_000_000L ||
               resp.url.contains(".mkv", ignoreCase = true) ||
               resp.url.contains(".mp4", ignoreCase = true)
    }

    private suspend fun getDocumentWithWebViewFallback(url: String): Triple<String, org.jsoup.nodes.Document?, Boolean> {
        try {
            val resp = app.get(url)
            if (isDirectResponse(resp)) {
                return Triple(resp.url, null, true)
            }
            val doc = resp.document
            val title = doc.title().lowercase()
            if (resp.code == 403 || title.contains("just a moment") || title.contains("cloudflare")) {
                Log.i("KatMovieHD", "Cloudflare detected on $url. Resolving via WebView...")
                val html = getHtmlViaWebView(url)
                if (html.isNotBlank()) {
                    val parsedDoc = org.jsoup.Jsoup.parse(html)
                    return Triple(url, parsedDoc, false)
                }
            }
            return Triple(resp.url, doc, false)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Content-Length", ignoreCase = true) || msg.contains("OOM", ignoreCase = true) || msg.contains("5000000", ignoreCase = true)) {
                Log.i("KatMovieHD", "OOM safety check triggered for $url (direct link).")
                return Triple(url, null, true)
            }
            Log.i("KatMovieHD", "Connection failed for $url: ${e.message}. Retrying via WebView...")
            try {
                val html = getHtmlViaWebView(url)
                if (html.isNotBlank()) {
                    val parsedDoc = org.jsoup.Jsoup.parse(html)
                    return Triple(url, parsedDoc, false)
                }
                return Triple(url, null, false)
            } catch (ex: Exception) {
                val exMsg = ex.message ?: ""
                if (exMsg.contains("Content-Length", ignoreCase = true) || exMsg.contains("OOM", ignoreCase = true) || exMsg.contains("5000000", ignoreCase = true)) {
                    return Triple(url, null, true)
                }
                Log.e("KatMovieHD", "WebView fallback failed for $url: ${ex.message}")
                return Triple(url, null, false)
            }
        }
    }

    private suspend fun getHtmlViaWebView(url: String): String {
        var html = ""
        try {
            val webview = WebViewResolver(
                interceptUrl = Regex(".*"),
                useOkhttp = false,
                script = "document.documentElement.outerHTML",
                scriptCallback = { html = it }
            )
            webview.resolveUsingWebView(url)
            var count = 0
            while (html.isBlank() && count < 30) {
                kotlinx.coroutines.delay(100)
                count++
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Failed to get HTML via WebView: ${e.message}")
        }
        
        return if (html.isNotBlank()) {
            val cleaned = html.trim()
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                cleaned.substring(1, cleaned.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "")
                    .replace("\\\\", "\\")
            } else {
                cleaned
            }
        } else {
            ""
        }
    }

    private suspend fun resolveRedirectUrl(url: String): String {
        try {
            val resp = app.get(url, allowRedirects = true)
            if (resp.url.isNotBlank() && resp.url != url) {
                return resp.url
            }
        } catch (e: Exception) {
            Log.w("KatMovieHD", "Failed fast redirect resolve for $url: ${e.message}")
        }
        
        try {
            val webview = WebViewResolver(
                interceptUrl = Regex("https?://(www\\.)?(gofile\\.io|send\\.cm|send\\.now|pixeldrain\\.(com|dev)|megaup\\.net|1fichier\\.com)/.*"),
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                useOkhttp = false,
                script = "(function() { var checkTimer = setInterval(function() { var continueBtn = Array.from(document.querySelectorAll('a, button, input[type=submit]')).find(function(el) { var txt = el.textContent.trim().toLowerCase(); var val = el.value ? el.value.trim().toLowerCase() : ''; return txt.indexOf('continue') !== -1 || txt.indexOf('proceed') !== -1 || txt.indexOf('click') !== -1 || txt.indexOf('download') !== -1 || val.indexOf('continue') !== -1 || val.indexOf('proceed') !== -1; }); if (continueBtn) { continueBtn.click(); } var form = document.querySelector('form'); if (form && !form.dataset.submitted) { form.dataset.submitted = 'true'; form.submit(); } }, 1000); })();",
                timeout = 15000L
            )
            val (req, _) = webview.resolveUsingWebView(url)
            if (req != null) {
                return req.url.toString()
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Failed WebView redirect resolve for $url: ${e.message}")
        }
        return url
    }

    private suspend fun resolveHubCloud(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.i("KatMovieHD", "Resolving HubCloud URL: $url")
        try {
            val (_, doc, _) = getDocumentWithWebViewFallback(url)
            if (doc == null) {
                Log.e("KatMovieHD", "resolveHubCloud page returned null document")
                return
            }
            Log.i("KatMovieHD", "HubCloud page anchors: " + doc.select("a[href]").map { "[${it.text().trim()}] -> ${it.attr("href")}" }.toString())
            
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
                    
                    Log.i("KatMovieHD", "Fetching genUrl via WebView: $absoluteGenUrl")
                    val html = getHtmlViaWebView(absoluteGenUrl)
                    if (html.isNotBlank()) {
                        val genDoc = org.jsoup.Jsoup.parse(html)
                        Log.i("KatMovieHD", "HubCloud gen page anchors from WebView: " + genDoc.select("a[href]").map { "[${it.text().trim()}] -> ${it.attr("href")}" }.toString())
                        buttons = parseHubCloudButtons(genDoc)
                        refererUrl = absoluteGenUrl
                    } else {
                        val (_, genDoc, _) = getDocumentWithWebViewFallback(absoluteGenUrl)
                        if (genDoc != null) {
                            buttons = parseHubCloudButtons(genDoc)
                            refererUrl = absoluteGenUrl
                        }
                    }
                }
            }
            
            buttons.forEach { (serverName, serverUrl) ->
                if (serverName == "Pixel") {
                    val absoluteServerUrl = if (serverUrl.startsWith("/")) {
                        val uri = URI(refererUrl)
                        "${uri.scheme}://${uri.host}$serverUrl"
                    } else {
                        serverUrl
                    }
                    val realPixeldrainUrl = resolveRedirectUrl(absoluteServerUrl)
                    
                    if (!realPixeldrainUrl.isNullOrBlank() && realPixeldrainUrl.contains("pixeldrain")) {
                        resolvePixeldrain(realPixeldrainUrl, quality, callback)
                    }
                } else {
                    try {
                        val absoluteServerUrl = if (serverUrl.startsWith("/")) {
                            val uri = URI(refererUrl)
                            "${uri.scheme}://${uri.host}$serverUrl"
                        } else {
                            serverUrl
                        }
                        
                        val (finalUrl, finalDoc, isDirectLink) = getDocumentWithWebViewFallback(absoluteServerUrl)
                        
                        val directUrl = extractDirectLink(finalUrl)
                        if (directUrl != null) {
                            callback(newKatExtractorLink(serverName, serverName, directUrl) {
                                this.quality = getQualityFromName(quality)
                            })
                            return@forEach
                        }
                        
                        if (isDirectLink) {
                            callback(newKatExtractorLink(serverName, serverName, finalUrl) {
                                this.quality = getQualityFromName(quality)
                            })
                        } else if (finalDoc != null) {
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
                                val finalDirect = extractDirectLink(absolutePlayUrl) ?: absolutePlayUrl
                                callback(newKatExtractorLink(serverName, serverName, finalDirect) {
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
        Log.i("KatMovieHD", "Resolving Send.cm URL: $url")
        var finalSendUrl = url
        if (url.contains("/redirect/")) {
            finalSendUrl = resolveRedirectUrl(url)
        }

        // Try direct POST extraction first with WebView fallback page retrieval
        try {
            val (_, doc, _) = getDocumentWithWebViewFallback(finalSendUrl)
            if (doc != null) {
                val form = doc.selectFirst("form")
                if (form != null) {
                    val inputs = form.select("input[type=hidden], input[type=submit], input[type=text]")
                    val postData = mutableMapOf<String, String>()
                    inputs.forEach { input ->
                        val name = input.attr("name")
                        val value = input.attr("value")
                        if (!name.isNullOrBlank()) {
                            postData[name] = value
                        }
                    }
                    
                    if (!postData.containsKey("op")) {
                        postData["op"] = "download2"
                    }
                    
                    val actionUrl = form.attr("action").ifBlank { finalSendUrl }
                    val absoluteAction = if (actionUrl.startsWith("/")) {
                        val uri = URI(finalSendUrl)
                        "${uri.scheme}://${uri.host}$actionUrl"
                    } else {
                        actionUrl
                    }
                    
                    val resp = app.post(
                        absoluteAction,
                        data = postData,
                        headers = mapOf(
                            "Referer" to finalSendUrl,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        ),
                        allowRedirects = false
                    )
                    
                    val redirectUrl = resp.headers["Location"]
                    if (!redirectUrl.isNullOrBlank()) {
                        callback(newKatExtractorLink("Send.cm", "Send.cm", redirectUrl) {
                            this.quality = getQualityFromName(quality)
                        })
                        Log.i("KatMovieHD", "Successfully resolved Send.cm via POST: $redirectUrl")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("KatMovieHD", "Send.cm: Direct POST extraction failed: ${e.message}. Falling back to WebView.")
        }

        // Fallback to interactive WebView Resolver
        try {
            var capturedUrl: String? = null
            var capturedHeaders: Map<String, String>? = null
            
            // Use dummy interceptUrl to prevent immediate exit on page URL match
            val webview = WebViewResolver(
                interceptUrl = Regex("this-is-a-dummy-regex-pattern-that-does-not-match"),
                additionalUrls = listOf(
                    Regex(".*\\.m3u8.*"),
                    Regex(".*\\.mp4.*"),
                    Regex(".*\\.mkv.*"),
                    Regex(".*send\\.(now|cm)/d/.*")
                ),
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                useOkhttp = false,
                script = "(function() { var checkTimer = setInterval(function() { var continueBtn = Array.from(document.querySelectorAll('a, button, div, span')).find(function(el) { return el.textContent.trim().toUpperCase() === 'CONTINUE'; }); if (continueBtn && !continueBtn.dataset.clicked) { continueBtn.dataset.clicked = 'true'; continueBtn.click(); return; } var form = document.querySelector('form'); if (form && !form.dataset.submitted) { form.dataset.submitted = 'true'; form.submit(); return; } var btn = document.querySelector('a.btn-download, a[href*=\"download\"], button[type=\"submit\"], .vjs-big-play-button'); if (btn && !btn.dataset.clicked) { btn.dataset.clicked = 'true'; btn.click(); } }, 1000); })();",
                timeout = 25000L
            )
            
            webview.resolveUsingWebView(
                url = finalSendUrl,
                requestCallBack = { request ->
                    val requestUrl = request.url.toString()
                    Log.i("KatMovieHD", "WebView captured request: $requestUrl")
                    if (requestUrl.contains(".m3u8") || requestUrl.contains(".mp4") || requestUrl.contains(".mkv")) {
                        capturedUrl = requestUrl
                        capturedHeaders = request.headers.names().associateWith { request.header(it).orEmpty() }
                        true // Destroy webview
                    } else if (requestUrl != finalSendUrl && requestUrl.contains("/d/") && (requestUrl.contains(".mp4") || requestUrl.contains(".mkv") || requestUrl.contains(".m3u8") || requestUrl.contains("download") || requestUrl.contains("stream"))) {
                        capturedUrl = requestUrl
                        capturedHeaders = request.headers.names().associateWith { request.header(it).orEmpty() }
                        true // Destroy webview
                    } else {
                        false
                    }
                }
            )
            
            if (!capturedUrl.isNullOrBlank()) {
                callback(
                    newKatExtractorLink(
                        "Send.cm", 
                        "Send.cm", 
                        capturedUrl!!
                    ) {
                        this.referer = finalSendUrl
                        if (capturedHeaders != null) {
                            this.headers = capturedHeaders!!
                        }
                        this.quality = getQualityFromName(quality)
                    }
                )
                Log.i("KatMovieHD", "Successfully resolved Send.cm stream via WebView: $capturedUrl")
            } else {
                Log.w("KatMovieHD", "Send.cm: No stream link captured in WebView requests")
            }
        } catch (e: Exception) {
            Log.e("KatMovieHD", "Send.cm WebView resolution failed: ${e.message}")
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
