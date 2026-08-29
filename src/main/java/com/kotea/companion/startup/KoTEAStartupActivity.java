package com.kotea.companion.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.kotea.companion.index.KoTEAIndexService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * Eagerly instantiates {@link KoTEAIndexService} at project open so its PSI/VFS/module-root
 * listeners are live and the initial full rebuild is scheduled (see the service constructor)
 * before the user navigates - rather than lazily on the first gutter or keyboard navigation.
 */
public class KoTEAStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        KoTEAIndexService.getInstance(project);
        return Unit.INSTANCE;
    }
}
