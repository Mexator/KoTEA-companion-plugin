package com.kotea.companion.domain;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the {@link Marker} (gutter icon) a PsiElement gets.
 * E.g., an emission site has an icon to go to processing sites and vice versa.
 */
public interface KoTEAMarkerResolver {

    enum Marker {TO_EMISSION, TO_PROCESSING}

    static KoTEAMarkerResolver getInstance(@NotNull Project project) {
        return project.getService(KoTEAMarkerResolver.class);
    }

    @Nullable
    Marker markerFor(@NotNull PsiElement referenceSite);
}
