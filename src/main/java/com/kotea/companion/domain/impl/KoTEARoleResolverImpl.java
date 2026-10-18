package com.kotea.companion.domain.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.TypeConversionUtil;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.util.KoTEAClassSupport;
import com.kotea.companion.util.KoTEAElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

/**
 * An Event or News is emitted where it is used as a value and processed where it is checked against. A Command is
 * emitted the same way but processed only inside a {@code CommandsFlowHandler} that handles it.
 */
public final class KoTEARoleResolverImpl implements KoTEARoleResolver {

    static final String COMMANDS_FLOW_HANDLER_FQN = "ru.tinkoff.kotea.core.CommandsFlowHandler";

    private static final TokenSet EQUALITY_OPERATIONS =
            TokenSet.create(KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ);

    private final Project project;

    public KoTEARoleResolverImpl(@NotNull Project project) {
        this.project = project;
    }

    @Nullable
    @Override
    public Role roleOf(@NotNull PsiElement referenceSite) {
        KtNameReferenceExpression ref = referenceSite instanceof KtNameReferenceExpression expression ? expression
                : referenceSite.getParent() instanceof KtNameReferenceExpression expression ? expression : null;
        if (ref == null) return null;
        if (PsiTreeUtil.getParentOfType(ref, KtImportDirective.class, KtSuperTypeList.class) != null) return null;

        KtClassOrObject target = KoTEAClassSupport.resolveClassOrObject(ref);
        if (target == null) return null;
        KoTEAElementKind kind = KoTEAClassifier.getInstance(project).kindOf(target);
        if (kind == null) return null;

        Role role = roleByUsage(ref);
        if (kind != KoTEAElementKind.COMMAND) return role;
        if (isInsideHandlerOf(ref, target)) return Role.PROCESSING;
        return role == Role.EMISSION ? Role.EMISSION : null;
    }

    @Nullable
    private static Role roleByUsage(KtNameReferenceExpression ref) {
        PsiElement parent = ref.getParent();
        if (parent instanceof KtUserType type) return roleByType(type);
        if (parent instanceof KtCallExpression call && call.getCalleeExpression() == ref) return Role.EMISSION;
        if (parent instanceof KtCallableReferenceExpression callable) {
            return callable.getCallableReference() == ref ? Role.EMISSION : null;
        }

        KtExpression expression = ref;
        if (parent instanceof KtQualifiedExpression qualified) {
            if (qualified.getSelectorExpression() != ref) return null;
            expression = qualified;
        }
        PsiElement context = expression.getParent();
        if (context instanceof KtDoubleColonExpression) return null;
        if (context instanceof KtWhenConditionWithExpression) return Role.PROCESSING;
        if (context instanceof KtBinaryExpression binary
                && EQUALITY_OPERATIONS.contains(binary.getOperationToken())) return Role.PROCESSING;
        return Role.EMISSION;
    }

    /** {@code is}, {@code !is}, {@code as} and {@code as?} check against a type; any other type usage is no role. */
    @Nullable
    private static Role roleByType(KtUserType type) {
        PsiElement owner = type.getParent();
        if (owner instanceof KtNullableType) owner = owner.getParent();
        if (!(owner instanceof KtTypeReference typeReference)) return null;

        PsiElement usage = typeReference.getParent();
        return usage instanceof KtIsExpression || usage instanceof KtWhenConditionIsPattern
                || usage instanceof KtBinaryExpressionWithTypeRHS ? Role.PROCESSING : null;
    }

    private static boolean isInsideHandlerOf(PsiElement site, KtClassOrObject command) {
        PsiClass commandClass = LightClassUtilsKt.toLightClass(command);
        if (commandClass == null) return false;
        PsiType commandType = JavaPsiFacade.getElementFactory(command.getProject()).createType(commandClass);

        for (KtClassOrObject enclosing = PsiTreeUtil.getParentOfType(site, KtClassOrObject.class);
             enclosing != null;
             enclosing = PsiTreeUtil.getParentOfType(enclosing, KtClassOrObject.class)) {
            PsiType handledType = handledCommandType(enclosing);
            if (handledType != null && handledType.isAssignableFrom(commandType)) return true;
        }
        return false;
    }

    /** The Command type argument {@code handler} passes to {@code CommandsFlowHandler}, or null if it is not one. */
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
        PsiClass flowHandler = JavaPsiFacade.getInstance(handler.getProject())
                .findClass(COMMANDS_FLOW_HANDLER_FQN, handler.getResolveScope());
        if (flowHandler == null || !InheritanceUtil.isInheritorOrSelf(handlerClass, flowHandler, true)) return null;

        PsiTypeParameter[] parameters = flowHandler.getTypeParameters();
        if (parameters.length == 0) return null;
        PsiSubstitutor substitutor =
                TypeConversionUtil.getSuperClassSubstitutor(flowHandler, handlerClass, PsiSubstitutor.EMPTY);
        return substitutor.substitute(parameters[0]);
    }
}
