package com.kotea.companion.util;

import com.intellij.psi.PsiElement;
import com.kotea.companion.commands.CommandUtil;
import com.kotea.companion.events.EventUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

/**
 * Resolves a PSI element to the {@link NavigableElement} (if any).
 * Answers the question: "is a PSI element a KoTEA class and what is its kind (Event or a Command)?"
 */
public final class NavigableElementResolver {

    private NavigableElementResolver() {
    }

    @Nullable
    public static NavigableElement resolve(PsiElement element) {
        KtClassOrObject eventClass = EventUtil.tryResolveToClass(element);
        if (eventClass != null) {
            return new NavigableElement(KoTEAElementKind.EVENT, eventClass);
        }
        return resolveCommand(element);
    }

    @Nullable
    private static NavigableElement resolveCommand(PsiElement element) {
        UElement uElement = UastContextKt.toUElement(element, UElement.class);
        if (uElement == null) return null;

        UClass uClass = null;
        if (uElement instanceof UMethod method && method.isConstructor()) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class);
        }
        if (uClass == null) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class, false);
        }
        if (uClass == null || !CommandUtil.isNavigableCommand(uClass.getJavaPsi())) return null;

        return uClass.getSourcePsi() instanceof KtClassOrObject ktClass
                ? new NavigableElement(KoTEAElementKind.COMMAND, ktClass)
                : null;
    }
}
