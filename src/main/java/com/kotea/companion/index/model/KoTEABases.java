package com.kotea.companion.index.model;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/** KoTEA's own {@code Update} and {@code DslUpdate}, as records every Update hierarchy starts from. */
public final class KoTEABases {

    // https://opensource.tbank.ru/mobile-tech/KoTEA/-/blob/main/core/src/commonMain/kotlin/ru/tinkoff/kotea/core/Update.kt
    public static final String UPDATE_FQN = "ru.tinkoff.kotea.core.Update";
    public static final String DSL_UPDATE_FQN = "ru.tinkoff.kotea.core.dsl.DslUpdate";

    /** Both take {@code <State, Event, Command, News>}. */
    private static final Map<String, UpdateRecord> RECORDS = Map.of(
            UPDATE_FQN, record(UPDATE_FQN),
            DSL_UPDATE_FQN, record(DSL_UPDATE_FQN));

    private KoTEABases() {
    }

    @Nullable
    public static UpdateRecord recordOf(String fqn) {
        return RECORDS.get(fqn);
    }

    private static UpdateRecord record(String fqn) {
        return new UpdateRecord(fqn, Set.of(), new RoleBinding.FromTypeParameter(1),
                new RoleBinding.FromTypeParameter(2), new RoleBinding.FromTypeParameter(3));
    }
}
