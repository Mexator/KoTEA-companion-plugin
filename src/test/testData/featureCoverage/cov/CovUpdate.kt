package cov

import ru.tinkoff.kotea.core.dsl.DslUpdate

class CovUpdate : DslUpdate<CovState, FeatureEvent, FeatureCommand, FeatureNews>() {

    override fun NextBuilder.update(event: FeatureEvent) {
        when (event) {
            // Event Processing site for ItemClicked; Command Emission site for LoadItems.
            is ItemClicked -> commands(LoadItems(event.id))
            // Event Processing site for BackPressed; News Emission site for ShowError.
            is BackPressed -> news(ShowError("back"))
            else -> {}
        }
    }
}
