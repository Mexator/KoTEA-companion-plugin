package com.kotea.companion.index;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.VfsTestUtil;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.io.IOException;
import java.util.Set;

public class KoTEAIndexRebuildTest extends KoTEAFixtureTestCase {

    private static final KoTEARootsIndex BASELINE = new KoTEARootsIndex(
            Set.of("feat1.Feat1Event", "feat2.Feat2Event"),
            Set.of("feat1.Feat1Command", "feat2.Feat2Command"),
            Set.of("feat1.Feat1News", "feat2.Feat2News"));

    public void testBodyOnlyEdit_leavesSnapshotUntouched() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());

        patchFixture("feat1/Feat1Update.kt", "/* {body} */", "event.hashCode()");

        assertEquals(BASELINE, getKoTEAIndex());
    }

    public void testRetargetEventTypeArgument_updatesSnapshotAndRepaintsGutters() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());
        assertEquals(Set.of("Feat1Clicked", "Feat1Load", "Feat1News"), koTEAGutterOwners("feat1/Contract.kt"));

        patchFixture("feat1/Feat1Update.kt", "Feat1Event", "Feat1EventAlt");
        awaitIndexIdle();

        assertEquals(
                new KoTEARootsIndex(
                        Set.of("feat1.Feat1EventAlt", "feat2.Feat2Event"),
                        Set.of("feat1.Feat1Command", "feat2.Feat2Command"),
                        Set.of("feat1.Feat1News", "feat2.Feat2News")
                ),
                getKoTEAIndex()
        );
        assertEquals("gutters did not follow the new Event Root",
                Set.of("Feat1AltClicked", "Feat1Load", "Feat1News"), koTEAGutterOwners("feat1/Contract.kt"));
    }

    public void testAddFeatureUpdateFile_addsItsRootsAndPaintsItsGutters() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());

        myFixture.addFileToProject("feat3/Feat3.kt", """
                package feat3
                
                import ru.tinkoff.kotea.core.dsl.DslUpdate
                
                data class Feat3State(val x: Int = 0)
                data class Feat3News(val t: String)
                
                sealed interface Feat3Event
                data class Feat3Tapped(val id: Int) : Feat3Event
                
                sealed interface Feat3Command
                data class Feat3Send(val page: Int) : Feat3Command
                
                class Feat3Update : DslUpdate<Feat3State, Feat3Event, Feat3Command, Feat3News>() {
                    override fun NextBuilder.update(event: Feat3Event) {}
                }
                """);
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(
                        Set.of("feat1.Feat1Event", "feat2.Feat2Event", "feat3.Feat3Event"),
                        Set.of("feat1.Feat1Command", "feat2.Feat2Command", "feat3.Feat3Command"),
                        Set.of("feat1.Feat1News", "feat2.Feat2News", "feat3.Feat3News")),
                getKoTEAIndex());
        assertEquals("the new feature's Concrete Event / Command / News did not get gutters",
                Set.of("Feat3Tapped", "Feat3Send", "Feat3News"), koTEAGutterOwners("feat3/Feat3.kt"));
    }

    public void testDeleteFeatureUpdateFile_removesItsRootsFromTheSnapshot() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());

        deleteTempFile("feat2/Feat2Update.kt");
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(Set.of("feat1.Feat1Event"), Set.of("feat1.Feat1Command"),
                Set.of("feat1.Feat1News")), getKoTEAIndex());
    }

    public void testDeleteUnrelatedContentFile_leavesSnapshotUntouched() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());

        deleteTempFile("feat1/Standalone.kt");

        assertEquals(BASELINE, getKoTEAIndex());
    }

    public void testTwoRapidChanges_finalSnapshotReflectsTheLaterChange() {
        openFixtureProject("indexRebuild");
        assertEquals(BASELINE, getKoTEAIndex());

        // no await between the two edits: the second bumps the change counter while the first
        // rebuild is still coalesced/queued, so the service must land on the second.
        patchFixture("feat1/Feat1Update.kt", "Feat1Event", "Feat1EventAlt");
        patchFixture("feat1/Feat1Update.kt", "Feat1EventAlt", "Feat1EventAlt2");
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(
                        Set.of("feat1.Feat1EventAlt2", "feat2.Feat2Event"),
                        Set.of("feat1.Feat1Command", "feat2.Feat2Command"),
                        Set.of("feat1.Feat1News", "feat2.Feat2News")),
                getKoTEAIndex());
    }

    public void testTypealiasedRoot_retargetedThroughTheAlias_movesTheSnapshot() {
        openFixtureProject("typealiasImport");
        KoTEARootsIndex aliased = new KoTEARootsIndex(
                Set.of("alias.RealEvent"), Set.of("alias.contract.RealCommand"), Set.of("alias.AliasNews"));
        assertEquals(aliased, getKoTEAIndex());

        patchFixture("alias/AliasUpdate.kt", "= RealEvent", "= RealEvent2");
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(Set.of("alias.RealEvent2"), Set.of("alias.contract.RealCommand"),
                        Set.of("alias.AliasNews")),
                getKoTEAIndex());
    }

    public void testAddConcreteEvent_getsItsGutter() {
        openFixtureProject("indexRebuild");

        patchFixture("feat1/Contract.kt", "data class Feat1Clicked(val id: Int) : Feat1Event",
                "data class Feat1Clicked(val id: Int) : Feat1Event\ndata class Feat1Swiped(val id: Int) : Feat1Event");
        awaitIndexIdle();

        assertEquals(BASELINE, getKoTEAIndex());
        assertTrue(koTEAGutterOwners("feat1/Contract.kt").contains("Feat1Swiped"));
    }

    public void testRenameRootByRefactoring_keepsGuttersOnTheRenamedHierarchy() {
        openFixtureProject("indexRebuild");

        myFixture.renameElement(ktClass("feat1/Contract.kt", "Feat1Event"), "Feat1Input");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        awaitIndexIdle();

        assertEquals(withEventRoots("feat1.Feat1Input", "feat2.Feat2Event"), getKoTEAIndex());
        assertEquals(Set.of("Feat1Clicked", "Feat1Load", "Feat1News"), koTEAGutterOwners("feat1/Contract.kt"));
    }

    public void testDeleteFeatureFolder_removesItsRoots() {
        openFixtureProject("indexRebuild");

        deleteTempFile("feat2");
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(Set.of("feat1.Feat1Event"), Set.of("feat1.Feat1Command"),
                Set.of("feat1.Feat1News")), getKoTEAIndex());
    }

    public void testMoveInFolderWithNewFeature_addsItsRoots() throws IOException {
        openFixtureProject("indexRebuild");
        VirtualFile sourceRoot = myFixture.findFileInTempDir("feat1").getParent();
        VirtualFile staging = WriteAction.computeAndWait(() -> sourceRoot.getParent().createChildDirectory(this, "staging"));
        try {
            VirtualFile folder = WriteAction.computeAndWait(() -> staging.createChildDirectory(this, "feat3"));
            writeFile(folder, "Feat3Update.kt", """
                    package feat3

                    import feat2.*
                    import ru.tinkoff.kotea.core.dsl.DslUpdate

                    sealed interface Feat3Event
                    data class Feat3Tapped(val id: Int) : Feat3Event

                    class Feat3Update : DslUpdate<Feat2State, Feat3Event, Feat2Command, Feat2News>()
                    """);

            WriteAction.runAndWait(() -> folder.move(this, sourceRoot));
            awaitIndexIdle();

            assertEquals(withEventRoots("feat1.Feat1Event", "feat2.Feat2Event", "feat3.Feat3Event"),
                    getKoTEAIndex());
        } finally {
            WriteAction.runAndWait(() -> staging.delete(this));
        }
    }

    public void testMoveFeatureUpdateIntoTestSources_removesItsRoots() throws IOException {
        openFixtureProject("indexRebuild");
        Module module = getModule();
        VirtualFile sourceRoot = myFixture.findFileInTempDir("feat1").getParent();
        VirtualFile testRoot = WriteAction.computeAndWait(() -> sourceRoot.getParent().createChildDirectory(this, "tests"));
        ModuleRootModificationUtil.updateModel(module, model -> model.addContentEntry(testRoot).addSourceFolder(testRoot, true));
        try {
            VirtualFile update = myFixture.findFileInTempDir("feat2/Feat2Update.kt");
            WriteAction.runAndWait(() -> update.move(this, testRoot));
            awaitIndexIdle();

            assertEquals(new KoTEARootsIndex(Set.of("feat1.Feat1Event"), Set.of("feat1.Feat1Command"),
                    Set.of("feat1.Feat1News")), getKoTEAIndex());
        } finally {
            ModuleRootModificationUtil.updateModel(module, model -> {
                for (ContentEntry entry : model.getContentEntries()) {
                    if (testRoot.equals(entry.getFile())) model.removeContentEntry(entry);
                }
            });
            WriteAction.runAndWait(() -> testRoot.delete(this));
        }
    }

    public void testSharedBaseTypeArgumentChange_updatesItsDescendantsRoots() {
        openFixtureProject("intermediateUpdate");

        patchFixture("intermediate/BaseFeatureUpdate.kt", "Nothing>()", "ProfileNews>()");
        awaitIndexIdle();

        assertEquals(new KoTEARootsIndex(Set.of("intermediate.ProfileEvent"), Set.of("intermediate.ProfileCommand"),
                Set.of("intermediate.ProfileNews")), getKoTEAIndex());
    }

    public void testSharedBaseLeavesAndRejoinsKoTEA_dropsAndRestoresItsDescendantsRoots() {
        KoTEARootsIndex baseline = openFixtureProject("intermediateUpdate");

        patchFixture("intermediate/BaseFeatureUpdate.kt", " : DslUpdate<S, E, C, Nothing>()", "");
        awaitIndexIdle();
        KoTEARootsIndex withoutBase = getKoTEAIndex();
        patchFixture("intermediate/BaseFeatureUpdate.kt", "C : Any>", "C : Any> : DslUpdate<S, E, C, Nothing>()");
        awaitIndexIdle();

        assertEquals(KoTEARootsIndex.EMPTY, withoutBase);
        assertEquals(baseline, getKoTEAIndex());
    }

    public void testDisposeDuringRebuild_doesNotThrowFromTeardown() {
        openFixtureProject("indexRebuild");

        // trigger a rebuild but never await it, so tearDown() disposes KoTEAIndexService mid-rebuild;
        // the test passes if teardown unwinds without throwing.
        patchFixture("feat1/Feat1Update.kt", "Feat1Event", "Feat1EventAlt");
    }

    // --- helpers ---

    private static KoTEARootsIndex withEventRoots(String... eventRoots) {
        return new KoTEARootsIndex(Set.of(eventRoots), Set.of("feat1.Feat1Command", "feat2.Feat2Command"),
                Set.of("feat1.Feat1News", "feat2.Feat2News"));
    }

    private KoTEARootsIndex getKoTEAIndex() {
        return KoTEAIndexService.getInstance(getProject()).getIndex();
    }

    /**
     * Replaces <b>target</b> with <b>replacement</b> in <b>relativePath</b> and reindexes the file.
     */
    private void patchFixture(String relativePath, String target, String replacement) {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        String text = LoadTextUtil.loadText(file).toString();
        assertTrue(relativePath + " has no <" + target + "> to replace", text.contains(target));
        myFixture.saveText(file, text.replace(target, replacement));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private void writeFile(VirtualFile directory, String name, String text) throws IOException {
        VirtualFile file = WriteAction.computeAndWait(() -> directory.createChildData(this, name));
        WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    }

    private void deleteTempFile(String relativePath) {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        VfsTestUtil.deleteFile(file);
    }
}
