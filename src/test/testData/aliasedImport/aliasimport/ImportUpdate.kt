package aliasimport

import ru.tinkoff.kotea.core.dsl.DslUpdate as KoteaUpdate

data class ImState(val x: Int = 0)

sealed interface ImEvent
data class ImClicked(val id: Int) : ImEvent

sealed interface ImCommand
data class ImLoad(val page: Int) : ImCommand

data class ImNews(val text: String)

class ImportUpdate : KoteaUpdate<ImState, ImEvent, ImCommand, ImNews>() {
    override fun NextBuilder.update(event: ImEvent) {}
}
