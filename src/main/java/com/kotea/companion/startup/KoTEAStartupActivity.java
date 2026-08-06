package com.kotea.companion.startup;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.kotea.companion.index.KoTEAIndexService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public class KoTEAStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Indexing KoTEA events/commands", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        ReadAction.run(() -> KoTEAIndexService.getInstance(project).getIndex());
                    }
                }));
        return Unit.INSTANCE;
    }
}
