package com.kotea.companion.commands;

import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.KoTEAClassSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.*;

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

    public static boolean isCommandsHandler(@NotNull KtClassOrObject handler, @NotNull KtClassOrObject command) {
        PsiType handledType = handledCommandType(handler);
        if (handledType == null) return false;
        PsiClass commandClass = LightClassUtilsKt.toLightClass(command);
        if (commandClass == null) return false;
        PsiType commandType = JavaPsiFacade.getElementFactory(command.getProject()).createType(commandClass);
        return handledType.isAssignableFrom(commandType);
    }

    public static boolean hasHandleFunction(@NotNull KtClassOrObject handler) {
        return handler.getDeclarations().stream()
                .anyMatch(declaration -> declaration instanceof KtNamedFunction function
                        && "handle".equals(function.getName()));
    }

    /** The first type argument of the {@code CommandsFlowHandler} supertype, or null if {@code handler} is not one. */
    @Nullable
    private static PsiType handledCommandType(KtClassOrObject handler) {
        return CachedValuesManager.getCachedValue(handler, () ->
                CachedValueProvider.Result.create(computeHandledCommandType(handler),
                        PsiModificationTracker.MODIFICATION_COUNT));
    }

    @Nullable
    private static PsiType computeHandledCommandType(KtClassOrObject handler) {
        PsiClass handlerClass = LightClassUtilsKt.toLightClass(handler);
        if (handlerClass == null) return null;
        for (PsiClassType superType : handlerClass.getSuperTypes()) {
            PsiClass superClass = superType.resolve();
            if (superClass == null || !"CommandsFlowHandler".equals(superClass.getName())) continue;
            PsiType[] params = superType.getParameters();
            if (params.length > 0) return params[0];
        }
        return null;
    }
}
