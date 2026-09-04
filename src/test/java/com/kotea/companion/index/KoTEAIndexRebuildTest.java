package com.kotea.companion.index;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.VfsTestUtil;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

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

    public void testDisposeDuringRebuild_doesNotThrowFromTeardown() {
        openFixtureProject("indexRebuild");

        // trigger a rebuild but never await it, so tearDown() disposes KoTEAIndexService mid-rebuild;
        // the test passes if teardown unwinds without throwing.
        patchFixture("feat1/Feat1Update.kt", "Feat1Event", "Feat1EventAlt");
    }

    // --- helpers ---

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

    private void deleteTempFile(String relativePath) {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        VfsTestUtil.deleteFile(file);
    }
}
