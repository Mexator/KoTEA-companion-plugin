package unitroots

import ru.tinkoff.kotea.core.dsl.DslUpdate

class UnitCommandsUpdate : DslUpdate<UrState, UrEvent, Unit, Any>() {
    override fun NextBuilder.update(event: UrEvent) {}
}
