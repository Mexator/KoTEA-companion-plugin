package intermediate

class ProfileUpdate : BaseFeatureUpdate<ProfileState, ProfileEvent, ProfileCommand>() {
    override fun NextBuilder.update(event: ProfileEvent) {}
}
