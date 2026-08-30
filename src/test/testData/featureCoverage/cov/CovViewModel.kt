package cov

/** Not a *Update* / *Handler* file, so references here are Emission sites. */
class CovViewModel {

    private val events = ArrayList<FeatureEvent>()

    fun onItemClick(id: Int) {
        // Event Emission site for ItemClicked.
        events += ItemClicked(id)
    }
}
