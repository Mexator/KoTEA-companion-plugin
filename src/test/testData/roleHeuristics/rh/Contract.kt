package rh

data class RhState(val loading: Boolean = false)

sealed interface RhEvent {
    data class Clicked(val id: Int) : RhEvent
    data object Refresh : RhEvent
    data object Reset : RhEvent
}

sealed interface RealEvent {
    data object InvalidateCache : RealEvent
}

sealed interface RhCommand {
    data class Delegate(val event: RealEvent) : RhCommand
    data class Load(val id: Int) : RhCommand
}

sealed interface RhNews {
    data object Toast : RhNews
}
