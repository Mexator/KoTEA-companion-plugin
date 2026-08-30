package feature

import ru.tinkoff.kotea.core.dsl.DslUpdate

class MyFeatureUpdate : DslUpdate<FeatureState, FeatureEvent, FeatureCommand, FeatureNews>() {
    override fun NextBuilder.update(event: FeatureEvent) {}
}
