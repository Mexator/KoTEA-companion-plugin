package bare

data class BareState(val n: Int = 0)

sealed interface BareEvent
data class Ticked(val at: Long) : BareEvent

sealed interface BareCommand
data class Persist(val n: Int) : BareCommand
