package com.kotea.companion.fixtures;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.index.KoTEARootsIndex;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.io.File;

/** Base class for the feature-layer tests: shared descriptor + {@code src/test/testData} root. */
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
     * Copies a {@code testData} fixture directory into the project and waits until a KoTEA index is ready
     */
    protected void openFixtureProject(String fixtureDir) {
        myFixture.copyDirectoryToProject(fixtureDir, "");
        KoTEAIndexService.getInstance(getProject()).registerListeners();
        awaitIndex();
    }

    /** First {@link KtClassOrObject} anywhere under {@code file} whose simple name is {@code name}. */
    protected static KtClassOrObject findKtClass(PsiFile file, String name) {
        return PsiTreeUtil.findChildrenOfType(file, KtClassOrObject.class).stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no class " + name + " in " + file.getName()));
    }

    protected KoTEARootsIndex awaitIndex() {
        KoTEAIndexService service = KoTEAIndexService.getInstance(getProject());
        long deadlineMs = System.currentTimeMillis() + 60_000;
        KoTEARootsIndex previous = null;
        int stableStreak = 0;
        while (System.currentTimeMillis() < deadlineMs) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            KoTEARootsIndex current = service.getIndex();
            boolean settled = current.equals(previous) && !current.equals(KoTEARootsIndex.EMPTY);
            if (settled) {
                if (++stableStreak >= 10) return current;
            } else {
                stableStreak = 0;
            }
            previous = current;
            try {
                //noinspection BusyWait
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("KoTEA index did not settle within 60s; last snapshot: " + service.getIndex());
    }
}
