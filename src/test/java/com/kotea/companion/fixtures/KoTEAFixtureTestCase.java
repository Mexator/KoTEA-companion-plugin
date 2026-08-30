package com.kotea.companion.fixtures;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.index.KoTEARootsIndex;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.io.File;

/**
 * Base class for the feature-layer tests: shared descriptor + {@code src/test/testData} root.
 */
public abstract class KoTEAFixtureTestCase extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return new File("src/test/testData").getAbsolutePath();
    }

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KoTEAProjectDescriptor.INSTANCE;
    }

    /**
     * Copies a {@code testData} fixture directory into the project, registers the index listeners and
     * blocks until {@link KoTEAIndexService}'s snapshot indexes copied files. The project is reused between tests,
     * so force reindex is needed.
     *
     * @param fixtureDir the name of directory inside {@code src/test/testData}
     */
    protected KoTEARootsIndex openFixtureProject(String fixtureDir) {
        myFixture.copyDirectoryToProject(fixtureDir, "");
        KoTEAIndexService.getInstance(getProject()).registerListeners();
        return forceFullIndexRebuild();
    }

    /**
     * Runs a full rescan, waits for it to finish, and returns the resulting snapshot
     */
    protected KoTEARootsIndex forceFullIndexRebuild() {
        WriteAction.runAndWait(() -> ProjectRootManagerEx.getInstanceEx(getProject())
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN));
        awaitIndexIdle();
        return KoTEAIndexService.getInstance(getProject()).getIndex();
    }

    /**
     * Pumps the EDT queue until {@link KoTEAIndexService#isIdle()}
     */
    protected void awaitIndexIdle() {
        KoTEAIndexService service = KoTEAIndexService.getInstance(getProject());
        long deadlineMs = System.currentTimeMillis() + 60_000;
        while (!service.isIdle()) {
            if (System.currentTimeMillis() > deadlineMs) {
                throw new AssertionError("KoTEA index did not go idle within 60s; last snapshot: " + service.getIndex());
            }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            sleepBriefly();
        }
    }

    /**
     * First {@link KtClassOrObject} anywhere under {@code file} whose simple name is {@code name}.
     */
    protected static KtClassOrObject findKtClass(PsiFile file, String name) {
        return PsiTreeUtil.findChildrenOfType(file, KtClassOrObject.class).stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no class " + name + " in " + file.getName()));
    }

    /**
     * Opens {@code relativePath} in a virtual editor (to get its PSI) and returns the {@link KtClassOrObject}
     * named {@code name} inside it.
     */
    protected KtClassOrObject ktClass(String relativePath, String name) {
        return findKtClass(myFixture.configureFromTempProjectFile(relativePath), name);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
