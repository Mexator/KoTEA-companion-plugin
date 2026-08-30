package bare

import ru.tinkoff.kotea.core.Next
import ru.tinkoff.kotea.core.Update

class BareUpdate : Update<BareState, BareEvent, BareCommand, Nothing> {
    override fun update(state: BareState, event: BareEvent): Next<BareState, BareCommand, Nothing> = Next()
}
