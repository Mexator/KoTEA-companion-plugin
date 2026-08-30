package alias

data class AliasState(val x: Int = 0)

data class AliasNews(val text: String)

sealed interface RealEvent
data class Clicked(val id: Int) : RealEvent
