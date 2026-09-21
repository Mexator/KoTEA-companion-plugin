package com.kotea.companion.index.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex;
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtTypeAlias;

/** Lookups by FQN in the Kotlin and Java indexes, over the whole project and its libraries. */
final class TypeIndexes {

    private final Project project;
    private final GlobalSearchScope scope;

    TypeIndexes(Project project) {
        this.project = project;
        this.scope = GlobalSearchScope.allScope(project);
    }

    /** A Kotlin source class or typealias, else a Java or compiled library class. */
    boolean exists(String fqn) {
        return !StubIndex.getElements(KotlinFullClassNameIndex.Helper.getIndexKey(), fqn, project, scope,
                KtClassOrObject.class).isEmpty() || typealias(fqn) != null
                || JavaPsiFacade.getInstance(project).findClass(fqn, scope) != null;
    }

    @Nullable
    KtTypeAlias typealias(String fqn) {
        for (KtTypeAlias alias : StubIndex.getElements(KotlinTopLevelTypeAliasFqNameIndex.Helper.getIndexKey(), fqn,
                project, scope, KtTypeAlias.class)) {
            return alias;
        }
        return null;
    }
}
