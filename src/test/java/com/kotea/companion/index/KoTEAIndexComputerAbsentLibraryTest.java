package com.kotea.companion.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.util.List;
import java.util.Map;

/**
 * Tests a case when no KoTEA is present on the classpath
 */
public class KoTEAIndexComputerAbsentLibraryTest extends KoTEAFixtureTestCase {

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KoTEALibraryFreeDescriptor.INSTANCE;
    }

    public void testUpdateOffClasspath_computeAllIsEmpty_andDeriveIsEmptyIndex() {
        myFixture.copyDirectoryToProject("libraryFree", "");

        Map<VirtualFile, List<UpdateRecord>> byFile =
                ReadAction.compute(() -> KoTEAIndexComputer.computeAll(getProject()));

        assertEmpty(byFile.entrySet());
        assertEquals(KoTEARootsIndex.EMPTY, KoTEAIndexComputer.derive(byFile.values()));
    }

    private static final class KoTEALibraryFreeDescriptor extends DefaultLightProjectDescriptor {

        public static final KoTEALibraryFreeDescriptor INSTANCE = new KoTEALibraryFreeDescriptor();

        private KoTEALibraryFreeDescriptor() {
        }
    }
}
