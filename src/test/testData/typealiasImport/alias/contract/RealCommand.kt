package alias.contract

sealed interface RealCommand
data class DoThing(val x: Int) : RealCommand
