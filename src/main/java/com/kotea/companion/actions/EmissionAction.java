package com.kotea.companion.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.commands.CommandEmissionSearcher;
import com.kotea.companion.events.EventEmissionSearcher;
import com.kotea.companion.util.KoTEAUtil;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.util.List;

public class EmissionAction extends BaseAction {

    @Override
    protected List<PsiElement> findTargets(PsiElement targetClass, GlobalSearchScope scope) {
        if (KoTEAUtil.isEvent(targetClass)) {
            return EventEmissionSearcher.findEmissions((KtClassOrObject) targetClass, scope);
        }

        if (KoTEAUtil.isCommand(targetClass)) {
            UClass uClass = UastContextKt.toUElement(targetClass, UClass.class);
            return uClass != null ? CommandEmissionSearcher.findEmission(uClass, scope) : List.of();
        }

        return List.of();
    }

    @Override
    protected String getTitle() {
        return "Emission Sites";
    }

    @Override
    protected String getOperation() {
        return "Emission";
    }
}
