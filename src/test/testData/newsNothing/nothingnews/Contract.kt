package nothingnews

data class NnState(val x: Int = 0)

sealed interface NnEvent
data class Tapped(val id: Int) : NnEvent

sealed interface NnCommand
data class Fetch(val id: Int) : NnCommand
