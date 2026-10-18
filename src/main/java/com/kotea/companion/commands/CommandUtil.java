package com.kotea.companion.commands;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.KoTEAClassSupport;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;

public class CommandUtil {

    /**
     * Returns true if passed ktClass is a Command class for which this plugin provides navigation actions
     */
    public static boolean isNavigableCommandClass(KtClassOrObject ktClass) {
        if (KoTEAClassifier.getInstance(ktClass.getProject()).kindOf(ktClass) != KoTEAElementKind.COMMAND) return false;
        PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
        return lightClass != null && !lightClass.isInterface() && !lightClass.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        KtClassOrObject candidate = KoTEAClassSupport.resolveClassOrObject(element);
        return candidate != null && isNavigableCommandClass(candidate) ? candidate : null;
    }
}
