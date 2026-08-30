package com.kotea.companion.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.index.KoTEARootsIndex;

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
