package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.widget.Toast;

/** One-time Android7-9 removable-volume grant. No root, shell service or unattended permission change. */
public final class CacheAccessActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        try {
            for (StorageVolume volume : getSystemService(StorageManager.class).getStorageVolumes()) {
                if (!volume.isRemovable() || volume.getUuid() == null) continue;
                Intent request = volume.createAccessIntent(null);
                if (request != null) { startActivityForResult(request,1); return; }
            }
        } catch (RuntimeException unavailable) { }
        Toast.makeText(this,"U盘暂不可授权，继续使用本机缓存",Toast.LENGTH_LONG).show(); finish();
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        boolean saved = false;
        try { saved = code == 1 && result == RESULT_OK && data != null && UsbCacheAccess.remember(this,data); }
        catch (RuntimeException unavailable) { }
        Toast.makeText(this,saved ? "U盘缓存已启用" : "继续使用本机缓存",Toast.LENGTH_SHORT).show(); finish();
    }
}
