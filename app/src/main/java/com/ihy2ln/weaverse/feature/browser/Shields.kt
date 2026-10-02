package com.ihy2ln.weaverse.feature.browser

import android.net.Uri

/**
 * WeaverBrowser's Shields: the same protections Brave Shields has, run inside the app's
 * WebView. Requests to ad and tracker hosts are refused before they leave the phone;
 * Standard blocks them when another site loads them, Aggressive blocks them everywhere and
 * also hides ad slots on the page.
 */
object Shields {
    /** Estimates for the New Tab Page counters, per blocked request (Brave estimates too). */
    const val BYTES_PER_BLOCK = 48L * 1024
    const val MS_PER_BLOCK = 55L

    /**
     * Ad, tracker and fingerprinting hosts. A request is blocked when its host is one of these
     * or a subdomain of one. Kept to well-known networks so ordinary sites don't break.
     */
    private val BLOCKED = setOf(
        // Google ads and analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "imasdk.googleapis.com", "app-measurement.com", "2mdn.net", "admob.com",
        // Social trackers
        "connect.facebook.net", "pixel.facebook.com", "an.facebook.com", "analytics.tiktok.com",
        "ads.tiktok.com", "ads-twitter.com", "static.ads-twitter.com", "analytics.twitter.com",
        "ads.linkedin.com", "px.ads.linkedin.com", "snap.licdn.com", "tr.snapchat.com", "sc-static.net",
        "ct.pinterest.com", "analytics.pinterest.com", "bat.bing.com", "clarity.ms", "ads.reddit.com",
        "redditstatic.com/ads", "alb.reddit.com",
        // Ad exchanges and networks
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "amazon-adsystem.com", "adsrvr.org", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "smartadserver.com", "adform.net", "bidswitch.net", "3lift.com", "indexww.com", "media.net",
        "sharethrough.com", "teads.tv", "yieldmo.com", "spotxchange.com", "spotx.tv", "contextweb.com",
        "lijit.com", "sovrn.com", "gumgum.com", "triplelift.com", "districtm.io", "adcolony.com",
        "applovin.com", "unityads.unity3d.com", "inmobi.com", "mopub.com", "vungle.com", "chartboost.com",
        "advertising.com", "ads.yahoo.com", "analytics.yahoo.com", "adtech.de", "zedo.com", "revcontent.com",
        "mgid.com", "content.ad", "adroll.com", "quantcast.com", "quantserve.com", "scorecardresearch.com",
        "moatads.com", "adsafeprotected.com", "doubleverify.com", "serving-sys.com", "flashtalking.com",
        "eyeota.net", "bluekai.com", "krxd.net", "exelator.com", "demdex.net", "everesttech.net",
        "rlcdn.com", "agkn.com", "tapad.com", "adsymptotic.com", "mathtag.com", "turn.com", "rfihub.com",
        "simpli.fi", "sitescout.com", "yieldlab.net", "adition.com", "stroeerdigitalgroup.de", "mookie1.com",
        "zemanta.com", "nativo.com", "kargo.com", "33across.com", "ad.gt", "id5-sync.com", "liadm.com",
        "adkernel.com", "admixer.net", "adhese.com", "improvedigital.com", "connatix.com", "primis.tech",
        "vidazoo.com", "undertone.com", "springserve.com", "lkqd.net", "aniview.com", "playwire.com",
        "adthrive.com", "mediavine.com", "ezoic.net", "ezodn.com", "carbonads.com", "buysellads.com",
        "propellerads.com", "popads.net", "popcash.net", "adsterra.com", "hilltopads.net", "exoclick.com",
        "exosrv.com", "juicyads.com", "trafficjunky.net", "trafficjunky.com", "trafficfactory.biz",
        "tsyndicate.com", "adspyglass.com", "realsrv.com", "magsrv.com", "ad-maven.com", "clickadu.com",
        "a-ads.com", "onclickads.net", "adcash.com", "plugrush.com", "twinrdsrv.com",
        // Analytics and session recording
        "hotjar.com", "hotjar.io", "mixpanel.com", "cdn.segment.com", "api.segment.io", "amplitude.com",
        "fullstory.com", "mouseflow.com", "crazyegg.com", "luckyorange.com", "inspectlet.com", "heap.io",
        "heapanalytics.com", "chartbeat.com", "chartbeat.net", "parsely.com", "nr-data.net",
        "newrelic.com/js-agent", "optimizely.com", "kissmetrics.com", "statcounter.com", "histats.com",
        "yandex.ru/metrika", "mc.yandex.ru", "matomo.cloud", "branch.io", "adjust.com",
        "appsflyer.com", "kochava.com", "onesignal.com", "pushwoosh.com", "braze.com", "iterable.com",
        "comscore.com", "nielsen.com", "imrworldwide.com", "omtrdc.net", "2o7.net", "sc.omtrdc.net",
        "tealiumiq.com", "tiqcdn.com", "ensighten.com", "permutive.com", "permutive.app", "zeotap.com",
        // Fingerprinting
        "fingerprintjs.com", "fpjs.io", "fpcdn.io", "iovation.com", "threatmetrix.com", "online-metrix.net",
    )

    private val BLOCKED_HOSTS = BLOCKED.filterNot { '/' in it }.toSet()
    private val BLOCKED_PATHS = BLOCKED.filter { '/' in it }

    /** Paths that are almost always ads or beacons, on any host (Aggressive only). */
    private val AGGRESSIVE_PATHS = listOf("/ads/", "/adserver", "/pagead/", "/adview", "/advert", "/banners/", "/pixel?", "/beacon?", "/collect?v=")

    fun isTrackerHost(host: String): Boolean {
        var h = host.lowercase().removePrefix("www.")
        while (h.contains('.')) {
            if (h in BLOCKED_HOSTS) return true
            h = h.substringAfter('.')
        }
        return false
    }

    private fun registrable(host: String): String {
        val parts = host.lowercase().split('.')
        return if (parts.size <= 2) host.lowercase() else parts.takeLast(2).joinToString(".")
    }

    /**
     * Should this request be refused? [pageHost] is the site in the address bar; a request to
     * that same site is first-party and only Aggressive blocks it.
     */
    fun shouldBlock(request: Uri, pageHost: String, level: AdBlockLevel, isMainFrame: Boolean): Boolean {
        if (level == AdBlockLevel.Disabled || isMainFrame) return false
        val host = request.host ?: return false
        val firstParty = pageHost.isNotBlank() && registrable(host) == registrable(pageHost)
        val tracker = isTrackerHost(host) || BLOCKED_PATHS.any { (host + request.path.orEmpty()).contains(it) }
        return when (level) {
            AdBlockLevel.Standard -> tracker && !firstParty
            AdBlockLevel.Aggressive -> tracker || AGGRESSIVE_PATHS.any { request.toString().contains(it, ignoreCase = true) }
            AdBlockLevel.Disabled -> false
        }
    }

    /** Ad slots hidden on the page in Aggressive mode (cosmetic filtering). */
    const val COSMETIC_CSS = """
        [id^="google_ads_"], [id^="div-gpt-ad"], ins.adsbygoogle, .adsbygoogle, iframe[src*="doubleclick"],
        [class*="sponsored-ad"], [data-ad-slot], [data-adunit], .ad-banner, .ad-container, .ad-slot,
        .advertisement, #taboola-below-article, [id^="taboola-"], .OUTBRAIN, [data-widget-id^="AR_"],
        amp-ad, amp-embed[type="taboola"] { display: none !important; }
    """

    fun cosmeticScript(): String =
        "(function(){if(document.getElementById('weaver-shields-css'))return;var s=document.createElement('style');" +
            "s.id='weaver-shields-css';s.textContent=" + org.json.JSONObject.quote(COSMETIC_CSS) +
            ";(document.head||document.documentElement).appendChild(s);})();"

    /**
     * Fingerprinting protection run before the page's own scripts: small per-session noise on
     * canvas and audio reads, and generic values for the hardware details trackers combine.
     */
    val FINGERPRINT_SCRIPT = """
        (function(){
          if (window.__weaverFp) return; window.__weaverFp = true;
          var seed = Math.floor(Math.random()*7)+1;
          try {
            var toDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = function(){
              try { var ctx = this.getContext('2d'); if (ctx && this.width && this.height) {
                var d = ctx.getImageData(0,0,1,1); d.data[0] = (d.data[0]+seed)%256; ctx.putImageData(d,0,0); } } catch(e){}
              return toDataURL.apply(this, arguments);
            };
            var getImageData = CanvasRenderingContext2D.prototype.getImageData;
            CanvasRenderingContext2D.prototype.getImageData = function(){
              var r = getImageData.apply(this, arguments);
              for (var i = 0; i < r.data.length; i += 97) r.data[i] = (r.data[i]+seed)%256;
              return r;
            };
          } catch(e){}
          try {
            var gcd = AudioBuffer.prototype.getChannelData;
            AudioBuffer.prototype.getChannelData = function(){
              var c = gcd.apply(this, arguments);
              for (var i = 0; i < c.length; i += 101) c[i] = c[i] + seed*1e-7;
              return c;
            };
          } catch(e){}
          try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: function(){ return 4; } }); } catch(e){}
          try { Object.defineProperty(navigator, 'deviceMemory', { get: function(){ return 4; } }); } catch(e){}
          try { Object.defineProperty(navigator, 'plugins', { get: function(){ return []; } }); } catch(e){}
        })();
    """.trimIndent()
}
