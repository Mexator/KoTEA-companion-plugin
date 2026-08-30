package edges.ui

import edges.EdgesEvent
import edges.ItemTapped
import edges.LoadPage
import edges.Marker

@Marker(LoadPage::class)
class EdgesScreen {

    private val events = ArrayList<EdgesEvent>()

    fun onTap(id: Int) {
        events += ItemTapped(id)
        events += ItemTapped(id + 1)
    }

    fun record(e: ItemTapped) {
        events += e
    }

    fun pending(): List<LoadPage> = emptyList()
}
