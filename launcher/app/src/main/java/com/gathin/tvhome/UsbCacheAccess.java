package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;

/** Sony Android9 uses scoped volume permission for USB writes, even with WRITE_EXTERNAL_STORAGE granted. */
final class UsbCacheAccess {
    private static Context app;
    static void initialize(Context context) { app = context.getApplicationContext(); }
    static boolean available(File volume) { return tree(volume.getName()) != null; }
    private static Uri tree(String uuid) {
        if (app == null) return null;
        String saved = app.getSharedPreferences("usb_cache_access",0).getString(uuid,"");
        if (saved.isEmpty()) return null;
        Uri uri = Uri.parse(saved);
        for (android.content.UriPermission permission : app.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isWritePermission()) return uri;
        }
        return null;
    }
    static boolean remember(Context context, Intent reply) {
        initialize(context); Uri uri = reply.getData(); if (uri == null) return false;
        String id = DocumentsContract.getTreeDocumentId(uri);
        for (File root : UsbWallpapers.roots(context)) {
            if (!id.equals(root.getName()+":")) continue;
            int flags = reply.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) return false;
            app.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return app.getSharedPreferences("usb_cache_access",0).edit().putString(root.getName(),uri.toString()).commit();
        }
        return false;
    }
    static File staging(File destination) {
        if (app == null) return new File(destination.getPath()+".part");
        return new File(app.getCacheDir(),"usb-"+destination.getName()+".part");
    }
    static boolean publish(File source, File destination) throws Exception {
        if (app == null) return false;
        String path = destination.getAbsolutePath();
        if (!path.matches("/storage/[A-Za-z0-9-]+/\\.tvcache/(album|home)/[^/]+--[a-f0-9]{64}\\.jpg")) return false;
        File kind = destination.getParentFile(), base = kind.getParentFile(), volume = base.getParentFile();
        Uri grant = tree(volume.getName()); if (grant == null) return false;
        String rootId = DocumentsContract.getTreeDocumentId(grant);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(grant,rootId);
        Uri cache = folder(grant,root,rootId+".tvcache",base,".tvcache");
        Uri parent = folder(grant,cache,rootId+".tvcache/"+kind.getName(),kind,kind.getName());
        String partName = destination.getName()+".part-"+android.os.Process.myPid()+"-"+System.nanoTime()+".jpg";
        Uri part = DocumentsContract.createDocument(app.getContentResolver(),parent,"image/jpeg",partName);
        if (part == null) return false;
        boolean published = false;
        try {
            try (java.io.InputStream input = new java.io.FileInputStream(source);
                 java.io.OutputStream output = app.getContentResolver().openOutputStream(part,"wt")) {
                if (output == null) return false;
                byte[] buffer = new byte[32768]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("USB cache write cancelled");
                    output.write(buffer,0,count);
                }
            }
            if (destination.isFile()) {
                Uri previous = DocumentsContract.buildDocumentUriUsingTree(grant,rootId+".tvcache/"+kind.getName()+"/"+destination.getName());
                DocumentsContract.deleteDocument(app.getContentResolver(),previous);
            }
            published = DocumentsContract.renameDocument(app.getContentResolver(),part,destination.getName()) != null;
            return published;
        } finally {
            if (!published && new File(kind, partName).isFile()) {
                try { DocumentsContract.deleteDocument(app.getContentResolver(),part); } catch (Exception ignored) { }
            }
        }
    }
    private static Uri folder(Uri grant, Uri parent, String id, File file, String name) throws Exception {
        if (file.isDirectory()) return DocumentsContract.buildDocumentUriUsingTree(grant,id);
        Uri result = DocumentsContract.createDocument(app.getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,name);
        if (result == null) throw new java.io.IOException("USB cache directory unavailable"); return result;
    }
}
