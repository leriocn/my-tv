package com.gathin.tvhome;

/** Balanced rows; a single row is centered, additional rows retain the first row's left edge. */
final class FavoriteShelf {
    static int columns(int count) { return count <= 6 ? Math.max(1, count) : Math.min(6, (count + 1) / 2); }
    static int rows(int count) { return count > 6 ? 2 : 1; }
}
