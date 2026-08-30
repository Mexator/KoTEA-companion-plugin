package cov

/** Command Root — top of the feature's Command hierarchy, not Navigable. */
sealed interface FeatureCommand

/** Abstract / sealed Command base — not Navigable. */
sealed class BaseCommand : FeatureCommand

/** Plain interface below the Root — not Navigable. */
interface AsyncCommand : FeatureCommand

/** Concrete Command (data class) — Navigable. */
data class LoadItems(val page: Int) : FeatureCommand

/** Concrete Command (data object) below the abstract base — Navigable. */
data object Refresh : BaseCommand
