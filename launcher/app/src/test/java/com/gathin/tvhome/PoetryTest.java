package com.gathin.tvhome;

import org.junit.Test;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import static org.junit.Assert.*;

public class PoetryTest {
    private String data() throws Exception { return new String(Files.readAllBytes(new File("src/main/assets/poetry/poems.json").toPath()),java.nio.charset.StandardCharsets.UTF_8); }
    @Test public void allTenBooksIncludeGardensAndNewEditionMoves() throws Exception {
        PoemCatalog catalog=PoemCatalog.parse(data(),null,"");assertEquals(87,catalog.poems.size());
        int[] expected={6,7,7,7,10,9,10,10,10,11},actual=new int[10];
        int previous=0;
        for(PoemCatalog.Poem poem:catalog.poems){int book=(poem.grade-1)*2+poem.term-1;assertTrue(book>=previous);previous=book;actual[book]++;assertFalse(poem.meaning.isBlank());}
        assertArrayEquals(expected,actual);
        assertEquals("鹿柴",catalog.poems.get(33).title);
        assertEquals("早春呈水部张十八员外",catalog.poems.get(73).title);
        assertEquals("游子吟",catalog.poems.get(70).title);assertEquals("游子吟",catalog.poems.get(79).title);
        assertTrue(catalog.poems.get(23).body.startsWith("两个黄鹂"));assertTrue(catalog.poems.get(37).body.startsWith("迟日"));
        assertTrue(catalog.poems.get(22).body.endsWith("春风吹又生。"));
        assertTrue(catalog.poems.get(65).body.contains("只留清气"));
    }
    @Test public void rejectsDuplicateIdsAndUnsafeAudioPaths() throws Exception {
        JSONObject root=new JSONObject(data());root.getJSONArray("poems").getJSONObject(1).put("id","p001");
        reject(root.toString());
        for(String path:new String[]{"../bad.mp3","audio/../../bad.mp3","https://example.com/a.mp3","/sdcard/a.mp3","audio/sub/a.mp3"}){
            root=new JSONObject(data());root.getJSONArray("poems").getJSONObject(0).put("audio",path);reject(root.toString());
        }
    }
    @Test public void missingAudioIsRecoverableAndProgressUsesStableId() throws Exception {
        PoemCatalog catalog=PoemCatalog.parse(data(),new File("missing-poetry-library"),"");
        assertNull(catalog.audio(catalog.poems.get(0)));assertEquals(73,catalog.index("p074"));assertEquals(0,catalog.index("removed"));
        assertEquals(86,PoemCatalog.step(0,-1,87));assertEquals(0,PoemCatalog.step(86,1,87));
    }
    @Test public void oldCompletionCannotAdvanceAfterNextPauseOrExit() {
        PoetrySequence sequence=new PoetrySequence();long first=sequence.begin();assertTrue(sequence.accepts(first));
        long second=sequence.begin();assertFalse(sequence.accepts(first));assertTrue(sequence.accepts(second));
        sequence.cancel();assertFalse(sequence.accepts(second));
        assertEquals(PoetrySequence.Phase.RECITATION,sequence.phase());
    }
    @Test public void recitationWaitsThreeSecondsThenMeaningWaitsFiveSecondsBeforeNext() {
        PoetrySequence sequence=new PoetrySequence();
        sequence.audioCompleted();assertEquals(3_000,sequence.delayMs());
        sequence.cancel();assertEquals(PoetrySequence.Phase.WAIT_MEANING,sequence.phase());
        assertFalse(sequence.finishWait());assertEquals(PoetrySequence.Phase.MEANING,sequence.phase());
        sequence.audioCompleted();assertEquals(5_000,sequence.delayMs());
        assertTrue(sequence.finishWait());assertEquals(PoetrySequence.Phase.RECITATION,sequence.phase());
        sequence.audioCompleted();sequence.reset();assertEquals(PoetrySequence.Phase.RECITATION,sequence.phase());
        assertEquals(0,sequence.delayMs());
    }
    @Test public void meaningAudioUsesTheSameLocalPathBoundary() throws Exception {
        for(String field:new String[]{"meaningAudio","listeningAudio"})for(String path:new String[]{"../bad.mp3","audio/../../bad.mp3","https://example.com/a.mp3","/sdcard/a.mp3","audio/sub/a.mp3"}){
            JSONObject root=new JSONObject(data());root.getJSONArray("poems").getJSONObject(0).put(field,path);reject(root.toString());
        }
        JSONObject root=new JSONObject(data());root.getJSONArray("poems").getJSONObject(0).put("meaningAudio","audio/p001-meaning.m4a");
        PoemCatalog catalog=PoemCatalog.parse(root.toString(),new File("missing-poetry-library"),"");
        assertEquals("audio/p001-meaning.m4a",catalog.poems.get(0).meaningAudio);assertNull(catalog.meaningAudio(catalog.poems.get(0)));
        root.getJSONArray("poems").getJSONObject(0).put("listeningAudio","audio/p001-listening.m4a");
        assertEquals("audio/p001-listening.m4a",PoemCatalog.parse(root.toString(),null,"").poems.get(0).audio);
    }
    @Test public void listeningDoesNotLeaveAnIsolatedCharacterFromAPairedVerse() {
        assertEquals("泉眼无声惜细流，\n树阴照水爱晴柔。\n小荷才露尖尖角，\n早有蜻蜓立上头。",PoetryText.listening("泉眼无声惜细流，树阴照水爱晴柔。\n小荷才露尖尖角，早有蜻蜓立上头。"));
        assertTrue(PoetryText.listening("鹅，鹅，鹅，曲项向天歌。").startsWith("鹅，鹅，鹅，\n"));
    }
    private void reject(String json) { try{PoemCatalog.parse(json,null,"");fail("Accepted invalid library");}catch(Exception expected){} }
}
