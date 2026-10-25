package com.example.mbnprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.qualcomm.qcrilhook.QcRilHook;
import com.qualcomm.qcrilhook.QcRilHookCallback;

public class MbnProbeActivity extends Activity {
    private static final String T = "MBNPROBE";
    private static final String SUPER_ID = "797007684bc56aa94a932a27a8600304bd5134ff";
    private static final String SUPER_FILE = "/vendor/fsg/emea_apac_super.mbn";
    private QcRilHook h;
    private int mask = 1; // bitmask of slots: 1 = slot0, 2 = slot1, 3 = both

    private static String idStr(String hex) throws Exception {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return new String(b, "ISO-8859-1");
    }

    private static String hex(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(String.format("%02x", (int) c & 0xff));
        return b + " (len=" + s.length() + ")";
    }

    private static String hex(byte[] a) {
        if (a == null) return "null";
        StringBuilder b = new StringBuilder();
        for (byte x : a) b.append(String.format("%02x", x));
        return b.toString();
    }

    private String meta(String id) {
        if (id == null || id.isEmpty()) return "-";
        try { return String.valueOf(h.qcRilGetMetaInfoForConfig(id, 0)); }
        catch (Throwable e) { return "ERR:" + e; }
    }

    private boolean loaded(String id) {
        if (id == null || id.isEmpty()) return false;
        try { return h.qcRilGetMetaInfoForConfig(id, 0) != null; }
        catch (Throwable e) { return false; }
    }

    private boolean waitLoaded(String id, int ms) throws Exception {
        for (int t = 0; t < ms; t += 500) {
            if (loaded(id)) return true;
            Thread.sleep(500);
        }
        return loaded(id);
    }

    private void state(String tag) {
        try {
            Log.i(T, tag + " getConfig()=" + hex(h.qcRilGetConfig()));
            for (int s = 0; s <= 1; s++) {
                String id = h.qcRilGetConfig(s);
                Log.i(T, tag + " getConfig(" + s + ")=" + hex(id) + " meta=" + meta(id));
                for (int t = 0; t <= 1; t++) {
                    String x = h.qcRilGetConfig(s, t);
                    Log.i(T, tag + " getConfig(" + s + "," + t + ")=" + hex(x) + " meta=" + meta(x));
                }
            }
        } catch (Throwable e) { Log.e(T, tag + " state failed: " + e); }
    }

    // Load the MBN into the modem (no select, no activate). Returns true if loaded.
    private boolean loadOnly(String file, String id) throws Exception {
        if (loaded(id)) {
            Log.i(T, "already loaded: " + meta(id));
            return true;
        }
        Log.i(T, "setConfig(mask=" + mask + ")=" + h.qcRilSetConfig(file, id, mask));
        boolean ok = waitLoaded(id, 10000);
        Log.i(T, "loaded after set=" + ok + " meta=" + meta(id));
        return ok;
    }

    private void apply(String file, String id) throws Exception {
        state("pre");
        if (!loadOnly(file, id)) {
            Log.i(T, "STOP: config not loaded, not selecting");
            return;
        }
        Log.i(T, "select(mask=" + mask + ")=" + h.qcRilSelectConfig(id, mask));
        Log.i(T, "activate(mask=" + mask + ")=" + h.qcRilActivateConfig(mask));
        Thread.sleep(10000);
        state("post10s");
        Thread.sleep(15000);
        state("post25s");
    }

    private void run(String stage, String file, String idHex) throws Exception {
        switch (stage) {
            case "1": { // read-only
                state("state");
                if (file != null) {
                    Log.i(T, "fileQcVer=" + hex(h.qcRilGetQcVersionOfFile(file))
                        + " fileOemVer=" + hex(h.qcRilGetOemVersionOfFile(file)));
                }
                if (idHex != null) Log.i(T, "metaOfCandidate=" + meta(idStr(idHex)));
                break;
            }
            case "2": { // load only (no select/activate)
                if (file == null || idHex == null) { Log.e(T, "need file and id"); return; }
                state("pre");
                loadOnly(file, idStr(idHex));
                state("post-load");
                break;
            }
            case "5": { // load + select + activate candidate
                if (file == null || idHex == null) { Log.e(T, "need file and id"); return; }
                apply(file, idStr(idHex));
                break;
            }
            case "8":
            case "9": // restore emea_apac_super
                apply(SUPER_FILE, idStr(SUPER_ID));
                break;
            default:
                Log.e(T, "unknown stage " + stage);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String stage = getIntent().getStringExtra("stage");
        final String file = getIntent().getStringExtra("file");
        final String id = getIntent().getStringExtra("id");
        mask = getIntent().getIntExtra("mask", 1);
        Log.i(T, "MbnProbe v5 stage=" + stage + " mask=" + mask);
        h = new QcRilHook(this, new QcRilHookCallback() {
            public void onQcRilHookReady() {
                new Thread(() -> {
                    try {
                        if (stage == null) state("idle");
                        else run(stage, file, id);
                    } catch (Throwable e) { Log.e(T, "failed: " + e); }
                }).start();
            }
            public void onQcRilHookDisconnected() {}
        });
    }
}
