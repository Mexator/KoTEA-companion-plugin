package rh

class Analytics {

    fun report(event: RhEvent) {
        if (event is RhEvent.Reset) println("reset")
        val clicked = event as? RhEvent.Clicked
        println(clicked)
    }

    fun log(command: RhCommand) {
        if (command is RhCommand.Load) println("load")
    }
}
