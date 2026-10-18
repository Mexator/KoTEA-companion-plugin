package com.kotea.companion.domain;

import com.intellij.openapi.project.Project;
import com.kotea.companion.util.KoTEAElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;

public interface KoTEAClassifier {

    static KoTEAClassifier getInstance(@NotNull Project project) {
        return project.getService(KoTEAClassifier.class);
    }

    /** Returns the kind of {@code candidate}, if it represents any KoTEA entity or {@code null} otherwise */
    @Nullable
    KoTEAElementKind kindOf(@Nullable KtClassOrObject candidate);
}
