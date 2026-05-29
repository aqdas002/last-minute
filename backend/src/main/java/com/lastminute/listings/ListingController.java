package com.lastminute.listings;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

  private final ListingQueryService q;

  public ListingController(ListingQueryService q) {
    this.q = q;
  }

  @GetMapping
  public List<ListingDto> startingSoon(@RequestParam(required = false) String city) {
    return q.startingSoon(city).stream().map(ListingDto::from).toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<ListingDto> byId(@PathVariable UUID id) {
    return q.byId(id).map(ListingDto::from).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/search")
  public List<ListingDto> search(
      @RequestParam String q,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String category) {
    String trimmed = q == null ? "" : q.trim();
    if (trimmed.length() < 2) return List.of();
    return this.q.search(trimmed, city, category).stream().map(ListingDto::from).toList();
  }
}
