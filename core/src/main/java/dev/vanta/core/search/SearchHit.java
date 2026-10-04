package dev.vanta.core.search;

import java.util.List;
import java.util.Set;

/**
 * One ranked search result.
 *
 * @param item          the indexed item
 * @param score         relevance (higher is better)
 * @param highlights    character ranges of the title that matched, merged and sorted
 * @param matchedFields fields that contributed to the score
 */
public record SearchHit<T>(T item, double score, List<MatchRange> highlights, Set<SearchField> matchedFields) {
}
