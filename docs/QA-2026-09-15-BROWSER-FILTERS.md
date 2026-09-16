# Browser filter controls

The browser filter dialog now keeps Reset and Filter above the scrolling options. Genres, Formats and Demographic use expandable checkbox lists. Language, publication status and tag match mode use dropdowns. Custom tags remain available in a collapsed section. Extension groups and sorting are collapsible; extension select values and native filter objects retain their original semantics.

Shared tags support All (AND) and Any (OR), with exact case-insensitive tag matching. These filters operate on catalog metadata returned by each source. They are not server-side filters and cannot match metadata a source omits. Extension-native filters continue to be passed to the extension search operation.

MangaDex now retains publication demographic, status, year and content rating from its existing catalog response. An empty filtered page offers Load next page when the source has more results. This prevents a first page without matches from trapping the user in Retry.

Verification: final `:app:assembleRelease` passed, including R8 minification and signing. The final certificate matched the installed certificate (SHA-256 f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05). Installed with `adb install -r` on MuMu 127.0.0.1:16384; existing Library / Last Updates entry remained visible.

MuMu interaction checks passed: open MangaDex, open Filters, expand Genres, select Action, apply and observe changed catalog results, reopen and verify Genres (1), choose Any (OR), choose English, Reset and verify All (AND), no selected genres and Any language. The checkbox section remained expanded after toggling a checkbox. Reset and Filter remained above the scrollable body.

Artifact: `S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.17-browser-filters-20260915-signed.apk`.

Limits: no installed third-party extension was exercised in this check. Fields such as year ranges and content-rating selection require source-native support and are not added as synthetic shared controls. Shared filters still depend on source metadata and operate page by page. No claim of complete Mihon parity.
