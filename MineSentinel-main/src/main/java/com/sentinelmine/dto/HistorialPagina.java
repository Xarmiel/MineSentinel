package com.sentinelmine.dto;

import java.util.List;

/**
 * Página de resultados del historial de alertas. Incluye los datos necesarios
 * para que la vista pueda navegar sin conocer detalles de la consulta SQL.
 */
public class HistorialPagina {

    private final List<AlertaViewDTO> content;
    private final long totalElements;
    private final int page;
    private final int size;

    public HistorialPagina(List<AlertaViewDTO> content, long totalElements, int page, int size) {
        this.content = content != null ? content : List.of();
        this.totalElements = Math.max(totalElements, 0);
        this.page = Math.max(page, 0);
        this.size = size > 0 ? size : HistorialFiltro.PAGE_SIZE_DEFAULT;
    }

    public List<AlertaViewDTO> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean isHasPrevious() {
        return page > 0;
    }

    public boolean isHasNext() {
        return page + 1 < getTotalPages();
    }

    public int getPreviousPage() {
        return Math.max(page - 1, 0);
    }

    public int getNextPage() {
        return Math.min(page + 1, Math.max(getTotalPages() - 1, 0));
    }

    public long getStartItem() {
        return totalElements == 0 ? 0 : (long) page * size + 1;
    }

    public long getEndItem() {
        return Math.min((long) (page + 1) * size, totalElements);
    }

    public boolean isVacia() {
        return content.isEmpty();
    }
}
