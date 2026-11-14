# Trakt account synchronization (v0.177.0)
Connect Trakt in Settings > Account. Choose Sync Trakt account now, then Sync once or Sync now and daily. Library > Synced Trakt account library is available offline after download. The unified library and home discovery also link to it.

Downloads history (including separate repeated watch events), watchlist, ratings, saved playback progress, favorites, movie collection and TV collection membership. No remote mutation is performed by this sync. No sampled viewing duration, local watch record, or approved upload is created. The existing manual review-only import from downloaded history remains optional.

Each category is fetched with explicit pagination (250 requested per page, up to 100 pages per category). Actual server page counts or an empty page terminate the download. Safety-capped or unsupported data is reported as incomplete; existing complete snapshots are retained rather than overwritten by a partial fetch. For very large accounts the safety cap requires further pagination support. Collection entries are movie/show membership, not per-episode collection metadata. Custom lists, comments, profile data, and other account features are not downloaded in this release.

Account snapshots are encrypted using the Android Keystore with account/category/row identity as authenticated data. New sign-in, disconnect and clearing the downloaded library remove snapshots; clearing also disables daily refresh. Manual sync refreshes all categories; opted-in daily refresh skips complete unchanged categories when the API supplies corresponding activity markers. Remote changes remain separate from actual measured local viewing. No new permissions or signing changes.

Test with an authorized real account: compare history repeats, watchlist/rating changes, offline browse/search, account disconnect and account switch, empty categories, and API 429. CI cannot verify a private Trakt account without user authorization.
