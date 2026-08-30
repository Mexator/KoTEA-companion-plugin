package feat2

data class Feat2State(val loading: Boolean = false)

data class Feat2News(val text: String)

sealed interface Feat2Event
data class Feat2Opened(val id: Int) : Feat2Event

sealed interface Feat2Command
data class Feat2Fetch(val page: Int) : Feat2Command
