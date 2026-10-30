package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class FavoriteShelfTest {
    @Test public void oneRowUsesExactlyTheChosenAppCount() {
        for(int n=1;n<=6;n++){assertEquals(n,FavoriteShelf.columns(n));assertEquals(1,FavoriteShelf.rows(n));}
    }
    @Test public void twoRowsAreBalancedWithTheShorterRowOnTheBottom() {
        for(int n=7;n<=12;n++) {
            int first=FavoriteShelf.columns(n),second=n-first;
            assertEquals(2,FavoriteShelf.rows(n));assertTrue(first<=6 && first-second<=1 && second<=first);
        }
        assertEquals(4,FavoriteShelf.columns(8));assertEquals(5,FavoriteShelf.columns(9));
    }
    @Test public void emptyShelfHasAValidLayoutWithoutAnAppPlaceholder() {assertEquals(1,FavoriteShelf.columns(0));}
}
