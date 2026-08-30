package feat1

import ru.tinkoff.kotea.core.dsl.DslUpdate

class Feat1Update : DslUpdate<Feat1State, Feat1Event, Feat1Command, Feat1News>() {
    override fun NextBuilder.update(event: Feat1Event) {
        /* {body} */
    }
}
