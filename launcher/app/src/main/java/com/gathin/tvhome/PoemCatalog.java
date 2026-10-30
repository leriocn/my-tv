package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Bounded, editable UTF-8 library. Audio paths stay inside the selected library. */
final class PoemCatalog {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final class Poem {
        final String id, title, author, dynasty, body, meaning, audio, meaningAudio, narrator;
        final int grade, term;
        Poem(JSONObject value) throws Exception {
            id = value.getString("id");
            if (!id.matches("[A-Za-z0-9_-]{1,60}")) throw new IOException("Invalid poem id");
            title = text(value,"title",80); author = text(value,"author",80);
            dynasty = value.optString("dynasty","");
            grade = value.getInt("grade"); term = value.getInt("term");
            if (grade < 1 || grade > 5 || term < 1 || term > 2) throw new IOException("Invalid grade or term");
            JSONArray lines = value.getJSONArray("lines");
            if (lines.length() < 1 || lines.length() > 40) throw new IOException("Invalid lines");
            StringBuilder content = new StringBuilder();
            for (int i=0; i<lines.length(); i++) {
                String line = lines.getString(i).trim();
                if (line.isEmpty() || line.length()>100) throw new IOException("Invalid verse line");
                if (i>0) content.append('\n'); content.append(line);
            }
            body = content.toString(); meaning = text(value,"meaning",2400);
            String originalAudio=audioPath(value.optString("audio",""));
            audio = audioPath(value.optString("listeningAudio",originalAudio));
            meaningAudio = audioPath(value.optString("meaningAudio",""));
            narrator = value.optString("narrator","");
        }
        String credit() { return (dynasty.isEmpty() ? "" : dynasty + " · ") + author; }
        String book() { return "一二三四五".charAt(grade-1)+"年级"+(term==1?"上册":"下册"); }
    }
    final List<Poem> poems;
    final File directory;
    final String notice;
    private PoemCatalog(List<Poem> poems, File directory, String notice) {
        this.poems = Collections.unmodifiableList(poems); this.directory = directory; this.notice = notice;
    }
    static PoemCatalog parse(String json, File directory, String notice) throws Exception {
        if (json.length() > MAX_BYTES) throw new IOException("Library too large");
        JSONObject root = new JSONObject(json);
        if (root.getInt("version") != 1) throw new IOException("Unsupported library version");
        JSONArray array = root.getJSONArray("poems");
        if (array.length() < 1 || array.length() > 256) throw new IOException("Invalid library size");
        List<Poem> poems = new ArrayList<>(); HashSet<String> ids = new HashSet<>();
        for (int i=0; i<array.length(); i++) {
            Poem poem = new Poem(array.getJSONObject(i));
            if (!ids.add(poem.id)) throw new IOException("Duplicate poem id: "+poem.id);
            poems.add(poem);
        }
        return new PoemCatalog(poems,directory,notice);
    }
    int index(String id) {
        for (int i=0; i<poems.size(); i++) if (poems.get(i).id.equals(id)) return i;
        return 0;
    }
    static int step(int current, int delta, int size) { return Math.floorMod(current+delta,size); }
    File audio(Poem poem) throws IOException {
        return localAudio(poem.audio);
    }
    File meaningAudio(Poem poem) throws IOException { return localAudio(poem.meaningAudio); }
    private File localAudio(String path) throws IOException {
        if (directory == null || path.isEmpty()) return null;
        File file = new File(directory,path).getCanonicalFile();
        if (!file.getPath().startsWith(directory.getCanonicalPath()+File.separator)) throw new IOException("Audio outside library");
        return file.isFile() && file.length() > 0 ? file : null;
    }
    private static String audioPath(String path) throws IOException {
        if (!path.isEmpty() && !path.matches("audio/[A-Za-z0-9_-]{1,100}\\.(m4a|mp3|ogg|opus|wav)"))
            throw new IOException("Audio must be a local file in audio/");
        return path;
    }
    private static String text(JSONObject object, String key, int max) throws Exception {
        String text = object.getString(key).trim();
        if (text.isEmpty() || text.length()>max) throw new IOException("Invalid "+key); return text;
    }
}
