package com.lastminute.categories;

import java.util.UUID;

public record CategoryDto(UUID id, String slug, String name, String iconName) {
  public static CategoryDto from(Category c) {
    return new CategoryDto(c.getId(), c.getSlug(), c.getName(), c.getIconName());
  }
}
