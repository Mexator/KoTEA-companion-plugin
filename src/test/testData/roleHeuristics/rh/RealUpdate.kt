package rh

import ru.tinkoff.kotea.core.dsl.DslUpdate

class RealUpdate : DslUpdate<RhState, RealEvent, RhCommand, RhNews>() {

    override fun NextBuilder.update(event: RealEvent) {
        if (event == RealEvent.InvalidateCache) commands(RhCommand.Load(2))
    }
}
