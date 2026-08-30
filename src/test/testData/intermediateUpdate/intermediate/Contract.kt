package intermediate

data class ProfileState(val name: String = "")

sealed interface ProfileEvent
data class NameChanged(val value: String) : ProfileEvent

sealed interface ProfileCommand
data class SaveName(val value: String) : ProfileCommand
