package edges

sealed interface EdgesEvent

/** Constructed from several sites in EdgesScreen.kt — the multi-target Emission case. */
data class ItemTapped(val id: Int) : EdgesEvent
