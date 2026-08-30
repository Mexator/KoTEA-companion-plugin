package feat1

data class Feat1State(val loading: Boolean = false)

data class Feat1News(val text: String)

// The Root the Feature Update points at initially.
sealed interface Feat1Event
data class Feat1Clicked(val id: Int) : Feat1Event

// Alternate Roots the retarget tests switch the type argument to.
sealed interface Feat1EventAlt
data class Feat1AltClicked(val id: Int) : Feat1EventAlt

sealed interface Feat1EventAlt2
data class Feat1Alt2Clicked(val id: Int) : Feat1EventAlt2

sealed interface Feat1Command
data class Feat1Load(val page: Int) : Feat1Command
