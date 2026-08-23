package com.finme.backend.dto;

import java.util.List;

/**
 * Dashboard recommendations (FR-1.7.4).
 *
 * @param recommendations   short, plain-language suggestions; empty when none could be produced
 * @param unavailableReason why the list is empty, in words the user can act on - null when
 *                          recommendations were produced normally. Carried explicitly rather
 *                          than left as a bare empty list so the dashboard can distinguish "you
 *                          have no data yet" from "the AI providers are down", which call for
 *                          very different things from the reader.
 */
public record RecommendationsResponse(List<String> recommendations, String unavailableReason) {
}
