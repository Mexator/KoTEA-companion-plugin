package cov

/** Event Root — top of the feature's Event hierarchy, not Navigable. */
sealed interface FeatureEvent

/** Abstract intermediate — not Navigable. */
sealed class NavigationEvent : FeatureEvent

/** Plain interface below the Root — not Navigable. */
interface AnalyticsEvent : FeatureEvent

/** Concrete Event (data class) — Navigable. */
data class ItemClicked(val id: Int) : FeatureEvent

/** Concrete Event (data object) below the abstract intermediate — Navigable. */
data object BackPressed : NavigationEvent
