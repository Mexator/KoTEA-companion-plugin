package com.kotea.companion.fixtures;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the real {@code ru.tinkoff.kotea:core} artifact to the fixture module (ADR-0001). One
 * {@code static final} instance so the light project and its library are built once for the suite.
 */
public final class KoTEAProjectDescriptor extends DefaultLightProjectDescriptor {

    public static final KoTEAProjectDescriptor INSTANCE = new KoTEAProjectDescriptor();

    private KoTEAProjectDescriptor() {
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        MavenDependencyUtil.addFromMaven(model, "ru.tinkoff.kotea:core:1.3.0");
    }
}
