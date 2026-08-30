package alias

data class AliasState(val x: Int = 0)

data class AliasNews(val text: String)

sealed interface RealEvent
data class Clicked(val id: Int) : RealEvent

// Alternate Event Root the rebuild test retargets the `FeatureEvent` typealias to.
sealed interface RealEvent2
data class Clicked2(val id: Int) : RealEvent2
