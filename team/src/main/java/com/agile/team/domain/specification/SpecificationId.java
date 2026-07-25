package com.agile.team.domain.specification;

import java.util.UUID;

public record SpecificationId(UUID value) {
    public SpecificationId {
        if (value == null) throw new IllegalArgumentException("SpecificationId value must not be null");
    }

    public static SpecificationId generate() {
        return new SpecificationId(UUID.randomUUID());
    }

    public static SpecificationId of(UUID value) {
        return new SpecificationId(value);
    }
}
