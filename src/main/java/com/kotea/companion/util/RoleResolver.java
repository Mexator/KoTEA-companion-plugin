package com.kotea.companion.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the {@link Role} a reference plays: an <b>Emission</b>  or a <b>Processing</b>.
 */
public final class RoleResolver {

    public enum Role {EMISSION, PROCESSING}

    private RoleResolver() {
    }

    public static Role roleOf(@NotNull PsiElement referenceSite, @NotNull KoTEAElementKind kind) {
        PsiFile file = referenceSite.getContainingFile();
        return roleOfFileName(file != null ? file.getName() : "", kind);
    }

    static Role roleOfFileName(@NotNull String fileName, @NotNull KoTEAElementKind kind) {
        return switch (kind) {
            // Events are processed inside the Update class, emitted everywhere else.
            case EVENT -> fileName.contains("Update") ? Role.PROCESSING : Role.EMISSION;
            // Commands are processed inside a CommandsFlowHandler, emitted everywhere else
            case COMMAND -> fileName.contains("Handler") ? Role.PROCESSING : Role.EMISSION;
            // News is constructed inside the Update class and processed everywhere else
            case NEWS -> fileName.contains("Update") ? Role.EMISSION : Role.PROCESSING;
        };
    }
}
