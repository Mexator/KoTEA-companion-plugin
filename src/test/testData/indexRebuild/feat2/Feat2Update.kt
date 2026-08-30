package feat2

import ru.tinkoff.kotea.core.dsl.DslUpdate

class Feat2Update : DslUpdate<Feat2State, Feat2Event, Feat2Command, Feat2News>() {
    override fun NextBuilder.update(event: Feat2Event) {}
}
