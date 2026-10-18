package cov

/** Checks News against types, so News references here are Processing sites. */
class CovFragment {

    fun onNews(news: FeatureNews) {
        when (news) {
            // News Processing site for ShowError.
            is ShowError -> {}
            CloseScreen -> {}
        }
    }
}
