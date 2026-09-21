package com.kotea.companion.index.resolve;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtUserType;

import java.util.List;
import java.util.Set;

/**
 * Finds the FQN a simple name in a file stands for from the file's imports and package, without Kotlin analysis.
 * The rules run in Kotlin's own order; the first one that decides wins.
 */
final class NameLookupChain {

    /** Decides for {@code name}, or returns {@code null} to leave it to the next rule. */
    private interface Rule {
        @Nullable
        NameLookup lookup(KtUserType type, KtFile file, String name);
    }

    private static final Set<String> KOTLIN_DEFAULTS = Set.of("Any", "Nothing", "Unit");

    private final TypeIndexes indexes;
    private final List<Rule> rules = List.of(
            NameLookupChain::nestedClass,
            NameLookupChain::explicitImport,
            this::samePackage,
            this::starImport,
            NameLookupChain::kotlinDefault);

    NameLookupChain(TypeIndexes indexes) {
        this.indexes = indexes;
    }

    NameLookup lookup(KtUserType type, KtFile file, String name) {
        for (Rule rule : rules) {
            NameLookup result = rule.lookup(type, file, name);
            if (result != null) return result;
        }
        return new NameLookup.Unsure("not found in imports, package or indexes");
    }

    /**
     * A class nested in an enclosing class takes priority over imports, the nearest one first. A class nested in
     * the class whose supertype list holds {@code type} is left to Kotlin analysis.
     */
    @Nullable
    private static NameLookup nestedClass(KtUserType type, KtFile file, String name) {
        KtClassOrObject owner = PsiTreeUtil.getParentOfType(type, KtClassOrObject.class);
        if (owner != null && declaresNestedClass(owner, name)) {
            return new NameLookup.Unsure("a class nested in the same class has this name");
        }
        for (KtClassOrObject outer = owner != null ? PsiTreeUtil.getParentOfType(owner, KtClassOrObject.class) : null;
             outer != null; outer = PsiTreeUtil.getParentOfType(outer, KtClassOrObject.class)) {
            if (!declaresNestedClass(outer, name)) continue;
            FqName outerFqn = outer.getFqName();
            return outerFqn != null ? new NameLookup.Found(outerFqn.asString() + "." + name)
                    : new NameLookup.Unsure("a class nested in a local class has this name");
        }
        return null;
    }

    private static boolean declaresNestedClass(KtClassOrObject owner, String name) {
        for (KtDeclaration declaration : owner.getDeclarations()) {
            if (declaration instanceof KtClassOrObject nested && name.equals(nested.getName())) return true;
        }
        return false;
    }

    @Nullable
    private static NameLookup explicitImport(KtUserType type, KtFile file, String name) {
        for (KtImportDirective directive : file.getImportDirectives()) {
            Name imported = directive.getImportedName();
            FqName importedFqn = directive.getImportedFqName();
            if (!directive.isAllUnder() && imported != null && importedFqn != null && name.equals(imported.asString())) {
                return new NameLookup.Found(importedFqn.asString());
            }
        }
        return null;
    }

    @Nullable
    private NameLookup samePackage(KtUserType type, KtFile file, String name) {
        String packageName = file.getPackageFqName().asString();
        String fqn = packageName.isEmpty() ? name : packageName + "." + name;
        return indexes.exists(fqn) ? new NameLookup.Found(fqn) : null;
    }

    @Nullable
    private NameLookup starImport(KtUserType type, KtFile file, String name) {
        String found = null;
        for (KtImportDirective directive : file.getImportDirectives()) {
            FqName importedPackage = directive.getImportedFqName();
            if (!directive.isAllUnder() || importedPackage == null) continue;
            String candidate = importedPackage.asString() + "." + name;
            if (!indexes.exists(candidate)) continue;
            if (found != null) return new NameLookup.Unsure("two star imports provide it");
            found = candidate;
        }
        return found != null ? new NameLookup.Found(found) : null;
    }

    @Nullable
    private static NameLookup kotlinDefault(KtUserType type, KtFile file, String name) {
        return KOTLIN_DEFAULTS.contains(name) ? new NameLookup.Found("kotlin." + name) : null;
    }
}
