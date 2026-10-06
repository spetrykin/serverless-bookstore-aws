package com.serhii.bookstore.common.web;

import java.util.List;

/** {@code nextCursor} is {@code null} once there are no more pages. */
public record Page<T>(List<T> items, String nextCursor) {
}