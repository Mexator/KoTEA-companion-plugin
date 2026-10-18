package unitroots

data class UrState(val x: Int = 0)

sealed interface UrEvent
data class Tapped(val id: Int) : UrEvent
