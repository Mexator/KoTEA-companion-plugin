package cov

import kotlinx.coroutines.flow.Flow
import ru.tinkoff.kotea.core.CommandsFlowHandler

class CovHandler : CommandsFlowHandler<FeatureCommand, FeatureEvent> {

    override fun handle(commands: Flow<FeatureCommand>): Flow<FeatureEvent> {
        // Command Processing site for LoadItems — the single reference inside handle(...).
        error(LoadItems(1))
    }
}
