package feat1

// A source file with content that declares no Update. The "delete a non-Update file"
// test removes this and expects the snapshot to stay put.
data class SomethingLogged(val name: String, val count: Int)

fun formatLogged(logged: SomethingLogged): String = "${logged.name} x${logged.count}"
