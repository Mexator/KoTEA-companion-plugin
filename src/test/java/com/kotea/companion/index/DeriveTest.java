package com.kotea.companion.index;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DeriveTest {

    private static UpdateRecord featureUpdate(String fqn, String eventRootFqn, String commandRootFqn, String newsRootFqn) {
        return new UpdateRecord(fqn, Set.of(), eventRootFqn, commandRootFqn, newsRootFqn);
    }

    @Test
    public void singleFeatureUpdate_yieldsExactlyItsRoots() {
        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(
                List.of(featureUpdate("f.FeatureUpdate", "f.Event", "f.Command", "f.News"))));

        assertEquals(new KoTEARootsIndex(Set.of("f.Event"), Set.of("f.Command"), Set.of("f.News")), index);
    }

    @Test
    public void twoFeatureUpdates_yieldTheUnionOfRoots() {
        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(
                List.of(featureUpdate("a.FeatureUpdate", "a.Event", "a.Command", "a.News")),
                List.of(featureUpdate("b.FeatureUpdate", "b.Event", "b.Command", "b.News"))));

        assertEquals(
                new KoTEARootsIndex(
                        Set.of("a.Event", "b.Event"),
                        Set.of("a.Command", "b.Command"),
                        Set.of("a.News", "b.News")),
                index);
    }

    @Test
    public void projectBaseClassBeneathUpdate_isExcluded_onlyMostDerivedContributes() {
        UpdateRecord projectBase = new UpdateRecord(
                "p.BaseUpdate", Set.of(), "p.BogusEvent", "p.BogusCommand", "p.BogusNews");
        UpdateRecord featureUpdate = new UpdateRecord(
                "p.FeatureUpdate", Set.of("p.BaseUpdate"), "p.RealEvent", "p.RealCommand", "p.RealNews");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(projectBase, featureUpdate)));

        assertEquals(
                new KoTEARootsIndex(Set.of("p.RealEvent"), Set.of("p.RealCommand"), Set.of("p.RealNews")),
                index);
    }

    @Test
    public void anonymousUpdate_isTreatedAsAFeatureUpdate() {
        UpdateRecord anonymous = new UpdateRecord(null, Set.of(), "x.Event", "x.Command", "x.News");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(anonymous)));

        assertEquals(new KoTEARootsIndex(Set.of("x.Event"), Set.of("x.Command"), Set.of("x.News")), index);
    }

    @Test
    public void nullRoot_isSkipped_withoutNpe() {
        UpdateRecord allNull = new UpdateRecord("n.FeatureUpdate", Set.of(), null, null, null);
        UpdateRecord eventAndNewsOnly = new UpdateRecord("n.OtherUpdate", Set.of(), "n.Event", null, "n.News");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(allNull, eventAndNewsOnly)));

        assertEquals(new KoTEARootsIndex(Set.of("n.Event"), Set.of(), Set.of("n.News")), index);
    }
}
