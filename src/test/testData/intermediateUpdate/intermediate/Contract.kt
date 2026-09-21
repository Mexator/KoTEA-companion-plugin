package intermediate

data class ProfileState(val name: String = "")

sealed interface ProfileEvent
data class NameChanged(val value: String) : ProfileEvent

sealed interface ProfileCommand
data class SaveName(val value: String) : ProfileCommand

// News Root the rebuild test binds in the shared base, in place of `Nothing`.
data class ProfileNews(val text: String)
