package com.kotea.companion.domain.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.domain.KoTEAMarkerResolver;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.index.model.KoTEABases;
import com.kotea.companion.util.ClassReferenceResolver;
import com.kotea.companion.util.KoTEAElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

public final class KoTEAMarkerResolverImpl implements KoTEAMarkerResolver {

    private final Project project;

    public KoTEAMarkerResolverImpl(@NotNull Project project) {
        this.project = project;
    }

    @Nullable
    @Override
    public Marker markerFor(@NotNull PsiElement referenceSite) {
        Role role = KoTEARoleResolver.getInstance(project).roleOf(referenceSite);
        if (role != null) return role == Role.EMISSION ? Marker.TO_PROCESSING : Marker.TO_EMISSION;

        // handle*(MyEvent) helper functions in Updates
        if (isUpdateHelperFunction(referenceSite)) return Marker.TO_EMISSION;

        return null;
    }

    private boolean isUpdateHelperFunction(PsiElement referenceSite) {
        KtNameReferenceExpression ref = PsiTreeUtil.getParentOfType(referenceSite, KtNameReferenceExpression.class, false);
        if (ref == null) return false;

        KtParameter parameter = PsiTreeUtil.getParentOfType(ref, KtParameter.class);
        if (parameter == null) return false;

        KtTypeReference typeReference = parameter.getTypeReference();
        KtTypeElement declared = typeReference != null ? typeReference.getTypeElement() : null;
        if (declared instanceof KtNullableType nullable) declared = nullable.getInnerType();
        if (!(declared instanceof KtUserType userType) || userType.getReferenceExpression() != ref) return false;

        if (parameter.getOwnerFunction() instanceof KtNamedFunction namedFunction) {
            KtClassOrObject klass = PsiTreeUtil.getParentOfType(namedFunction, KtClassOrObject.class);
            return klass != null && isUpdate(klass) && isReferenceToEvent(ref);
        }
        return false;
    }

    private boolean isReferenceToEvent(KtNameReferenceExpression ref) {
        var refTarget = ClassReferenceResolver.resolveClassOrObject(ref);
        return KoTEAClassifier.getInstance(project).kindOf(refTarget) == KoTEAElementKind.EVENT;
    }

    private static boolean isUpdate(KtClassOrObject candidate) {
        return CachedValuesManager.getCachedValue(candidate, () -> {
            PsiClass lightClass = LightClassUtilsKt.toLightClass(candidate);
            return CachedValueProvider.Result.create(
                    InheritanceUtil.isInheritor(lightClass, KoTEABases.UPDATE_FQN),
                    PsiModificationTracker.MODIFICATION_COUNT);
        });
    }
}
