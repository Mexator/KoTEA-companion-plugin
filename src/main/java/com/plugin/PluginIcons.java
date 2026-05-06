package com.plugin;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public interface PluginIcons {
    Icon EMISSION = IconLoader.getIcon("/icons/emission.svg", PluginIcons.class);
    Icon PROCESSING = IconLoader.getIcon("/icons/processing.svg", PluginIcons.class);
}