package com.kotea.companion.index.resolve;

import com.kotea.companion.index.IndexLog;
import com.kotea.companion.index.ScanTimings;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtConstructor;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jetbrains.kotlin.psi.KtTypeAlias;
import org.jetbrains.kotlin.psi.KtUserType;

/**
 * The slow path, through Kotlin analysis. Used only where reading imports cannot settle a name, and each use is
 * logged.
 */
final class AnalysisFallback {

    private AnalysisFallback() {
    }

    /** The FQN {@code type} names, by resolving its reference. */
    @Nullable
    static String fqnOf(KtUserType type, KtFile file, String unsureBecause) {
        long start = System.nanoTime();
        String fqn = ScanTimings.time(ScanTimings.Step.FALLBACK, () -> fqnByReference(type.getReferenceExpression()));
        IndexLog.debug(() -> "Resolve fallback for " + type.getReferencedName() + " in "
                + IndexLog.mask(file.getPackageFqName().asString() + "." + file.getName()) + ": " + unsureBecause
                + ", " + IndexLog.ms(start) + " ms, resolved to " + (fqn != null ? IndexLog.mask(fqn) : "nothing"));
        return fqn;
    }

    @Nullable
    private static String fqnByReference(@Nullable KtReferenceExpression ref) {
        PsiReference psiRef = ref != null ? ref.getReference() : null;
        PsiElement resolved = psiRef != null ? psiRef.resolve() : null;
        return switch (resolved) {
            case KtClassOrObject ktClass -> asString(ktClass.getFqName());
            case KtConstructor<?> constructor -> asString(constructor.getContainingClassOrObject().getFqName());
            case KtTypeAlias alias -> asString(alias.getFqName());
            case PsiClass psiClass -> psiClass.getQualifiedName();
            case null, default -> null;
        };
    }

    @Nullable
    private static String asString(@Nullable FqName fqName) {
        return fqName != null ? fqName.asString() : null;
    }
}
