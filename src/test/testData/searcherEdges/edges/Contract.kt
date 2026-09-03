package edges

/** The state a screen shows while it waits for [ItemTapped] to produce a [LoadPage]. */
data class EdgesState(val loading: Boolean = false)

data class EdgesNews(val text: String)
