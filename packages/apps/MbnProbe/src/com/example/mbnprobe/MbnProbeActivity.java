
package com.example.mbnprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.util.Arrays;
import com.qualcomm.qcrilhook.QcRilHook;
import com.qualcomm.qcrilhook.QcRilHookCallback;

public class MbnProbeActivity extends Activity {
    private static final String T = "MBNPROBE";
    private static final String SUPER_ID = "797007684bc56aa94a932a27a8600304bd5134ff";
    private static final String SUPER_FILE = "/vendor/fsg/emea_apac_super.mbn";
    private QcRilHook h;
    private int mask = 1; // bitmask of slots: 1 = slot0, 2 = slot1, 3 = both

    private volatile int rxCount = 0;
    private volatile int lastErr = -1;
    private volatile String lastActive = null;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            StringBuilder b = new StringBuilder("RX ").append(i.getAction()).append(' ');
            Bundle e = i.getExtras();
            if (e != null) {
                for (String k : e.keySet()) {
                    Object v = e.get(k);
                    b.append(k).append('=');
                    if (v instanceof byte[]) b.append("0x").append(hex((byte[]) v));
                    else if (v instanceof int[]) b.append(Arrays.toString((int[]) v));
                    else if (v instanceof String[]) b.append(Arrays.toString((String[]) v));
                    else b.append(v);
                    b.append(' ');
                }
            }
            Log.i(T, b.toString());

            if ("qualcomm.intent.action.ACTION_PDC_DATA_RECEIVED".equals(i.getAction()) && e != null) {
                Object er = e.get("error");
                lastErr = (er instanceof Number) ? ((Number) er).intValue() : -1;
                Object ac = e.get("active");
                lastActive = (ac instanceof byte[]) ? hex((byte[]) ac) : String.valueOf(ac);
                rxCount++;
            }
        }
    };

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

    private boolean waitRx(int before, int ms) throws Exception {
        for (int t = 0; t < ms; t += 200) {
            if (rxCount > before) return true;
            Thread.sleep(200);
        }
        return rxCount > before;
    }

    private void quick(String tag) {
        String id = h.qcRilGetConfig(0);
        Log.i(T, tag + " active=" + hex(id) + " meta=" + meta(id));
    }

    private void switchTo(String idHex) throws Exception {
        String id = idStr(idHex);
        String cur = h.qcRilGetConfig(0);

        if (cur == null || cur.isEmpty()) {
            Log.e(T, "ABORT: PDC not ready");
            return;
        }

        if (!loaded(id)) {
            Log.e(T, "ABORT: target not loaded");
            return;
        }

        quick("before");

        int n0 = rxCount;
        Log.i(T, "select=" + h.qcRilSelectConfig(id, mask));

        if (!waitRx(n0, 8000)) {
            Log.e(T, "ABORT: no select RX");
            return;
        }

        Log.i(T, "select RX error=" + lastErr + " active=" + lastActive);

        if (lastErr != 0) {
            Log.e(T, "ABORT: select error");
            return;
        }

        Log.i(T, "activate=" + h.qcRilActivateConfig(mask));

        for (int k = 1; k <= 18; k++) {
            Thread.sleep(5000);
            quick("act+" + (k * 5) + "s");
        }
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
                if (file == null || idHex == null) {
                    Log.e(T, "need file and id");
                    return;
                }
                state("pre");
                loadOnly(file, idStr(idHex));
                state("post-load");
                break;
            }
            case "5": { // load + select + activate candidate
                if (file == null || idHex == null) {
                    Log.e(T, "need file and id");
                    return;
                }
                apply(file, idStr(idHex));
                break;
            }
            case "6": { // only select
                String cur = h.qcRilGetConfig(0);
                if (cur == null || cur.isEmpty()) {
                    Log.e(T, "ABORT: PDC not ready");
                    return;
                }
                String id = idStr(idHex);
                if (!loaded(id)) {
                    Log.e(T, "ABORT: candidate not loaded");
                    return;
                }
                state("before6");
                Log.i(T, "select(mask=" + mask + ")=" + h.qcRilSelectConfig(id, mask));
                for (int i = 1; i <= 6; i++) {
                    Thread.sleep(5000);
                    state("t+" + (i * 5) + "s");
                }
                break;
            }
            case "7": { // only select with back to emea_apac_super
                String cur = h.qcRilGetConfig(0);
                if (cur == null || cur.isEmpty()) {
                    Log.e(T, "ABORT: PDC not ready");
                    return;
                }
                String sid = idStr(SUPER_ID);
                if (!loaded(sid)) {
                    Log.e(T, "ABORT: super not loaded");
                    return;
                }
                Log.i(T, "select-super(mask=" + mask + ")=" + h.qcRilSelectConfig(sid, mask));
                for (int i = 1; i <= 4; i++) {
                    Thread.sleep(5000);
                    state("rev+" + (i * 5) + "s");
                }
                break;
            }
            case "8":
            case "9": // restore emea_apac_super
                apply(SUPER_FILE, idStr(SUPER_ID));
                break;
            case "10": {
                Log.i(T, "getAllConfigs=" + h.qcRilGetAllConfigs());
                Thread.sleep(10000);
                break;
            }
            case "11":
                switchTo(idHex);
                break;
            case "12":
                switchTo(SUPER_ID);
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
        Log.i(T, "MbnProbe v8.1 stage=" + stage + " mask=" + mask);

        IntentFilter f = new IntentFilter();
        f.addAction("com.qualcomm.intent.action.ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW");
        f.addAction("qualcomm.intent.action.ACTION_PDC_DATA_RECEIVED");
        f.addAction("qualcomm.intent.action.ACTION_PDC_CONFIG_LIST_RECEIVED");
        f.addAction("qualcomm.intent.action.ACTION_PDC_CONFIGS_CLEARED");
        f.addAction("qualcomm.intent.action.ACTION_PDC_CONFIGS_VALIDATION");
        f.addAction("qualcomm.intent.action.ACTIN_PDC_VALIDATE_DUMPED");
        getApplicationContext().registerReceiver(rx, f, Context.RECEIVER_EXPORTED);

        h = new QcRilHook(this, new QcRilHookCallback() {
            public void onQcRilHookReady() {
                new Thread(() -> {
                    try {
                        if (stage == null) state("idle");
                        else run(stage, file, id);
                    } catch (Throwable e) {
                        Log.e(T, "failed", e);
                    }
                }).start();
            }
            public void onQcRilHookDisconnected() {}
        });
    }
}

