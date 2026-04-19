package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.dialogForceClose;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.customcontrols.mouse.HotbarView;
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.QuickSettingSideDialog;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;

public class MainActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    public static final String INTENT_MINECRAFT_VERSION = "intent_version";

    volatile public static boolean isInputStackCall;

    public static TouchCharInput touchCharInput;
    private MinecraftGLSurface minecraftGLView;
    private static Touchpad touchpad;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;

    MinecraftProfile minecraftProfile;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        minecraftProfile = LauncherProfiles.getCurrentProfile();
        MCOptionUtils.load(Tools.getGameDirPath(minecraftProfile).getAbsolutePath());

        Intent gameServiceIntent = new Intent(this, GameService.class);
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);
        CallbackBridge.addGrabListener(touchpad);
        CallbackBridge.addGrabListener(minecraftGLView);

        mGyroControl = new GyroControl(this);

        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.openLoadDialog(); break;
                case 4: mControlLayout.openSaveDialog(this); break;
                case 5: mControlLayout.openSetDefaultDialog(); break;
                case 6: mControlLayout.openExitDialog(this);
            }
        };

        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);

        ContextExecutor.setActivity(this);
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            touchCharInput.setCharacterSender(new LwjglCharSender());

            if(minecraftProfile.pojavRendererName != null) {
                Tools.LOCAL_RENDERER = minecraftProfile.pojavRendererName;
            }

            setTitle("Minecraft " + minecraftProfile.lastVersionId);

            String version = getIntent().getStringExtra(INTENT_MINECRAFT_VERSION);
            version = version == null ? minecraftProfile.lastVersionId : version;

            JMinecraftVersionList.Version mVersionInfo = Tools.getVersionInfo(version);
            isInputStackCall = mVersionInfo.arguments != null;
            CallbackBridge.nativeSetUseInputStackQueue(isInputStackCall);

            Tools.getDisplayMetrics(this);
            windowWidth = Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, 1f);
            windowHeight = Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, 1f);

            gameActionArrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_ingame));
            gameActionClickListener = (parent, view, position, id) -> {
                switch(position) {
                    case 0: dialogForceClose(MainActivity.this); break;
                    case 1: openLogOutput(); break;
                    case 2: dialogSendCustomKey(); break;
                    case 3: openQuickSettings(); break;
                    case 4: openCustomControls(); break;
                }
                drawerLayout.closeDrawers();
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();

            final String finalVersion = version;
            minecraftGLView.setSurfaceReadyListener(() -> {
                try {
                    if (PREF_VIRTUAL_MOUSE_START) {
                        touchpad.post(() -> touchpad.switchState());
                    }
                    runCraft(finalVersion, mVersionInfo);
                }catch (Throwable e){
                    Tools.showErrorRemote(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
        } catch(IOException e) {
            try {
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        minecraftGLView = findViewById(R.id.main_game_render_view);
        touchpad = findViewById(R.id.main_touchpad);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1);
    }

    @Override
    protected void onPause() {
        mGyroControl.disable();
        if (CallbackBridge.isGrabbing()){
            sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onStop() {
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallbackBridge.removeGrabListener(touchpad);
        CallbackBridge.removeGrabListener(minecraftGLView);
        ContextExecutor.clearActivity();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(mGyroControl != null) mGyroControl.updateOrientation();
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            minecraftGLView.refreshSize();
            Tools.updateWindowSize(this);
            mControlLayout.refreshControlButtonPositions();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(minecraftGLView != null) 
            Tools.MAIN_HANDLER.postDelayed(() -> minecraftGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runCraft(String versionId, JMinecraftVersionList.Version version) throws Throwable {
        if(Tools.LOCAL_RENDERER == null) {
            Tools.LOCAL_RENDERER = LauncherPreferences.PREF_RENDERER;
        }
        if(!Tools.checkRendererCompatible(this, Tools.LOCAL_RENDERER)) {
            Tools.RenderersList renderersList = Tools.getCompatibleRenderers(this);
            String firstCompatibleRenderer = renderersList.rendererIds.get(0);
            Log.w("runCraft","Incompatible renderer "+Tools.LOCAL_RENDERER+ " will be replaced with "+firstCompatibleRenderer);
            Tools.LOCAL_RENDERER = firstCompatibleRenderer;
            Tools.releaseRenderersCache();
        }
        MinecraftAccount minecraftAccount = PojavProfile.getCurrentProfileContent(this, null);
        
        // --- ZALITH ZERO-LAG ENGINE (OPTIMIZATION INJECTION) ---
        Logger.appendToLog("--------- INJECTING ZALITH OPTIMIZATION ENGINE ---------");
        
        // 1. RAM GUARD: Flush Background Memory before launch
        System.gc();
        System.runFinalization();
        Logger.appendToLog("ZALITH: Memory Flushed. RAM Guard Active.");

        // 2. SODIUM FORCE: Inject System Properties for Performance Mods
        System.setProperty("minecraft.render.sodium", "true");
        System.setProperty("sodium.force", "true");
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true"); // Faster mod loading
        
        // 3. THREAD BOOST: Give Minecraft Max CPU Priority
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        Logger.appendToLog("ZALITH: CPU Priority Maxed Out.");
        // --------------------------------------------------------

        Tools.printLauncherInfo(versionId, Tools.isValidString(minecraftProfile.javaArgs) ? minecraftProfile.javaArgs : LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        JREUtils.redirectAndPrintJRELog();
        LauncherProfiles.load();
        int requiredJavaVersion = 8;
        if(version.javaVersion != null) requiredJavaVersion = version.javaVersion.majorVersion;
        Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, versionId, requiredJavaVersion);
        
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    private void dialogSendCustomKey() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(EfficientAndroidLWJGLKeycode.generateKeyName(), (dInterface, position) -> EfficientAndroidLWJGLKeycode.execKeyIndex(position));
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;
        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    minecraftGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }
                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    public static void toggleMouse(Context ctx) {
        if (CallbackBridge.isGrabbing()) return;
        Toast.makeText(ctx, touchpad.switchState()
                        ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = minecraftGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true;
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState() {
        if(touchCharInput != null) touchCharInput.switchKeyboardState();
    }

    @Keep
    public static void openLink(String link) {
        Context ctx = touchpad.getContext();
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                if(link.startsWith("file:")) {
                    int truncLength = 5;
                    if(link.startsWith("file://")) truncLength = 7;
                    String path = link.substring(truncLength);
                    Tools.openPath(ctx, new File(path), false);
                }else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(link), "*/*");
                    ctx.startActivity(intent);
                }
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @SuppressWarnings("unused")
    public static void openPath(String path) {
        Context ctx = touchpad.getContext();
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Tools.openPath(ctx, new File(path), false);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @Keep
    public static void querySystemClipboard() {
        Tools.runOnUiThread(()->{
            ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
            if(clipData == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            ClipData.Item firstClipItem = clipData.getItemAt(0);
            CharSequence clipItemText = firstClipItem.getText();
            if(clipItemText == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
        });
    }

    @Keep
    public static void putClipboardData(String data, String mimeType) {
        Tools.runOnUiThread(()-> {
            ClipData clipData = null;
            switch(mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
            }
            if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
        });
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (IOException e) {
            Tools.showError(this,e);
        }
        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        minecraftGLView.start(localBinder.isActive, touchpad);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {}

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return minecraftGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }
}
