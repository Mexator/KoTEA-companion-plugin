package com.kotea.companion.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.kotea.companion.index.KoTEAIndexService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * Eagerly instantiates {@link KoTEAIndexService} at project open
 */
public class KoTEAStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        KoTEAIndexService.getInstance(project).registerListeners();
        return Unit.INSTANCE;
    }
}
