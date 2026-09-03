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
        return fileName.contains(processingFileToken(kind)) ? Role.PROCESSING : Role.EMISSION;
    }

    private static String processingFileToken(KoTEAElementKind kind) {
        return switch (kind) {
            case EVENT -> "Update";
            case COMMAND -> "Handler";
        };
    }
}
