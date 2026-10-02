package com.medbridge.dto;

import com.medbridge.entity.SourceFormat;

public record SourceSystemResponse(Long id, String code, String name, SourceFormat format, boolean active) {
}
