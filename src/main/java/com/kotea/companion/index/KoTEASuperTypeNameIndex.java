package com.kotea.companion.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.PsiDependentFileContent;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Records, per Kotlin file and with no cross-file PSI resolution, the literal simple names written
 * in each class declaration's supertype list (e.g. {@code "Update"} for {@code : Update<...>()}),
 * alongside a hash of that supertype clause's full text (type arguments included) so the index's
 * per-file data - and therefore {@link FileBasedIndex#getIndexModificationStamp}, which
 * {@link KoTEAIndexService} depends on - changes not just when a class starts or stops extending a
 * given name, but also when its generic arguments (e.g. the Event/Command types) are edited. This is
 * a purely syntactic, per-file-incremental primitive: {@link KoTEAIndexComputer} uses it to walk from
 * {@code Update} down to project classes actually related to that hierarchy, without a project-wide
 * {@code ClassInheritorsSearch}.
 */
public final class KoTEASuperTypeNameIndex extends FileBasedIndexExtension<String, Integer> {

    public static final ID<String, Integer> NAME = ID.create("com.kotea.companion.superTypeName");

    @Override
    public @NotNull ID<String, Integer> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Integer, FileContent> getIndexer() {
        return fileContent -> {
            if (!(fileContent instanceof PsiDependentFileContent psiDependentFileContent)) return Map.of();
            if (!(psiDependentFileContent.getPsiFile() instanceof KtFile ktFile)) return Map.of();

            Map<String, Integer> result = new HashMap<>();
            forEachLiteralSuperType(ktFile, (classOrObject, entry) -> {
                String name = literalSuperTypeSimpleName(entry);
                if (name != null) result.merge(name, entry.getText().hashCode(), Integer::sum);
            });
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<Integer> getValueExternalizer() {
        return new DataExternalizer<>() {
            @Override
            public void save(@NotNull DataOutput out, Integer value) throws IOException {
                out.writeInt(value);
            }

            @Override
            public Integer read(@NotNull DataInput in) throws IOException {
                return in.readInt();
            }
        };
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(KotlinFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Class-or-object declarations (named or anonymous) in {@code file} whose immediate, literal
     * supertype list mentions any of {@code targetNames}. Runs the same traversal as the indexer
     * above, but over a single already-identified file's PSI instead of the whole project.
     */
    public static List<KtClassOrObject> classesWithLiteralSuperType(KtFile file, Set<String> targetNames) {
        List<KtClassOrObject> result = new ArrayList<>();
        forEachLiteralSuperType(file, (classOrObject, entry) -> {
            String name = literalSuperTypeSimpleName(entry);
            if (name != null && targetNames.contains(name) && !result.contains(classOrObject)) {
                result.add(classOrObject);
            }
        });
        return result;
    }

    private static void forEachLiteralSuperType(KtFile file, BiConsumer<KtClassOrObject, KtSuperTypeListEntry> consumer) {
        file.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitClassOrObject(@NotNull KtClassOrObject classOrObject) {
                super.visitClassOrObject(classOrObject);
                for (KtSuperTypeListEntry entry : classOrObject.getSuperTypeListEntries()) {
                    consumer.accept(classOrObject, entry);
                }
            }
        });
    }

    @Nullable
    private static String literalSuperTypeSimpleName(KtSuperTypeListEntry entry) {
        KtTypeReference typeRef = entry.getTypeReference();
        KtTypeElement typeElement = typeRef != null ? typeRef.getTypeElement() : null;
        return typeElement instanceof KtUserType userType ? userType.getReferencedName() : null;
    }
}
