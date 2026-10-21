package de.robv.android.xposed;

import java.util.Set;

/** Stub of the Xposed API (compile only). */
public final class XposedBridge {
    private XposedBridge() {
    }

    public static Set<?> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        throw new UnsupportedOperationException("stub");
    }

    public static void log(String text) {
        throw new UnsupportedOperationException("stub");
    }
}
