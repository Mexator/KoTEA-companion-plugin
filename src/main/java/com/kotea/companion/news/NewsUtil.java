package com.kotea.companion.news;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.KoTEAClassSupport;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;

public class NewsUtil {

    /**
     * Returns true if passed ktClass is a News class for which this plugin provides navigation actions
     */
    public static boolean isNavigableNewsClass(KtClassOrObject ktClass) {
        if (KoTEAClassifier.getInstance(ktClass.getProject()).kindOf(ktClass) != KoTEAElementKind.NEWS) return false;
        PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
        return lightClass != null && !lightClass.isInterface() && !lightClass.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        KtClassOrObject candidate = KoTEAClassSupport.resolveClassOrObject(element);
        return candidate != null && isNavigableNewsClass(candidate) ? candidate : null;
    }
}
