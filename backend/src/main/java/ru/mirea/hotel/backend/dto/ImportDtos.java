package ru.mirea.hotel.backend.dto;

import java.util.List;

/** DTO проверки и применения CSV-импорта. */
public final class ImportDtos {
    private ImportDtos() {
    }

    /** CREATE_ONLY - только новые брони; UPSERT_BY_CODE - новые создаются, существующие (по code) обновляются. */
    public enum ImportMode { CREATE_ONLY, UPSERT_BY_CODE }

    public record RowIssue(long row, String field, String message) {
    }

    public record ImportPreview(int totalRows, int validRows, int invalidRows, int toCreate, int toUpdate,
                                List<RowIssue> issues) {
    }

    public record ImportResult(int totalRows, int created, int updated, int skipped, List<RowIssue> issues) {
    }
}
