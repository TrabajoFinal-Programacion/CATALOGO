package ar.edu.um.catalogo.catalog.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Pagina de resultados. catalogVersion indica la version de la copia local consultada. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              long catalogVersion) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> content, long catalogVersion) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), catalogVersion);
    }
}
