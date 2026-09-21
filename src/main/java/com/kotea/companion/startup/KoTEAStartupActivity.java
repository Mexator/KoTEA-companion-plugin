package com.kotea.companion.startup;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
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

    private static final Logger LOG = Logger.getInstance(KoTEAStartupActivity.class);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId("com.kotea.companion"));
        if (plugin != null) LOG.info(plugin.getName() + " " + plugin.getVersion() + " started");

        KoTEAIndexService service = KoTEAIndexService.getInstance(project);
        service.registerListeners();
        service.scanProject();
        return Unit.INSTANCE;
    }
}
