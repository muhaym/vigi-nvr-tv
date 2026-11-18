package com.fantacode.vigitv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.common.BitMatrix;
import java.util.Arrays;

/** Local-only TOTP enrollment, private remote entry and one-use recovery. */
public final class AuthenticatorActivity extends Activity {
  private static final int LOGIN = 0, ENROLL = 1, BACKUP = 2, RECOVER = 3;
  private static final int BG = 0xff090b10, WHITE = 0xffedf2ff, MUTED = 0xff98a2b3;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final char[] digits = new char[6];
  private int count, screen;
  private boolean visible, manage, resumeReset;
  private long grantEpoch, grantAt, entryEpoch, verifiedStep = -1;
  private byte[] pending;
  private String recovery;
  private Bitmap qrBitmap;
  private LinearLayout root, left, right;
  private TextView message, slots, clock;
  private EditText recoveryInput;
  private ImageView qr;
  private Button manualKey;

  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (!visible) return;
          if ((screen == ENROLL || screen == BACKUP) && !grantValid()) {
            reset();
            message.setText("Setup expired. Please start again.");
          }
          if (clock != null) {
            long wait = TotpLock.waitMs(AuthenticatorActivity.this);
            clock.setText(
                wait > 0
                    ? "Try again in " + ((wait + 999) / 1000) + " seconds"
                    : slots != null ? "Codes refresh every 30 seconds" : "");
          }
          ui.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    manage = getIntent().getBooleanExtra("manage", false);
    boolean migration = getIntent().getBooleanExtra("setup", false) && AppLock.allowed(this);
    if (migration || !AppLock.configured(this)) beginEnrollment();
    else reset();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String label, int size, int color) {
    TextView view = new TextView(this);
    view.setText(label);
    view.setTextColor(color);
    view.setTextSize(size);
    view.setLineSpacing(dp(3), 1);
    view.setSaveEnabled(false);
    return view;
  }

  private void frame(String heading, String description) {
    entryEpoch = AppLock.epoch;
    clearEntry();
    if (qr != null) qr.setImageDrawable(null);
    if (qrBitmap != null) qrBitmap.recycle();
    qrBitmap = null;
    qr = null;
    manualKey = null;
    root = new LinearLayout(this);
    root.setGravity(Gravity.CENTER);
    root.setBackgroundColor(BG);
    root.setPadding(dp(36), dp(22), dp(36), dp(22));
    root.setSaveEnabled(false);
    root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    setContentView(root);
    left = new LinearLayout(this);
    left.setOrientation(LinearLayout.VERTICAL);
    left.setGravity(Gravity.CENTER_VERTICAL);
    left.setPadding(0, 0, dp(36), 0);
    root.addView(left, new LinearLayout.LayoutParams(dp(360), dp(400)));
    left.addView(text("VIGI NVR TV", 11, MUTED));
    TextView title = text(heading, 29, WHITE);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
    titleParams.topMargin = dp(18);
    titleParams.bottomMargin = dp(18);
    left.addView(title, titleParams);
    message = text(description, 15, MUTED);
    left.addView(message, new LinearLayout.LayoutParams(-1, dp(120)));
    clock = text("", 11, MUTED);
    left.addView(clock, new LinearLayout.LayoutParams(-1, dp(48)));
    right = new LinearLayout(this);
    right.setOrientation(LinearLayout.VERTICAL);
    right.setGravity(Gravity.CENTER);
    root.addView(right, new LinearLayout.LayoutParams(dp(330), dp(400)));
  }

  private Button button(String label, Runnable action) {
    Button button = new Button(this);
    button.setId(View.generateViewId());
    button.setText(label);
    button.setTextSize(13);
    button.setAllCaps(false);
    button.setIncludeFontPadding(false);
    button.setMinimumHeight(0);
    button.setMinHeight(0);
    button.setPadding(dp(12), 0, dp(12), 0);
    button.setFocusableInTouchMode(true);
    button.setStateListAnimator(null);
    style(button, false);
    button.setOnFocusChangeListener((v, focused) -> style(button, focused));
    button.setSaveEnabled(false);
    button.setOnClickListener(
        v -> {
          if (visible) action.run();
        });
    return button;
  }

  private void style(Button button, boolean focused) {
    GradientDrawable background = new GradientDrawable();
    background.setColor(focused ? WHITE : 0xff181d27);
    background.setCornerRadius(dp(7));
    background.setStroke(dp(1), focused ? WHITE : 0xff30394a);
    button.setBackground(background);
    button.setTextColor(focused ? BG : WHITE);
  }

  private void addButton(String label, Runnable action) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
    params.topMargin = dp(8);
    right.addView(button(label, action), params);
  }

  private void reset() {
    wipePending();
    if (!AppLock.configured(this)) {
      beginEnrollment();
      return;
    }
    if (!TotpLock.configured(this)) {
      startActivity(new Intent(this, LockActivity.class).putExtra("change", true));
      finish();
      return;
    }
    screen = LOGIN;
    frame(
        manage ? "Confirm access" : "Unlock cameras",
        manage
            ? "Enter a new code from your authenticator to replace this setup."
            : "Enter the six-digit code from your authenticator.");
    keypad();
    addButton("Use recovery code", this::showRecovery);
  }

  private void beginEnrollment() {
    wipePending();
    grantEpoch = AppLock.epoch;
    grantAt = SystemClock.elapsedRealtime();
    pending = TotpLock.random(20);
    recovery = TotpLock.newRecovery();
    screen = ENROLL;
    frame(
        "Add an authenticator",
        "Scan this QR code with your authenticator app, then enter its six-digit code.\n\n"
            + "Set up in private. Anyone who scans this code can unlock this TV.");
    try {
      String label = "TV " + Totp.base32(TotpLock.random(5));
      BitMatrix matrix = EnrollmentQr.create(pending, label);
      qrBitmap = Bitmap.createBitmap(488, 488, Bitmap.Config.RGB_565);
      int[] pixels = new int[488 * 488];
      for (int y = 0; y < 488; y++)
        for (int x = 0; x < 488; x++)
          pixels[y * 488 + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
      qrBitmap.setPixels(pixels, 0, 488, 0, 0, 488, 488);
      Arrays.fill(pixels, 0);
      qr = new ImageView(this);
      qr.setImageBitmap(qrBitmap);
      qr.setContentDescription("Private authenticator setup QR code");
      right.addView(qr, new LinearLayout.LayoutParams(dp(244), dp(244)));
      addButton("Enter code", this::enrollmentEntry);
      manualKey =
          button(
              "Show setup key",
              () -> {
                manualKey.setText(Totp.base32(pending));
                manualKey.setTextSize(11);
              });
      right.addView(manualKey, new LinearLayout.LayoutParams(-1, dp(46)));
      right.getChildAt(1).requestFocus();
    } catch (Exception ignored) {
      wipePending();
      message.setText("Couldn’t create the setup code. Close and try again.");
    }
  }

  private boolean grantValid() {
    return pending != null
        && grantEpoch == AppLock.epoch
        && SystemClock.elapsedRealtime() - grantAt < 300_000;
  }

  private void enrollmentEntry() {
    if (!grantValid()) {
      reset();
      return;
    }
    screen = ENROLL;
    frame("Check your code", "Enter the six-digit code from the account you just added.");
    keypad();
    addButton("Restart QR setup", this::showSameQr);
  }

  private void showSameQr() {
    // Restarting produces a fresh key; never retain a QR after leaving its screen.
    new AlertDialog.Builder(this)
        .setTitle("Restart setup?")
        .setMessage(
            "This creates a new setup key. Remove the unfinished account from your authenticator,"
                + " then scan the new QR code.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Restart",
            (d, w) -> {
              if (grantValid()) beginEnrollment();
              else reset();
            })
        .show();
  }

  private void keypad() {
    slots = text("○  ○  ○  ○  ○  ○", 25, WHITE);
    slots.setGravity(Gravity.CENTER);
    right.addView(slots, new LinearLayout.LayoutParams(-1, dp(64)));
    Button[] keys = new Button[12];
    String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "Delete", "0", "Unlock"};
    if (screen == ENROLL) labels[11] = "Verify";
    for (int row = 0; row < 4; row++) {
      LinearLayout line = new LinearLayout(this);
      right.addView(line, new LinearLayout.LayoutParams(-1, dp(48)));
      for (int col = 0; col < 3; col++) {
        final int index = row * 3 + col;
        keys[index] =
            button(
                labels[index],
                () -> {
                  if (index == 9) erase();
                  else if (index == 11) checkCode();
                  else append(index == 10 ? '0' : (char) ('1' + index));
                });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        line.addView(keys[index], params);
      }
    }
    for (int i = 0; i < 12; i++) {
      if (i % 3 > 0) keys[i].setNextFocusLeftId(keys[i - 1].getId());
      if (i % 3 < 2) keys[i].setNextFocusRightId(keys[i + 1].getId());
      keys[i].setNextFocusUpId(keys[Math.max(0, i - 3)].getId());
      if (i < 9) keys[i].setNextFocusDownId(keys[i + 3].getId());
    }
    keys[0].requestFocus();
  }

  private void append(char digit) {
    if (count == 6 || TotpLock.waitMs(this) > 0) return;
    digits[count++] = digit;
    updateSlots();
    if (count == 6) checkCode();
  }

  private void erase() {
    if (count > 0) digits[--count] = '\0';
    updateSlots();
  }

  private void updateSlots() {
    if (slots == null) return;
    StringBuilder masked = new StringBuilder();
    for (int i = 0; i < 6; i++) masked.append(i < count ? "●  " : "○  ");
    slots.setText(masked.toString().trim());
  }

  private void checkCode() {
    if (!visible || count != 6 || TotpLock.waitMs(this) > 0) return;
    if (entryEpoch != AppLock.epoch) {
      reset();
      return;
    }
    char[] candidate = Arrays.copyOf(digits, 6);
    clearDigits();
    try {
      boolean accepted;
      if (screen == ENROLL) {
        if (!grantValid()) {
          reset();
          return;
        }
        verifiedStep = Totp.match(pending, candidate, System.currentTimeMillis(), -1);
        accepted = verifiedStep >= 0;
        if (!TotpLock.result(this, accepted).commit()) throw new IllegalStateException();
      } else {
        Totp.Verdict verdict = TotpLock.check(this, candidate);
        if (verdict == Totp.Verdict.USED) {
          message.setText(
              "That code was already used. Enter the next code from your authenticator.");
          return;
        }
        accepted = verdict == Totp.Verdict.ACCEPTED;
      }
      if (!accepted) {
        message.setText(
            "Code not accepted. Try the next code.\n\n"
                + "If this keeps happening, check automatic time on your phone and TV.");
        return;
      }
      if (screen == ENROLL) showBackup();
      else if (manage) beginEnrollment();
      else openCameras();
    } catch (Exception ignored) {
      message.setText("Couldn’t check the code. Try again or use your recovery code.");
    } finally {
      Arrays.fill(candidate, '\0');
    }
  }

  private void showBackup() {
    screen = BACKUP;
    frame(
        "Save your recovery code",
        "Save this in your password manager. You’ll need it to add a new authenticator if you lose"
            + " your phone.\n\n"
            + "Shown only once.");
    StringBuilder grouped = new StringBuilder();
    for (int i = 0; i < recovery.length(); i += 4) {
      if (i > 0) grouped.append(i == 12 ? '\n' : ' ');
      grouped.append(recovery, i, i + 4);
    }
    TextView code = text(grouped.toString(), 24, WHITE);
    code.setTypeface(Typeface.MONOSPACE);
    code.setGravity(Gravity.CENTER);
    right.addView(code, new LinearLayout.LayoutParams(-1, dp(120)));
    addButton(
        "Saved · Enable authenticator",
        () -> {
          if (!grantValid()) {
            reset();
            return;
          }
          try {
            TotpLock.activate(this, pending, recovery, verifiedStep);
            wipePending();
            openCameras();
          } catch (Exception ignored) {
            message.setText("Couldn’t save setup. Please try again.");
          }
        });
    right.getChildAt(1).requestFocus();
  }

  private void showRecovery() {
    screen = RECOVER;
    frame(
        "Use recovery code",
        "Enter the recovery code you saved during setup. Then add a new authenticator.");
    recoveryInput = new EditText(this);
    recoveryInput.setSaveEnabled(false);
    recoveryInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    recoveryInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
    recoveryInput.setImeOptions(
        EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    recoveryInput.setTextColor(WHITE);
    recoveryInput.setSingleLine(true);
    recoveryInput.setContentDescription("Private recovery code");
    right.addView(recoveryInput, new LinearLayout.LayoutParams(-1, dp(56)));
    addButton("Replace authenticator", this::recover);
    addButton("Back to code", this::reset);
    recoveryInput.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_GO) {
            recover();
            return true;
          }
          return false;
        });
    recoveryInput.requestFocus();
  }

  private void recover() {
    if (!visible || recoveryInput == null || TotpLock.waitMs(this) > 0) return;
    if (entryEpoch != AppLock.epoch) {
      reset();
      return;
    }
    char[] candidate = new char[recoveryInput.length()];
    recoveryInput.getText().getChars(0, candidate.length, candidate, 0);
    recoveryInput.setText("");
    try {
      if (TotpLock.recover(this, candidate)) {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(recoveryInput.getWindowToken(), 0);
        manage = true;
        beginEnrollment();
      } else message.setText("Recovery code not accepted. Check it and try again.");
    } catch (Exception ignored) {
      message.setText("Couldn’t check the recovery code. Please try again.");
    } finally {
      Arrays.fill(candidate, '\0');
    }
  }

  private void clearDigits() {
    Arrays.fill(digits, '\0');
    count = 0;
    updateSlots();
  }

  private void clearEntry() {
    clearDigits();
    if (recoveryInput != null) recoveryInput.setText("");
    recoveryInput = null;
    slots = null;
  }

  private void wipePending() {
    if (pending != null) Arrays.fill(pending, (byte) 0);
    pending = null;
    recovery = null;
    verifiedStep = -1;
  }

  private void openCameras() {
    AppLock.unlocked = true;
    startActivity(
        new Intent(this, NativeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    visible = true;
    if (resumeReset) {
      resumeReset = false;
      reset();
    }
    ui.post(tick);
  }

  @Override
  protected void onPause() {
    visible = false;
    ui.removeCallbacksAndMessages(null);
    clearEntry();
    wipePending();
    if (qr != null) qr.setImageDrawable(null);
    if (qrBitmap != null) {
      qrBitmap.recycle();
      qrBitmap = null;
    }
    if (manualKey != null) manualKey.setText("");
    // Remove QR, manual key and recovery text even from the retained view hierarchy.
    if (root != null) root.removeAllViews();
    resumeReset = true;
    super.onPause();
  }

  @Override
  protected void onUserLeaveHint() {
    AppLock.lock();
    super.onUserLeaveHint();
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getComponent() != null
        && getPackageName().equals(intent.getComponent().getPackageName()))
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    super.startActivity(intent);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (screen != RECOVER
        && slots != null
        && event.getKeyCode() >= KeyEvent.KEYCODE_0
        && event.getKeyCode() <= KeyEvent.KEYCODE_9) {
      if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
        append((char) ('0' + event.getKeyCode() - KeyEvent.KEYCODE_0));
      return true;
    }
    if (screen != RECOVER && slots != null && event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) erase();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onBackPressed() {
    if (count > 0) {
      clearDigits();
      return;
    }
    if (screen == RECOVER) {
      reset();
      return;
    }
    AppLock.lock();
    wipePending();
    moveTaskToBack(true);
  }
}
