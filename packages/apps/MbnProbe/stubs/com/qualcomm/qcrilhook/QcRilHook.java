package com.qualcomm.qcrilhook;

import android.content.Context;

public class QcRilHook {
    public QcRilHook(Context context, QcRilHookCallback callback) {
    }

    public boolean qcRilGetAllConfigs() { return false; }
    public String qcRilGetConfig() { return null; }
    public String qcRilGetConfig(int phoneId) { return null; }
    public String qcRilGetConfig(int phoneId, int type) { return null; }

    public String qcRilGetMetaInfoForConfig(String configId) { return null; }
    public String qcRilGetMetaInfoForConfig(String configId, int phoneId) { return null; }
    public byte[] qcRilGetQcVersionOfID(String configId) { return null; }
    public byte[] qcRilGetOemVersionOfID(String configId) { return null; }
    public byte[] qcRilGetQcVersionOfFile(String file) { return null; }
    public byte[] qcRilGetOemVersionOfFile(String file) { return null; }
    public String qcRilGetAvailableConfigs(String arg) { return null; }

    public boolean qcRilDisableMbnAutoSel() { return false; }

    public boolean qcRilSetConfig(String file, String configId, int sub) { return false; }
    public boolean qcRilSetConfig(String file, String configId, int sub, int type) { return false; }
    public boolean qcRilSelectConfig(String configId, int sub) { return false; }
    public boolean qcRilSelectConfig(String configId, int sub, int type) { return false; }
    public boolean qcRilActivateConfig(int sub) { return false; }
    public boolean qcRilActivateConfig(int sub, int type) { return false; }
}
