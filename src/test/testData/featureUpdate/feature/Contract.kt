package feature

data class FeatureState(val loading: Boolean = false)

sealed interface FeatureEvent
data class ItemClicked(val id: Int) : FeatureEvent
object Refreshed : FeatureEvent

sealed interface FeatureCommand
data class LoadItems(val page: Int) : FeatureCommand
object LogOut : FeatureCommand

data class FeatureNews(val message: String)
