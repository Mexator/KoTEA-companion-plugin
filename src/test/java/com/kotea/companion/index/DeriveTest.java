package com.kotea.companion.index;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DeriveTest {

    private static UpdateRecord featureUpdate(String fqn, String eventRootFqn, String commandRootFqn) {
        return new UpdateRecord(fqn, Set.of(), eventRootFqn, commandRootFqn);
    }

    @Test
    public void singleFeatureUpdate_yieldsExactlyItsRoots() {
        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(
                List.of(featureUpdate("f.FeatureUpdate", "f.Event", "f.Command"))));

        assertEquals(new KoTEARootsIndex(Set.of("f.Event"), Set.of("f.Command")), index);
    }

    @Test
    public void twoFeatureUpdates_yieldTheUnionOfRoots() {
        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(
                List.of(featureUpdate("a.FeatureUpdate", "a.Event", "a.Command")),
                List.of(featureUpdate("b.FeatureUpdate", "b.Event", "b.Command"))));

        assertEquals(
                new KoTEARootsIndex(Set.of("a.Event", "b.Event"), Set.of("a.Command", "b.Command")),
                index);
    }

    @Test
    public void projectBaseClassBeneathUpdate_isExcluded_onlyMostDerivedContributes() {
        UpdateRecord projectBase = new UpdateRecord(
                "p.BaseUpdate", Set.of(), "p.BogusEvent", "p.BogusCommand");
        UpdateRecord featureUpdate = new UpdateRecord(
                "p.FeatureUpdate", Set.of("p.BaseUpdate"), "p.RealEvent", "p.RealCommand");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(projectBase, featureUpdate)));

        assertEquals(new KoTEARootsIndex(Set.of("p.RealEvent"), Set.of("p.RealCommand")), index);
    }

    @Test
    public void anonymousUpdate_isTreatedAsAFeatureUpdate() {
        UpdateRecord anonymous = new UpdateRecord(null, Set.of(), "x.Event", "x.Command");

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(anonymous)));

        assertEquals(new KoTEARootsIndex(Set.of("x.Event"), Set.of("x.Command")), index);
    }

    @Test
    public void nullEventOrCommandRoot_isSkipped_withoutNpe() {
        UpdateRecord bothNull = new UpdateRecord("n.FeatureUpdate", Set.of(), null, null);
        UpdateRecord eventOnly = new UpdateRecord("n.OtherUpdate", Set.of(), "n.Event", null);

        KoTEARootsIndex index = KoTEAIndexComputer.derive(List.of(List.of(bothNull, eventOnly)));

        assertEquals(new KoTEARootsIndex(Set.of("n.Event"), Set.of()), index);
    }
}
