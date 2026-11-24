package barakah.khelcom.moubarak;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/* Dossiers de la galerie MOUBARAK : Moubarak Images, Moubarak Vidéos, Status Moubarak, Vidéos téléchargées */
@CapacitorPlugin(name = "MoubarakMedia")
public class MoubarakMediaPlugin extends Plugin {

    private static class Sess {
        OutputStream os;
        Uri uri;
        File file;
        boolean video;
    }

    private final Map<String, Sess> sessions = new HashMap<>();

    private static String folderName(String key) {
        if ("images".equals(key)) return "Moubarak Images";
        if ("videos".equals(key)) return "Moubarak Vidéos";
        if ("status".equals(key)) return "Status Moubarak";
        return "Vidéos téléchargées";
    }

    private boolean needLegacyPermission() {
        return Build.VERSION.SDK_INT < 29
                && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    @PluginMethod
    public void createFolders(PluginCall call) {
        try {
            if (needLegacyPermission()) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7731);
            }
            String[] keys = {"images", "videos", "status", "downloaded"};
            for (String k : keys) {
                try {
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName(k)).mkdirs();
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), folderName(k)).mkdirs();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        call.resolve();
    }

    @PluginMethod
    public void begin(PluginCall call) {
        try {
            String name = call.getString("name", "moubarak");
            String mime = call.getString("mime", "image/jpeg");
            String folder = folderName(call.getString("folder", "images"));
            boolean video = mime.startsWith("video");
            String base = video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
            Sess s = new Sess();
            s.video = video;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, base + "/" + folder);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                ContentResolver r = getContext().getContentResolver();
                Uri coll = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                s.uri = r.insert(coll, v);
                if (s.uri == null) { call.reject("insert"); return; }
                s.os = r.openOutputStream(s.uri);
            } else {
                if (needLegacyPermission()) { call.reject("permission"); return; }
                File dir = new File(Environment.getExternalStoragePublicDirectory(base), folder);
                dir.mkdirs();
                s.file = new File(dir, name);
                s.os = new FileOutputStream(s.file);
            }
            String id = UUID.randomUUID().toString();
            sessions.put(id, s);
            JSObject o = new JSObject();
            o.put("id", id);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("begin: " + e.getMessage());
        }
    }

    @PluginMethod
    public void append(PluginCall call) {
        try {
            Sess s = sessions.get(call.getString("id", ""));
            if (s == null) { call.reject("session"); return; }
            byte[] b = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            s.os.write(b);
            call.resolve();
        } catch (Exception e) {
            call.reject("append: " + e.getMessage());
        }
    }

    @PluginMethod
    public void finish(PluginCall call) {
        try {
            Sess s = sessions.remove(call.getString("id", ""));
            if (s == null) { call.reject("session"); return; }
            s.os.flush();
            s.os.close();
            if (s.uri != null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContext().getContentResolver().update(s.uri, v, null, null);
            } else if (s.file != null) {
                MediaScannerConnection.scanFile(getContext(), new String[]{s.file.getAbsolutePath()}, null, null);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("finish: " + e.getMessage());
        }
    }

    @PluginMethod
    public void abort(PluginCall call) {
        try {
            Sess s = sessions.remove(call.getString("id", ""));
            if (s != null) {
                try { s.os.close(); } catch (Exception ignored) { }
                if (s.uri != null) getContext().getContentResolver().delete(s.uri, null, null);
                if (s.file != null) s.file.delete();
            }
        } catch (Exception ignored) {
        }
        call.resolve();
    }
  }
