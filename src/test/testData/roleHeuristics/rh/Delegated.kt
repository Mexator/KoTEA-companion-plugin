package rh

/** A lambda passed to the delegate of a supertype entry is an ordinary value usage. */
interface RhFeatureConfiguration

fun configureRh(mapper: () -> RhEvent?): RhFeatureConfiguration = object : RhFeatureConfiguration {}

class RhDelegated : RhFeatureConfiguration by configureRh({ RhEvent.Reset })
