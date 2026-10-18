package com.kotea.companion.domain;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the {@link Role} a reference to a KoTEA class plays: an <b>Emission</b> or a <b>Processing</b>.
 */
public interface KoTEARoleResolver {

    enum Role {EMISSION, PROCESSING}

    static KoTEARoleResolver getInstance(@NotNull Project project) {
        return project.getService(KoTEARoleResolver.class);
    }

    @Nullable
    Role roleOf(@NotNull PsiElement referenceSite);
}
