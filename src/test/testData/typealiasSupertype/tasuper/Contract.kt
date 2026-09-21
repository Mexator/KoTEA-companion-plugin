package tasuper

data class TaState(val x: Int = 0)

sealed interface TaEvent
data class TaClicked(val id: Int) : TaEvent

// Alternate Event Root the rebuild test retargets the `TaEventRoot` typealias to.
sealed interface TaEvent2
data class TaClicked2(val id: Int) : TaEvent2

sealed interface TaCommand
data class TaLoad(val page: Int) : TaCommand

data class TaNews(val text: String)
