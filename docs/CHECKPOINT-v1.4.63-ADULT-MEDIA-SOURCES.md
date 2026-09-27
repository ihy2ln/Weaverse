# WeaverSocial 1.4.63: public adult media sources

Adult-labeled posts now use a rotating directory of public, indexed creator
pages, adult hubs, galleries, aggregators and forums through the optional Brave
Image Search key. Search uses Safe Search off only for adult-labeled posts while
WeaverSocial 18+ is enabled. Other posts use strict Safe Search. Sources
include OnlyFans, Fansly, Patreon, Pornhub, XVideos, xHamster, XNXX, RedGIFs,
Civitai, Gelbooru, Rule34, Sex.com and indexed adult Reddit communities.

Gelbooru's documented read-only DAPI is a second direct gallery source alongside
Civitai. Anonymous access is attempted; optional Gelbooru user ID and API key
are stored through the app's encrypted credential store. Adult-only rating and
underage-tag checks apply before selection. GIF requests accept only actual
GIF files; video pages remain attributed thumbnail cards. Existing relevance,
source credit, duplicate URL/checksum/image checks, and the 18+ switch remain
in force. Public previews can fail when a site requires authentication,
restricts a region, or provides no direct image URL. Private and paid media
is not accessed.

The implementation follows the [Brave Image Search API](https://api-dashboard.search.brave.com/api-reference/images/image_search),
[Brave search operators](https://api-dashboard.search.brave.com/documentation/resources/search-operators),
and [Gelbooru DAPI documentation](https://gelbooru.com/index.php?id=18780&page=wiki&s=view).

Delivery: `assembleRelease` succeeded, producing `weaverse-v1.4.63.apk` in
`S:\AI\Novel\Weaververse\Beta.Test.Build`. The APK declares version code 195 and
version name 1.4.63. Its SHA-256 is
`BD4421113E00FF5C1BE7E93FADC94D634FA12F8FFFC065982E6909CCDD72D16B`.
The signing certificate SHA-256 matches 1.4.62:
`f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`.
