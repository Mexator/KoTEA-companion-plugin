package com.kotea.companion.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.util.PerfLog;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public class KoTEAStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(KoTEAStartupActivity.class);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Indexing KoTEA events/commands", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        long start = PerfLog.start();
                        KoTEAIndexService.getInstance(project).requestFullRebuild();
                        PerfLog.logElapsed(LOG, "KoTEA startup indexing requested", start);
                    }
                }));
        return Unit.INSTANCE;
    }
}
