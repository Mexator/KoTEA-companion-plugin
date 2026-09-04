package cov

/** Not a *Update* file, so News references here are Processing sites. */
class CovFragment {

    fun onNews(news: FeatureNews) {
        when (news) {
            // News Processing site for ShowError.
            is ShowError -> {}
            CloseScreen -> {}
        }
    }
}
