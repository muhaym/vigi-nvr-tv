package com.fantacode.vigitv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;

/** In-memory authorization, background locking, and a Keystore-backed verifier. */
public final class AppLock extends Application implements Application.ActivityLifecycleCallbacks {
  static volatile boolean unlocked = false;
  static volatile long epoch = 0;
  static final PermissionSession permission = new PermissionSession();
  static final Handler permissionHandler = new Handler(Looper.getMainLooper());
  int started = 0;

  @Override
  public void onCreate() {
    super.onCreate();
    registerActivityLifecycleCallbacks(this);
    IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    f.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    BroadcastReceiver r =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction()) || permission.owner() != null)
              lock();
          }
        };
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
    else registerLegacyReceiver(r, f);
  }

  // Receiver export flags are required on Android 13+. The guarded legacy call
  // serves API 26–32, whose registration overload has no NOT_EXPORTED flag.
  @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
  private void registerLegacyReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    registerReceiver(receiver, filter);
  }

  static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("app_lock", 0);
  }

  static boolean configured(Context c) {
    return prefs(c).contains("verifier") && prefs(c).contains("salt");
  }

  static boolean allowed(Context c) {
    return unlocked && configured(c);
  }

  static synchronized void lock() {
    unlocked = false;
    epoch++;
    permission.cancel();
    permissionHandler.removeCallbacksAndMessages(null);
  }

  static boolean permissionPending(Activity a) {
    return unlocked && permission.active(a, epoch, SystemClock.elapsedRealtime());
  }

  static void beginPermission(Activity a, int request) {
    if (!allowed(a)) return;
    permission.begin(a, request, epoch, SystemClock.elapsedRealtime());
    permissionHandler.removeCallbacksAndMessages(null);
    permissionHandler.postDelayed(
        new Runnable() {
          public void run() {
            if (permission.owner() != a) return;
            if (!permissionPending(a) || !permissionTaskVisible(a)) {
              lock();
              return;
            }
            permissionHandler.postDelayed(this, 100);
          }
        },
        100);
  }

  static void endPermission(Activity a, int request) {
    if (permission.finish(a, request, epoch, SystemClock.elapsedRealtime()))
      permissionHandler.removeCallbacksAndMessages(null);
  }

  static boolean permissionTaskVisible(Activity a) {
    if (Build.VERSION.SDK_INT < 34) return false;
    try {
      ActivityManager manager = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
      android.content.pm.ActivityInfo permissionUi =
          new Intent("android.content.pm.action.REQUEST_PERMISSIONS")
              .resolveActivityInfo(
                  a.getPackageManager(), android.content.pm.PackageManager.MATCH_SYSTEM_ONLY);
      String controller = permissionUi == null ? "" : permissionUi.packageName;
      for (ActivityManager.AppTask task : manager.getAppTasks()) {
        ActivityManager.RecentTaskInfo info = task.getTaskInfo();
        if (info.taskId == a.getTaskId()) {
          if (!info.isVisible() || info.topActivity == null) return false;
          String top = info.topActivity.getPackageName();
          return top.equals(a.getPackageName()) || top.equals(controller);
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  static boolean require(Activity a) {
    if (allowed(a)) return true;
    a.startActivity(new Intent(a, LockActivity.class));
    return false;
  }

  static void change(Activity a) {
    a.startActivity(new Intent(a, LockActivity.class).putExtra("change", true));
  }

  static byte[] seal(byte[] value) throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    String alias = "vigi-pattern-auth";
    if (!ks.containsAlias(alias)) {
      KeyGenerator g = KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
              .build());
      g.generateKey();
    }
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init((javax.crypto.SecretKey) ks.getKey(alias, null));
    return mac.doFinal(value);
  }

  static void save(Context c, char[] pattern) throws Exception {
    if (!PatternSecret.valid(pattern)) throw new IllegalArgumentException("Invalid pattern");
    byte[] salt = new byte[32];
    new SecureRandom().nextBytes(salt);
    byte[] derived = PatternSecret.derive(pattern, salt);
    try {
      byte[] verifier = seal(derived);
      if (!prefs(c)
          .edit()
          .putString("salt", Base64.encodeToString(salt, 2))
          .putString("verifier", Base64.encodeToString(verifier, 2))
          .putInt("attempts", 0)
          .putLong("blockedUntil", 0)
          .commit()) throw new IllegalStateException("Could not save lock");
    } finally {
      Arrays.fill(derived, (byte) 0);
    }
  }

  static synchronized boolean verify(Context c, char[] pattern) throws Exception {
    if (waitMs(c) > 0) return false;
    boolean match = false;
    if (PatternSecret.valid(pattern)) {
      byte[] salt = Base64.decode(prefs(c).getString("salt", ""), 2);
      byte[] derived = PatternSecret.derive(pattern, salt);
      try {
        match =
            PatternSecret.equal(
                seal(derived), Base64.decode(prefs(c).getString("verifier", ""), 2));
      } finally {
        Arrays.fill(derived, (byte) 0);
      }
    }
    int attempts = match ? 0 : prefs(c).getInt("attempts", 0) + 1;
    long blocked = 0;
    if (attempts >= 5) {
      attempts = 0;
      blocked = System.currentTimeMillis() + 30000;
    }
    if (!prefs(c).edit().putInt("attempts", attempts).putLong("blockedUntil", blocked).commit())
      throw new IllegalStateException("Could not record attempt");
    return match;
  }

  static long waitMs(Context c) {
    return Math.max(
        0, Math.min(30000, prefs(c).getLong("blockedUntil", 0) - System.currentTimeMillis()));
  }

  @Override
  public void onActivityStarted(Activity a) {
    started++;
  }

  @Override
  public void onActivityStopped(Activity a) {
    if (--started <= 0) {
      started = 0;
      if (!permissionPending(a) || !permissionTaskVisible(a)) lock();
    }
  }

  @Override
  public void onActivityCreated(Activity a, Bundle b) {}

  @Override
  public void onActivityResumed(Activity a) {}

  @Override
  public void onActivityPaused(Activity a) {}

  @Override
  public void onActivitySaveInstanceState(Activity a, Bundle b) {}

  @Override
  public void onActivityDestroyed(Activity a) {
    if (permission.owner() == a) lock();
  }
}
