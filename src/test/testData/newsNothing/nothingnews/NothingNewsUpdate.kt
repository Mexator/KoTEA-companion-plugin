package nothingnews

import ru.tinkoff.kotea.core.dsl.DslUpdate

class NothingNewsUpdate : DslUpdate<NnState, NnEvent, NnCommand, Nothing>() {
    override fun NextBuilder.update(event: NnEvent) {}
}
