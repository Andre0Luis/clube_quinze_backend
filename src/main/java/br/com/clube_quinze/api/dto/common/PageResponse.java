package br.com.clube_quinze.api.dto.common;

import java.io.Serializable;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size) implements Serializable {
}
