package cov

/** News Root — top of the feature's News hierarchy, not Navigable. */
sealed interface FeatureNews

/** Abstract intermediate — not Navigable. */
sealed class AlertNews : FeatureNews

/** Plain interface below the Root — not Navigable. */
interface ToastNews : FeatureNews

/** Concrete News (data class) — Navigable. */
data class ShowError(val message: String) : FeatureNews

/** Concrete News (data object) below the abstract intermediate — Navigable. */
data object CloseScreen : AlertNews
