                val playUrl = "$mainUrl/play?token=$token"
                var currentUrl = playUrl
                var location = ""
                var redirectCount = 0
                while (redirectCount < 5) {
                    val redirectResp = app.get(currentUrl, referer = data, allowRedirects = false)
                    val loc = redirectResp.headers["location"] ?: redirectResp.headers["Location"]
                    if (loc.isNullOrBlank()) {
                        break
                    }
                    location = if (loc.startsWith("/")) {
                        val uri = URI(currentUrl)
                        "${uri.scheme}://${uri.host}" + loc
                    } else {
                        loc
                    }
                    if (location.contains("#")) {
                        break
                    }
                    currentUrl = location
                    redirectCount++
                }
                
                val videoId = Regex("""#([^&]+)""").find(location)?.groupValues?.get(1) ?: return@forEach
                
                val embedHost = try {
                    val uri = URI(location)
                    if (uri.scheme != null && uri.host != null) {
                        "${uri.scheme}://${uri.host}"
                    } else {
                        "https://dramafilez.embedseek.com"
                    }
                } catch (e: Exception) {
                    "https://dramafilez.embedseek.com"
                }