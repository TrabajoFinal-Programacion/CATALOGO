package ar.edu.um.catalogo.sync;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SyncStateRepository extends JpaRepository<SyncState, Short> {

    /**
     * Lee la fila de estado con SELECT ... FOR UPDATE: dentro de la transaccion que aplica datos,
     * nadie mas puede cambiar la version local hasta el commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SyncState s where s.id = 1")
    Optional<SyncState> findForUpdate();
}
