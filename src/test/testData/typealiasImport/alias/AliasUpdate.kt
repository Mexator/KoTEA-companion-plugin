package alias

import alias.contract.RealCommand as FeatureCommand
import ru.tinkoff.kotea.core.dsl.DslUpdate

typealias FeatureEvent = RealEvent

class AliasUpdate : DslUpdate<AliasState, FeatureEvent, FeatureCommand, AliasNews>() {
    override fun NextBuilder.update(event: FeatureEvent) {}
}
