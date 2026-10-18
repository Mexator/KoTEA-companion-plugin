package rh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import ru.tinkoff.kotea.core.CommandsFlowHandler

class RhHandler : CommandsFlowHandler<RhCommand, RhEvent> {

    override fun handle(commands: Flow<RhCommand>): Flow<RhEvent> =
        commands.filterIsInstance<RhCommand.Load>().map { RhEvent.Refresh }
}
