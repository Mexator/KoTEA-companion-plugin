package com.kotea.companion.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.kotea.companion.commands.CommandUtil;
import com.kotea.companion.events.EventUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.*;

public class KoTEAUtil {

    @Nullable
    public static PsiElement tryResolveToKoTEAClass(PsiElement element) {
        // Try Event first
        KtClassOrObject eventClass = EventUtil.tryResolveToClass(element);
        if (eventClass != null) return eventClass;

        UElement uElement = UastContextKt.toUElement(element, UElement.class);
        if (uElement == null) return null;

        UClass uClass = null;
        if (uElement instanceof UMethod method && method.isConstructor()) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class);
        }
        if (uClass == null) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class, false);
        }

        if (uClass != null) {
            PsiClass psiClass = uClass.getJavaPsi();
            if (CommandUtil.isNavigableCommand(psiClass)) {
                return uClass.getSourcePsi();
            }
        }

        return null;
    }
}
