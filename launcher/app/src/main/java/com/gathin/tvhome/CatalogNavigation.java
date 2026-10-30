package com.gathin.tvhome;

/** Predictable remote navigation: horizontal edges stop; vertical moves clamp the short final row. */
final class CatalogNavigation {
    static int move(int position, int count, int columns, int dx, int dy) {
        if (count <= 0 || columns <= 0) return -1;
        position = Math.max(0, Math.min(position, count - 1));
        int row = position / columns, column = position % columns;
        if (dy != 0) {
            int targetRow = row + Integer.signum(dy);
            if (targetRow < 0 || targetRow > (count - 1) / columns) return position;
            return Math.min(count - 1, targetRow * columns + column);
        }
        int targetColumn = column + Integer.signum(dx), target = position + Integer.signum(dx);
        return targetColumn < 0 || targetColumn >= columns || target >= count ? position : target;
    }
}
