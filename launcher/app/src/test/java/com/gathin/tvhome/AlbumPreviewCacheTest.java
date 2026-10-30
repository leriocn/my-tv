package com.gathin.tvhome;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlbumPreviewCacheTest {
    @Test public void sourceChangesAndScreenChangesInvalidatePrivatePreviewOnly() throws Exception {
        File root = Files.createTempDirectory("album-cache-test").toFile();
        File source = new File(root,"original.jpg"), cache = new File(root,"cache");
        Files.write(source.toPath(),new byte[]{1,2,3});
        File first = AlbumPreviewCache.file(cache,source,1920,1080);
        assertEquals(first,AlbumPreviewCache.file(cache,source,1920,1080));
        assertEquals(cache.getCanonicalPath(),first.getParentFile().getCanonicalPath());
        assertTrue(first.getName().matches("[a-f0-9]{64}\\.jpg"));
        assertNotEquals(first,AlbumPreviewCache.file(cache,source,1280,720));
        Files.write(source.toPath(),new byte[]{4,5,6,7});
        assertNotEquals(first,AlbumPreviewCache.file(cache,source,1920,1080));
        assertArrayEquals(new byte[]{4,5,6,7},Files.readAllBytes(source.toPath()));
    }
}
