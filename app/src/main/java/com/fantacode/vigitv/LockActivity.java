package com.fantacode.vigitv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Remote-friendly pattern entry. Patterns never enter logs, intents, or saved state. */
public final class LockActivity extends Activity {
  final int BG = 0xff090b10, WHITE = 0xffedf2ff, MUTED = 0xff98a2b3, ACCENT = 0xffbbcbff;
  final Handler ui = new Handler(Looper.getMainLooper());
  TextView title, message, progress, submit;
  PatternBoard board;
  boolean changing, visible, busy, wasBlocked;
  int stage, generation;
  char[] first;
  // stage: 0 current pattern, 1 choose new, 2 confirm new
  final Runnable countdown =
      new Runnable() {
        public void run() {
          if (!visible) return;
          long wait = AppLock.waitMs(LockActivity.this);
          if (wait > 0) {
            wasBlocked = true;
            message.setText(
                "Too many attempts. Try again in " + ((wait + 999) / 1000) + " seconds.");
            submit.setEnabled(false);
          } else if (!busy) {
            submit.setEnabled(true);
            if (wasBlocked) {
              wasBlocked = false;
              message.setText(
                  changing
                      ? "Enter your current pattern to change it."
                      : "Your cameras are private. Enter your pattern to continue.");
            }
          }
          ui.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    changing = getIntent().getBooleanExtra("change", false);
    stage = AppLock.configured(this) ? 0 : 1;
    if (AppLock.allowed(this) && !changing) {
      openCameras();
      return;
    }
    LinearLayout root = new LinearLayout(this);
    root.setGravity(Gravity.CENTER);
    root.setBackgroundColor(BG);
    root.setPadding(dp(44), dp(24), dp(44), dp(24));
    setContentView(root);
    LinearLayout copy = new LinearLayout(this);
    copy.setOrientation(LinearLayout.VERTICAL);
    copy.setGravity(Gravity.CENTER_VERTICAL);
    copy.setPadding(0, 0, dp(52), 0);
    root.addView(copy, new LinearLayout.LayoutParams(dp(340), dp(330)));
    TextView brand = text("VIGI NVR TV  /  PRIVATE VIEWING", 11, MUTED);
    brand.setLetterSpacing(.15f);
    copy.addView(brand, new LinearLayout.LayoutParams(-1, dp(36)));
    title = text("", 30, WHITE);
    title.setTypeface(Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    copy.addView(title, new LinearLayout.LayoutParams(-1, dp(64)));
    message = text("", 15, MUTED);
    message.setSingleLine(false);
    message.setLineSpacing(dp(4), 1);
    copy.addView(message, new LinearLayout.LayoutParams(-1, dp(90)));
    TextView hint =
        text(
            "Arrows move · OK selects a dot\nMenu continues · Back clears\nUse 4–9 different dots.",
            12,
            MUTED);
    hint.setSingleLine(false);
    hint.setLineSpacing(dp(4), 1);
    copy.addView(hint, new LinearLayout.LayoutParams(-1, dp(64)));
    TextView recovery =
        button(
            "Forgot pattern?",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Reset app access")
                    .setMessage(
                        "To reset a forgotten pattern, clear Recorder TV storage in Android TV"
                            + " settings. This also removes the saved recorder sign-in and custom"
                            + " groups. You will need your recorder credentials again.")
                    .setPositiveButton("Close", null)
                    .show());
    copy.addView(recovery, new LinearLayout.LayoutParams(-2, dp(36)));
    LinearLayout entry = new LinearLayout(this);
    entry.setOrientation(LinearLayout.VERTICAL);
    entry.setGravity(Gravity.CENTER);
    root.addView(entry, new LinearLayout.LayoutParams(dp(286), -2));
    board = new PatternBoard();
    entry.addView(board, new LinearLayout.LayoutParams(dp(252), dp(252)));
    progress = text("No dots selected", 12, MUTED);
    progress.setGravity(Gravity.CENTER);
    entry.addView(progress, new LinearLayout.LayoutParams(-1, dp(34)));
    LinearLayout actions = new LinearLayout(this);
    actions.setGravity(Gravity.CENTER);
    entry.addView(actions, new LinearLayout.LayoutParams(-1, dp(44)));
    actions.addView(
        button(
            "Clear",
            () -> {
              board.clear();
              board.requestFocus();
            }),
        new LinearLayout.LayoutParams(dp(96), dp(38)));
    submit = button("", () -> submitPattern());
    LinearLayout.LayoutParams go = new LinearLayout.LayoutParams(dp(150), dp(38));
    go.leftMargin = dp(10);
    actions.addView(submit, go);
    refresh();
    board.requestFocus();
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setTypeface(Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  TextView button(String label, Runnable action) {
    TextView t = text(label, 13, WHITE);
    t.setPadding(dp(14), 0, dp(14), 0);
    t.setGravity(Gravity.CENTER);
    t.setFocusable(true);
    t.setFocusableInTouchMode(true);
    style(t, false);
    t.setOnFocusChangeListener((v, f) -> style(t, f));
    t.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    return t;
  }

  void style(TextView t, boolean focused) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(focused ? WHITE : 0xff181d27);
    bg.setCornerRadius(dp(6));
    t.setBackground(bg);
    t.setTextColor(focused ? BG : WHITE);
  }

  void wipeFirst() {
    if (first != null) {
      Arrays.fill(first, '\0');
      first = null;
    }
  }

  void refresh() {
    title.setText(
        stage == 0
            ? (changing ? "Verify your pattern" : "Unlock workspace")
            : stage == 1 ? "Create your pattern" : "Confirm your pattern");
    message.setText(
        stage == 0
            ? (changing
                ? "Enter your current pattern to change it."
                : "Your cameras are private. Enter your pattern to continue.")
            : stage == 1
                ? "First, protect this TV with a pattern. Next, connect your recorder."
                : "Enter the same pattern again to save it.");
    submit.setText(stage == 0 ? "Unlock" : stage == 1 ? "Continue" : "Save pattern");
    board.clear();
  }

  void submitPattern() {
    if (busy || AppLock.waitMs(this) > 0) return;
    char[] candidate = board.pattern();
    if (!PatternSecret.valid(candidate)) {
      Arrays.fill(candidate, '\0');
      message.setText("Select at least 4 different dots.");
      board.requestFocus();
      return;
    }
    if (stage == 1) {
      wipeFirst();
      first = candidate;
      stage = 2;
      refresh();
      board.requestFocus();
      return;
    }
    if (stage == 2 && !Arrays.equals(first, candidate)) {
      Arrays.fill(candidate, '\0');
      board.clear();
      message.setText("Those patterns do not match. Try the confirmation again.");
      board.requestFocus();
      return;
    }
    final int request = ++generation, action = stage;
    final long epoch = AppLock.epoch;
    busy = true;
    submit.setEnabled(false);
    message.setText(action == 0 ? "Checking pattern…" : "Saving your pattern…");
    new Thread(
            () -> {
              boolean success = false;
              try {
                if (action == 0) success = AppLock.verify(this, candidate);
                else {
                  AppLock.save(this, candidate);
                  success = true;
                }
              } catch (Exception ignored) {
              } finally {
                Arrays.fill(candidate, '\0');
              }
              final boolean accepted = success;
              ui.post(
                  () -> {
                    if (request != generation
                        || epoch != AppLock.epoch
                        || !visible
                        || isFinishing()) {
                      if (request == generation) {
                        busy = false;
                        submit.setEnabled(true);
                        board.clear();
                      }
                      return;
                    }
                    busy = false;
                    submit.setEnabled(true);
                    board.clear();
                    if (!accepted) {
                      message.setText(
                          AppLock.waitMs(this) > 0
                              ? "Too many attempts. Please wait 30 seconds."
                              : action == 0
                                  ? "Pattern not recognized. Try again."
                                  : "Could not save the pattern. Please try again.");
                      board.requestFocus();
                      return;
                    }
                    if (action == 0 && changing) {
                      AppLock.unlocked = true;
                      stage = 1;
                      refresh();
                      board.requestFocus();
                    } else {
                      wipeFirst();
                      AppLock.unlocked = true;
                      openCameras();
                    }
                  });
            },
            "vigi-pattern")
        .start();
  }

  void openCameras() {
    startActivity(
        new Intent(this, NativeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (board == null) return;
    visible = true;
    ui.post(countdown);
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getComponent() != null
        && getPackageName().equals(intent.getComponent().getPackageName()))
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    super.startActivity(intent);
  }

  @Override
  protected void onUserLeaveHint() {
    AppLock.lock();
    super.onUserLeaveHint();
  }

  @Override
  protected void onPause() {
    visible = false;
    generation++;
    busy = false;
    ui.removeCallbacksAndMessages(null);
    wipeFirst();
    if (board != null) {
      stage = AppLock.configured(this) ? 0 : 1;
      refresh();
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    wipeFirst();
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
      if (event.getAction() == KeyEvent.ACTION_UP && visible) submitPattern();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onBackPressed() {
    if (busy) return;
    if (board != null && !board.points.isEmpty()) {
      board.clear();
      board.requestFocus();
      return;
    }
    if (stage == 2) {
      wipeFirst();
      stage = 1;
      refresh();
      board.requestFocus();
    } else if (changing && AppLock.allowed(this)) finish();
    else moveTaskToBack(true);
  }

  final class PatternBoard extends View {
    final ArrayList<Integer> points = new ArrayList<>();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int cursor = 4;

    PatternBoard() {
      super(LockActivity.this);
      setFocusable(true);
      setFocusableInTouchMode(true);
      setContentDescription("Pattern grid. Center dot. Use arrows and OK.");
      setOnFocusChangeListener((v, f) -> invalidate());
    }

    char[] pattern() {
      char[] p = new char[points.size()];
      for (int i = 0; i < p.length; i++) p[i] = (char) ('0' + points.get(i));
      return p;
    }

    void clear() {
      points.clear();
      cursor = 4;
      if (progress != null) progress.setText("No dots selected");
      invalidate();
    }

    void select() {
      if (busy || AppLock.waitMs(LockActivity.this) > 0) return;
      if (points.contains(cursor)) {
        message.setText("That dot is already selected. Choose a different dot.");
        return;
      }
      points.add(cursor);
      progress.setText(points.size() + " dots selected");
      invalidate();
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
      if (busy) return true;
      int r = cursor / 3, c = cursor % 3;
      if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
        if (event.getRepeatCount() == 0) select();
        return true;
      }
      if (key == KeyEvent.KEYCODE_DPAD_DOWN && r == 2) return super.onKeyDown(key, event);
      if (key == KeyEvent.KEYCODE_DPAD_UP) r = Math.max(0, r - 1);
      else if (key == KeyEvent.KEYCODE_DPAD_DOWN) r = Math.min(2, r + 1);
      else if (key == KeyEvent.KEYCODE_DPAD_LEFT) c = Math.max(0, c - 1);
      else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) c = Math.min(2, c + 1);
      else return super.onKeyDown(key, event);
      cursor = r * 3 + c;
      setContentDescription(
          "Pattern grid. "
              + new String[] {
                    "Top left",
                    "Top center",
                    "Top right",
                    "Middle left",
                    "Center",
                    "Middle right",
                    "Bottom left",
                    "Bottom center",
                    "Bottom right"
                  }
                  [cursor]
              + " dot. "
              + points.size()
              + " dots selected.");
      invalidate();
      return true;
    }

    @Override
    public boolean onKeyUp(int key, KeyEvent e) {
      if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) return true;
      return super.onKeyUp(key, e);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_UP) {
        requestFocus();
        int c = Math.round((e.getX() - getWidth() / 6f) / (getWidth() / 3f)),
            r = Math.round((e.getY() - getHeight() / 6f) / (getHeight() / 3f));
        if (c >= 0 && c < 3 && r >= 0 && r < 3) {
          cursor = r * 3 + c;
          select();
        }
        return true;
      }
      return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w = getWidth() / 3f, h = getHeight() / 3f;
      boolean show = stage != 0;
      if (show && points.size() > 1) {
        paint.setColor(0xff657595);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
          int p = points.get(i);
          float x = (p % 3 + .5f) * w, y = (p / 3 + .5f) * h;
          if (i == 0) path.moveTo(x, y);
          else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
      }
      for (int p = 0; p < 9; p++) {
        float x = (p % 3 + .5f) * w, y = (p / 3 + .5f) * h;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(show && points.contains(p) ? ACCENT : 0xff485264);
        canvas.drawCircle(x, y, dp(5), paint);
        if (p == cursor && hasFocus()) {
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(dp(2));
          paint.setColor(WHITE);
          canvas.drawCircle(x, y, dp(19), paint);
        }
      }
    }
  }
}
