package nr

import ru.tinkoff.kotea.core.dsl.DslUpdate

class NrUpdate : DslUpdate<NrState, NrEvent, NrCommand, NrNews>() {
    override fun NextBuilder.update(event: NrEvent) {}
}
