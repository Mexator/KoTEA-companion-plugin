package com.kotea.companion.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KoTEAIndexComputerTest extends KoTEAFixtureTestCase {

    private static final String DSL_UPDATE_FQN = "ru.tinkoff.kotea.core.dsl.DslUpdate";

    public void testComputeAll_featureUpdate_yieldsOneRecordGroupedByItsFile() {
        myFixture.copyDirectoryToProject("featureUpdate", "");

        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        VirtualFile updateFile = myFixture.findFileInTempDir("feature/MyFeatureUpdate.kt");
        assertNotNull("fixture file not found in temp dir", updateFile);

        List<UpdateRecord> records = byFile.get(updateFile);
        assertNotNull("no records grouped under " + updateFile, records);
        assertSize(1, records);

        UpdateRecord record = records.get(0);
        assertEquals("feature.MyFeatureUpdate", record.fqn());
        assertEquals("feature.FeatureEvent", record.eventRootFqn());
        assertEquals("feature.FeatureCommand", record.commandRootFqn());
        assertContainsElements(record.updateAncestorFqns(), DSL_UPDATE_FQN);
    }

    public void testComputeAll_abstractIntermediate_contributesNoRoot() {
        myFixture.copyDirectoryToProject("intermediateUpdate", "");

        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        UpdateRecord profile = recordFor(byFile, "intermediate.ProfileUpdate");
        assertContainsElements(profile.updateAncestorFqns(), "intermediate.BaseFeatureUpdate");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(byFile.values());
        assertEquals(
                new KoTEARootsIndex(
                        Set.of("intermediate.ProfileEvent"), Set.of("intermediate.ProfileCommand"), Set.of()),
                index);
    }

    public void testComputeAll_newsIsNothing_eventAndCommandRootsStillResolve() {
        myFixture.copyDirectoryToProject("newsNothing", "");

        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        UpdateRecord record = recordFor(byFile, "nothingnews.NothingNewsUpdate");
        assertEquals("nothingnews.NnEvent", record.eventRootFqn());
        assertEquals("nothingnews.NnCommand", record.commandRootFqn());
        assertNull("Nothing bound to News yields no News Root", record.newsRootFqn());
    }

    public void testComputeAll_newsIsNothing_directUpdateImplementation_eventAndCommandRootsStillResolve() {
        myFixture.copyDirectoryToProject("bareUpdate", "");

        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        UpdateRecord record = recordFor(byFile, "bare.BareUpdate");
        assertEmpty(record.updateAncestorFqns());
        assertEquals("bare.BareEvent", record.eventRootFqn());
        assertEquals("bare.BareCommand", record.commandRootFqn());
        assertNull("Nothing bound to News yields no News Root", record.newsRootFqn());

        KoTEARootsIndex index = KoTEAIndexComputer.derive(byFile.values());
        assertEquals(new KoTEARootsIndex(Set.of("bare.BareEvent"), Set.of("bare.BareCommand"), Set.of()), index);
    }

    public void testComputeForFile_matchesComputeAll_withTypealiasedAndAliasImportedRoots() {
        myFixture.copyDirectoryToProject("typealiasImport", "");

        VirtualFile updateFile = myFixture.findFileInTempDir("alias/AliasUpdate.kt");
        assertNotNull("fixture file not found in temp dir", updateFile);

        List<UpdateRecord> forFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeForFile(getProject(), updateFile));
        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        assertEquals(new HashSet<>(byFile.get(updateFile)), new HashSet<>(forFile));

        assertSize(1, forFile);
        UpdateRecord record = forFile.get(0);
        assertEquals("alias.RealEvent", record.eventRootFqn());
        assertEquals("alias.contract.RealCommand", record.commandRootFqn());
    }

    private static UpdateRecord recordFor(Map<VirtualFile, List<UpdateRecord>> byFile, String fqn) {
        return byFile.values().stream()
                .flatMap(List::stream)
                .filter(record -> fqn.equals(record.fqn()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no record for " + fqn + " in " + byFile));
    }
}
