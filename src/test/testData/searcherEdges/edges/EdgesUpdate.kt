package edges

import ru.tinkoff.kotea.core.dsl.DslUpdate

class EdgesUpdate : DslUpdate<EdgesState, EdgesEvent, EdgesCommand, EdgesNews>() {

    override fun NextBuilder.update(event: EdgesEvent) {
        when (event) {
            is ItemTapped -> {
                commands(LoadPage(event.id))
                commands(LoadPage(event.id + 1))
            }
            else -> {}
        }
    }
}
