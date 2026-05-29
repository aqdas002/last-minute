package com.lastminute.categories;

import com.lastminute.listings.ListingDto;
import com.lastminute.listings.ListingQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryRepository cats;
  private final ListingQueryService q;

  public CategoryController(CategoryRepository cats, ListingQueryService q) {
    this.cats = cats;
    this.q = q;
  }

  @GetMapping
  public List<CategoryDto> all() {
    return cats.findAll().stream().filter(Category::isActive).map(CategoryDto::from).toList();
  }

  @GetMapping("/{slug}/listings")
  public List<ListingDto> bySlug(@PathVariable String slug) {
    return q.byCategorySlug(slug).stream().map(ListingDto::from).toList();
  }
}
