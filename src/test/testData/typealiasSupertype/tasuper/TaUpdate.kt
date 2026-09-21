package tasuper

class TaUpdate : AppUpdate<TaState, TaEventRoot, TaCommand, TaNews>() {
    override fun NextBuilder.update(event: TaEventRoot) {}
}
