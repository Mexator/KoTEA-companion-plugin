package com.kotea.companion.actions;

import com.kotea.companion.domain.KoTEARoleResolver.Role;

public class GoToProcessingAction extends BaseGoToAction {

    public GoToProcessingAction() {
        super(Role.PROCESSING);
    }
}
