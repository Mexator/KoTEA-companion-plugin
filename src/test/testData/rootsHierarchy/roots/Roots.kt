package roots

sealed interface FeatureEvent
sealed class NavigationEvent : FeatureEvent
data class ItemClicked(val id: Int) : FeatureEvent
object BackPressed : NavigationEvent

sealed interface FeatureCommand
data class LoadItems(val page: Int) : FeatureCommand

class Unrelated
