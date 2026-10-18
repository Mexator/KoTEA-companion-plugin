package rh

class RhScreen {

    private val sink = ArrayList<RhEvent>()

    fun onReset() {
        sink += RhEvent.Reset
    }

    fun onClicks(ids: List<Int>) {
        sink += ids.map(RhEvent::Clicked)
    }

    fun track(clicked: RhEvent.Clicked) {
        println(RhEvent.Refresh::class)
    }

    fun onNews(news: RhNews) {
        when (news) {
            RhNews.Toast -> {}
        }
    }
}
