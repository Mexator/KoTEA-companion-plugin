package rh

import ru.tinkoff.kotea.core.dsl.DslUpdate

class DecoratorUpdate : DslUpdate<RhState, RhEvent, RhCommand, RhNews>() {

    override fun NextBuilder.update(event: RhEvent) {
        when (event) {
            is RhEvent.Clicked -> commands(
                RhCommand.Delegate(
                    RealEvent.InvalidateCache,
                ),
            )
            RhEvent.Refresh -> commands(RhCommand.Load(1))
            else -> news(RhNews.Toast)
        }
    }

    private fun NextBuilder.handleClicked(event: RhEvent.Clicked) {
        println(event)
    }

    private fun toast(news: RhNews.Toast) = println(news)

    private fun load(command: RhCommand.Load) = println(command)
}
