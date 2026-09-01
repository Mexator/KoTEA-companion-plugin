package nr

data class NrState(val loading: Boolean = false)

sealed interface NrEvent

/** Command Root. */
sealed interface NrCommand

/** News Root carried over from a pre-KoTEA hierarchy: it extends the Command Root (issue #3). */
sealed interface NrNews : NrCommand

/** Concrete Command directly under the Command Root — Navigable, unaffected by the fix. */
data class NrLoad(val page: Int) : NrCommand

/** Concrete News — reaches the News Root before the Command Root, so it is not a Concrete Command. */
data class NrShown(val text: String) : NrNews
