package ar.edu.um.catalogo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Reglas de decision de la sincronizacion (contrato 14.2 y 18.2). */
class SyncPlannerTest {

    @Test
    void emptyDatabaseRequiresSnapshot() {
        SyncPlan plan = SyncPlanner.plan(null, new RemoteVersions(7, 4));
        assertThat(plan.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(plan.reason()).isEqualTo("BASE_VACIA");
    }

    @Test
    void sameVersionNeedsNothing() {
        assertThat(SyncPlanner.plan(7L, new RemoteVersions(7, 4)).type()).isEqualTo(SyncPlan.Type.NONE);
    }

    @Test
    void versionsWithinWindowAreAppliedIncrementallyInOrder() {
        SyncPlan plan = SyncPlanner.plan(5L, new RemoteVersions(7, 4));
        assertThat(plan.type()).isEqualTo(SyncPlan.Type.INCREMENTAL);
        assertThat(plan.fromVersion()).isEqualTo(6);
        assertThat(plan.toVersion()).isEqualTo(7);
    }

    @Test
    void localEqualToOldestAvailableIsStillIncremental() {
        SyncPlan plan = SyncPlanner.plan(4L, new RemoteVersions(7, 4));
        assertThat(plan.type()).isEqualTo(SyncPlan.Type.INCREMENTAL);
        assertThat(plan.fromVersion()).isEqualTo(5);
    }

    /** Ejemplo literal del contrato 18.2: local 3, current 7, oldest 4 -> no se empieza en changes:4. */
    @Test
    void localBelowOldestAvailableRequiresSnapshot() {
        SyncPlan plan = SyncPlanner.plan(3L, new RemoteVersions(7, 4));
        assertThat(plan.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(plan.reason()).isEqualTo("FUERA_DE_VENTANA");
    }

    @Test
    void localAheadOfPublishedCannotBeVerifiedAndRequiresSnapshot() {
        SyncPlan plan = SyncPlanner.plan(9L, new RemoteVersions(7, 4));
        assertThat(plan.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(plan.reason()).isEqualTo("VERSION_LOCAL_ADELANTADA");
    }
}
