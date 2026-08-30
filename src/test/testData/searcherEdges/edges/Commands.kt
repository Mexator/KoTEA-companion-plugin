package edges

sealed interface EdgesCommand

/** Passed to commands(...) twice in EdgesUpdate.kt — the multi-target Emission case. */
data class LoadPage(val page: Int) : EdgesCommand
