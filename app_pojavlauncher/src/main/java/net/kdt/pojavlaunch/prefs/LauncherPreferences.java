package net.kdt.pojavlaunch.prefs;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;
import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import java.io.IOException;

public class LauncherPreferences {
    public static final String PREF_KEY_CURRENT_PROFILE = "currentProfile";
    public static final String PREF_KEY_SKIP_NOTIFICATION_CHECK = "skipNotificationPermissionCheck";

    public static SharedPreferences DEFAULT_PREF;
    
    // ZALITH MOD: Default to VirGL for better FPS on older Adreno GPUs
    public static String PREF_RENDERER = "virgl"; 

	public static boolean PREF_IGNORE_NOTCH = false;
	public static int PREF_NOTCH_SIZE = 0;
	public static float PREF_BUTTONSIZE = 100f;
	public static float PREF_MOUSESCALE = 1f;
	public static int PREF_LONGPRESS_TRIGGER = 300;
	public static String PREF_DEFAULTCTRL_PATH = Tools.CTRLDEF_FILE;

    // ZALITH MOD: High Performance JVM Args for 3GB RAM Devices
	public static String PREF_CUSTOM_JAVA_ARGS = "-Xmx1024M -Xms512M -XX:+UseG1GC -XX:MaxGCPauseMillis=25 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -Dsun.renderer=no";

    public static boolean PREF_FORCE_ENGLISH = false;
    public static final String PREF_VERSION_REPOS = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static boolean PREF_CHECK_LIBRARY_SHA = true;
    public static boolean PREF_DISABLE_GESTURES = false;
    public static boolean PREF_DISABLE_SWAP_HAND = false;
    public static float PREF_MOUSESPEED = 1f;
    public static int PREF_RAM_ALLOCATION;
    public static String PREF_DEFAULT_RUNTIME;
    public static boolean PREF_SUSTAINED_PERFORMANCE = true; // Forced ON for stability
    public static boolean PREF_VIRTUAL_MOUSE_START = false;
    public static boolean PREF_ARC_CAPES = false;
    public static boolean PREF_USE_ALTERNATE_SURFACE = true;
    public static boolean PREF_JAVA_SANDBOX = true;
    public static float PREF_SCALE_FACTOR = 1f;

    // ... (rest of the static variables remain same)

    public static void loadPreferences(Context ctx) {
        Tools.initStorageConstants(ctx);
        boolean isDevicePowerful = isDevicePowerful(ctx);

        // ZALITH MOD: Default Renderer set to virgl if not specified
        PREF_RENDERER = DEFAULT_PREF.getString("renderer", "virgl");
        PREF_BUTTONSIZE = DEFAULT_PREF.getInt("buttonscale", 100);
        PREF_MOUSESCALE = DEFAULT_PREF.getInt("mousescale", 100)/100f;
        PREF_MOUSESPEED = ((float)DEFAULT_PREF.getInt("mousespeed",100))/100f;
        PREF_IGNORE_NOTCH = DEFAULT_PREF.getBoolean("ignoreNotch", false);
		PREF_LONGPRESS_TRIGGER = DEFAULT_PREF.getInt("timeLongPressTrigger", 300);
		PREF_DEFAULTCTRL_PATH = DEFAULT_PREF.getString("defaultCtrl", Tools.CTRLDEF_FILE);
        PREF_FORCE_ENGLISH = DEFAULT_PREF.getBoolean("force_english", false);
        PREF_CHECK_LIBRARY_SHA = DEFAULT_PREF.getBoolean("checkLibraries",true);
        PREF_DISABLE_GESTURES = DEFAULT_PREF.getBoolean("disableGestures",false);
        PREF_DISABLE_SWAP_HAND = DEFAULT_PREF.getBoolean("disableDoubleTap", false);
        PREF_RAM_ALLOCATION = DEFAULT_PREF.getInt("allocation", findBestRAMAllocation(ctx));
        
        // ZALITH MOD: Custom Java Args Injection
        PREF_CUSTOM_JAVA_ARGS = DEFAULT_PREF.getString("javaArgs", PREF_CUSTOM_JAVA_ARGS);
        
        PREF_SUSTAINED_PERFORMANCE = DEFAULT_PREF.getBoolean("sustainedPerformance", true);
        PREF_VIRTUAL_MOUSE_START = DEFAULT_PREF.getBoolean("mouse_start", false);
        PREF_ARC_CAPES = DEFAULT_PREF.getBoolean("arc_capes",false);
        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", true);
        PREF_JAVA_SANDBOX = DEFAULT_PREF.getBoolean("java_sandbox", true);
        
        // Lower default resolution for stability
        PREF_SCALE_FACTOR = DEFAULT_PREF.getInt("resolutionRatio", 85)/100f; 

        // ... (rest of the loading logic)
    }

    /**
     * ZALITH OPTIMIZED RAM ALLOCATION
     * Specifically tuned for 3GB RAM devices (Redmi Y3)
     */
    private static int findBestRAMAllocation(Context ctx){
        int deviceRam = Tools.getTotalDeviceMemory(ctx);
        if (deviceRam < 1024) return 350;
        if (deviceRam < 1536) return 512;
        if (deviceRam < 2048) return 768;
        
        // Redmi Y3 (3GB RAM) usually shows ~2800MB total
        if (deviceRam < 3064) return 1024; // Sweet spot for 3GB devices
        
        if (deviceRam < 4096) return 1400;
        return 2048; 
    }

            // --- KANPUR DEV ROOM MISSING VARIABLES ---
    public static boolean PREF_ENABLE_GYRO = false;
    public static boolean PREF_GYRO_INVERT_X = false;
    public static boolean PREF_GYRO_INVERT_Y = false;
    public static float PREF_GYRO_SENSITIVITY = 1.0f;
    public static float PREF_DEADZONE_SCALE = 0.5f;
    public static boolean PREF_DUMP_SHADERS = false;
    public static boolean PREF_VSYNC_IN_ZINK = false;
    public static boolean PREF_ZINK_PREFER_SYSTEM_DRIVER = false;
    public static boolean PREF_FORCE_VSYNC = false;
    public static boolean PREF_BIG_CORE_AFFINITY = false;
    public static boolean PREF_VERIFY_MANIFEST = true;
    public static String PREF_DOWNLOAD_SOURCE = "default";
    public static boolean PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = false;
    public static boolean PREF_BUTTON_ALL_CAPS = true;
    public static boolean PREF_GYRO_SMOOTHING = false;
    public static int PREF_GYRO_SAMPLE_RATE = 100;

    public static boolean isDevicePowerful(android.content.Context ctx) { return true; }
    public static void computeNotchSize(android.app.Activity act) { }

    // ... (keep the rest of the file same)
}
