package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogNavigationTest {
    @Test public void twentySevenAppsAndShortLastRow() {
        assertEquals(26, CatalogNavigation.move(23, 27, 6, 0, 1));
        assertEquals(20, CatalogNavigation.move(26, 27, 6, 0, -1));
        assertEquals(5, CatalogNavigation.move(5, 27, 6, 1, 0));
        assertEquals(6, CatalogNavigation.move(6, 27, 6, -1, 0));
        assertEquals(26, CatalogNavigation.move(26, 27, 6, 1, 0));
        assertEquals(-1, CatalogNavigation.move(0, 0, 6, 1, 0));
    }
    @Test public void everyAppIsReachableWithoutEscapingTheGrid() {
        for (int count = 1; count <= 120; count++) {
            java.util.Set<Integer> reached = new java.util.HashSet<>(); reached.add(0);
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(); queue.add(0);
            while (!queue.isEmpty()) {
                int p = queue.remove();
                for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int q = CatalogNavigation.move(p, count, 6, d[0], d[1]);
                    assertTrue(q >= 0 && q < count);
                    if (reached.add(q)) queue.add(q);
                }
            }
            assertEquals(count, reached.size());
        }
    }
}
