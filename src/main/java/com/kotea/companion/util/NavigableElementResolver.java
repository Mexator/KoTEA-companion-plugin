package com.kotea.companion.util;

import com.intellij.psi.PsiElement;
import com.kotea.companion.commands.CommandUtil;
import com.kotea.companion.events.EventUtil;
import com.kotea.companion.news.NewsUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;

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
        KtClassOrObject newsClass = NewsUtil.tryResolveToClass(element);
        if (newsClass != null) {
            return new NavigableElement(KoTEAElementKind.NEWS, newsClass);
        }
        KtClassOrObject commandClass = CommandUtil.tryResolveToClass(element);
        if (commandClass != null) {
            return new NavigableElement(KoTEAElementKind.COMMAND, commandClass);
        }
        return null;
    }
}
