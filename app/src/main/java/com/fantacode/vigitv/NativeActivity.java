package com.fantacode.vigitv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.security.keystore.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import org.videolan.libvlc.*;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

/** Native grouped live and recorded viewing through the configured recorder. */
public final class NativeActivity extends Activity {
  final int BG = 0xff090b10, PANEL = 0xff181d27, ACCENT = 0xffbbcbff, MUTED = 0xff98a2b3;
  final Handler ui = new Handler(Looper.getMainLooper());
  final ArrayList<CameraGroup> groups = new ArrayList<>();
  final ArrayList<Feed> feeds = new ArrayList<>();
  RecorderProfile profile;
  String[] names;
  LibVLC engine;
  SharedPreferences prefs;
  FrameLayout root;
  LinearLayout bottomDock;
  TextView clock, pauseControl;
  String host = "", user = "", password = "";
  int groupIndex = 0, page = 0, pageSize = 2, channel = 1;
  boolean detail = false, resumed = false, audio = false, paused = false;
  long replayStart = 0, replayWindowStart = 0, replayWindowEnd = 0;
  TalkDialog talkDialog;
  AlarmDialog alarmDialog;
  RecorderSetup recorderSetup;
  int pendingTalkChannel = -1;
  NvrStreamCheck streamCheck;
  AlertDialog streamCheckDialog;
  android.app.Dialog replayPicker;
  LinearLayout replayTimeline;
  SeekBar scrubber;
  TextView scrubTime;
  boolean scrubbing = false;
  LinearLayout chromeTop, chromeBar;
  FrameLayout wallStage;
  OpticalZoomController optical;
  ZoomOverlay zoomOverlay;
  TextView zoomEntry;
  boolean zoomPanning;
  final DigitalViewport viewport = new DigitalViewport();
  TextView wallTitle, wallFooter;
  MosaicWall wall;
  boolean wallMode = false, returnFocusToTimeline = false;
  long lastInteraction = 0;
  int pendingPlayerStops = 0;
  final boolean compatibilityDrawing = "m7632".equalsIgnoreCase(android.os.Build.HARDWARE);
  final AutoCycle autoCycle = new AutoCycle();
  int cycleMode = 1;
  boolean overviewNoticeShown;
  TextView cycleControl;

  final Runnable tick =
      new Runnable() {
        public void run() {
          if (detail && replayStart > 0 && clock != null && !feeds.isEmpty()) {
            long position = replayPosition();
            clock.setText(paused ? "PAUSED" : "RECORDED");
            if (pauseControl != null) pauseControl.setText(paused ? "Play" : "Pause");
            if (!scrubbing && scrubber != null) {
              scrubber.setProgress(
                  (int)
                      Math.max(
                          0, Math.min(scrubber.getMax(), (position - replayWindowStart) / 1000)));
              showScrubTime(position, false);
            }
          }
          if (detail
              && zoomOverlay == null
              && (talkDialog == null || !talkDialog.isShowing())
              && (alarmDialog == null || !alarmDialog.isShowing())
              && !wallMode
              && !scrubbing
              && !paused
              && SystemClock.uptimeMillis() - lastInteraction > 7000) setWallMode(true);
          runAutoCycle();
          ui.postDelayed(this, 500);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    prefs = getSharedPreferences("native", 0);
    profile = ProfileStore.read(this);
    names = profile.names();
    loadGroups();
    loadAccount();
    pageSize = ViewLayout.normalize(prefs.getInt("layout", profile.combined ? 4 : 1));
    int seconds = prefs.getInt("auto_cycle_seconds", 0);
    autoCycle.configure(AutoCycle.supported(seconds) ? seconds : 0, SystemClock.uptimeMillis());
    cycleMode = prefs.getInt("auto_cycle_mode", 1);
    if (!profile.combined) pageSize = Math.min(4, pageSize);
    groupIndex = Math.max(0, Math.min(groups.size() - 1, getIntent().getIntExtra("unit", 0)));
    engine =
        new LibVLC(
            this,
            new ArrayList<>(
                Arrays.asList(
                    "--rtsp-tcp",
                    "--rtsp-frame-buffer-size=4194304",
                    "--network-caching=900",
                    "--no-video-title-show",
                    "--quiet",
                    "--verbose=-1")));
    render();
    root.setVisibility(View.INVISIBLE);
    ui.post(tick);
  }

  String cameraName(int id) {
    return id == 0 ? "Combined camera view" : profile.name(id);
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    return t;
  }

  GradientDrawable shape(boolean focused) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(focused ? 0xffedf2ff : Color.TRANSPARENT);
    d.setCornerRadius(dp(6));
    return d;
  }

  GradientDrawable fade(boolean top) {
    return new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        top ? new int[] {0xf5090b10, 0xdf090b10} : new int[] {0x99090b10, 0xf5090b10});
  }

  void focus(View v) {
    v.setFocusable(true);
    v.setFocusableInTouchMode(true);
    v.setBackground(shape(false));
    v.setOnFocusChangeListener(
        (a, b) -> {
          a.setBackground(shape(b));
          if (a instanceof TextView) ((TextView) a).setTextColor(b ? BG : 0xffd6dce8);
        });
  }

  LinearLayout row() {
    LinearLayout r = new LinearLayout(this);
    r.setGravity(Gravity.CENTER_VERTICAL);
    return r;
  }

  TextView add(LinearLayout row, String label, Runnable action) {
    TextView b = text(label, 13, 0xffd6dce8);
    b.setTypeface(Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(14), 0, dp(14), 0);
    focus(b);
    b.setIncludeFontPadding(false);
    if (row == chromeBar) {
      ControlTray.focus(b, false);
      b.setOnFocusChangeListener((v, f) -> ControlTray.focus(b, f));
    }
    b.setOnClickListener(
        v -> {
          lastInteraction = SystemClock.uptimeMillis();
          action.run();
        });
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(-2, dp(row == chromeBar ? ControlTray.BUTTON_DP : 34));
    p.setMargins(row.getChildCount() > 0 ? dp(8) : 0, 0, 0, 0);
    row.addView(b, p);
    return b;
  }

  void tab(LinearLayout row, String label, boolean selected, Runnable action) {
    TextView b = add(row, label, action);
    GradientDrawable selectedBg = new GradientDrawable();
    selectedBg.setColor(0xff252d3c);
    selectedBg.setCornerRadius(dp(6));
    b.setBackground(selected ? selectedBg : shape(false));
    b.setTextColor(selected ? Color.WHITE : MUTED);
    b.setOnFocusChangeListener(
        (v, f) -> {
          v.setBackground(f ? shape(true) : selected ? selectedBg : shape(false));
          b.setTextColor(f ? BG : selected ? Color.WHITE : MUTED);
        });
    b.setSelected(selected);
  }

  void spacer(LinearLayout row) {
    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
  }

  void more() {
    new AlertDialog.Builder(this)
        .setTitle("Workspace")
        .setItems(
            new String[] {
              "Arrange camera groups",
              "Camera layout",
              "Recorder connection",
              profile.overviewNeedsReview
                  ? "Overview settings · review needed"
                  : "Overview settings",
              "Lock now",
              "Change pattern",
              "Check native recorder connection",
              "Refresh camera list"
            },
            (d, w) -> {
              if (w == 0) groupMenu();
              else if (w == 1) layout();
              else if (w == 2) account();
              else if (w == 3) overviewSettings();
              else if (w == 4) {
                root.setVisibility(View.INVISIBLE);
                AppLock.lock();
                AppLock.require(this);
              } else if (w == 5) AppLock.change(this);
              else if (w == 6) checkNativeStream();
              else if (w == 7) refreshCameras();
            })
        .setNegativeButton("Close", null)
        .show();
  }

  void closeStreamCheck() {
    if (streamCheck != null) {
      streamCheck.close();
      streamCheck = null;
    }
    if (streamCheckDialog != null) {
      AlertDialog d = streamCheckDialog;
      streamCheckDialog = null;
      d.dismiss();
    }
  }

  void checkNativeStream() {
    if (!resumed || !AppLock.allowed(this)) return;
    closeStreamCheck();
    final NvrStreamCheck check = new NvrStreamCheck(this);
    streamCheck = check;
    final AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Recorder connection check")
            .setMessage(
                "Checking the secure recorder login. No microphone is opened and no sound is sent.")
            .setNegativeButton("Close", null)
            .create();
    streamCheckDialog = dialog;
    dialog.setOnDismissListener(
        d -> {
          check.close();
          if (streamCheck == check) streamCheck = null;
          if (streamCheckDialog == dialog) streamCheckDialog = null;
        });
    dialog.show();
    final String checkHost = host, checkUser = user, checkPassword = password;
    new Thread(
            () -> {
              String message;
              try {
                message = check.check(checkHost, checkUser, checkPassword);
              } catch (Exception error) {
                message =
                    "Connection check stopped during "
                        + check.stage
                        + " ("
                        + error.getClass().getSimpleName()
                        + "). No sound sent.";
              } finally {
                check.close();
              }
              final String result = message;
              ui.post(
                  () -> {
                    if (resumed
                        && AppLock.allowed(this)
                        && streamCheckDialog == dialog
                        && dialog.isShowing()) {
                      dialog.setMessage(result);
                      android.util.Log.i("VigiStreamCheck", result);
                    }
                  });
            },
            "vigi-native-check")
        .start();
  }

  void render() {
    closeZoom(false);
    closeTalk();
    closeAlarm();
    stop();
    if (wallStage != null)
      for (int i = wallStage.getChildCount() - 1; i >= 0; i--)
        if (wallStage.getChildAt(i) != wall) wallStage.removeViewAt(i);
    lastInteraction = SystemClock.uptimeMillis();
    if (root == null) {
      root = new FrameLayout(this);
      root.setBackgroundColor(BG);
      // Some MStar compositors display black video until a dialog changes layer blending.
      // Keep video inside an off-screen HWUI layer on the affected platform.

      wallStage = new FrameLayout(this);
      root.addView(wallStage, new FrameLayout.LayoutParams(-1, -1));
      chromeTop = row();
      chromeTop.setPadding(dp(20), dp(8), dp(20), dp(8));
      root.addView(chromeTop, new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP));
      bottomDock = new ControlTray(this);
      bottomDock.setOrientation(LinearLayout.VERTICAL);

      FrameLayout.LayoutParams dock = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
      dock.setMargins(dp(24), 0, dp(24), dp(18));
      root.addView(bottomDock, dock);
      chromeBar = row();
      bottomDock.addView(chromeBar, new LinearLayout.LayoutParams(-1, dp(ControlTray.BUTTON_DP)));
      replayTimeline = new LinearLayout(this);
      replayTimeline.setOrientation(LinearLayout.VERTICAL);
      replayTimeline.setPadding(dp(8), dp(6), dp(8), 0);
      bottomDock.addView(replayTimeline, new LinearLayout.LayoutParams(-1, dp(52)));
      wallFooter = text("", 10, MUTED);
      wallFooter.setPadding(dp(24), 0, dp(24), 0);
      root.addView(wallFooter, new FrameLayout.LayoutParams(-1, dp(26), Gravity.BOTTOM));
      setContentView(root);
      wall =
          new MosaicWall(
              this,
              engine,
              names,
              new MosaicWall.Listener() {
                public void select(int id) {
                  channel = id;
                  detail = true;
                  replayStart = 0;
                  wallMode = false;
                  render();
                }

                public void options(int id) {
                  cameraOptions(id);
                }
              });
      wallStage.addView(wall, new FrameLayout.LayoutParams(-1, -1));
      wall.mapping(profile.cells(), profile.columns, profile.rows);
    }
    if (compatibilityDrawing)
      root.setLayerType(
          !detail && profile.combined && pageSize > 4
              ? View.LAYER_TYPE_NONE
              : View.LAYER_TYPE_HARDWARE,
          null);
    scrubbing = false;
    scrubber = null;
    pauseControl = null;
    replayTimeline.removeAllViews();
    replayTimeline.setVisibility(View.GONE);
    cycleControl = null;
    chromeTop.removeAllViews();
    chromeBar.removeAllViews();
    FrameLayout.LayoutParams stage = (FrameLayout.LayoutParams) wallStage.getLayoutParams();
    stage.setMargins(
        detail ? 0 : dp(12), detail ? 0 : dp(64), detail ? 0 : dp(12), detail ? 0 : dp(26));
    wallStage.setLayoutParams(stage);
    chromeTop.setBackground(detail ? fade(true) : new android.graphics.drawable.ColorDrawable(BG));
    if (detail) {
      wall.suspendWall();
      wall.setVisibility(View.INVISIBLE);
      add(
          chromeTop,
          "← Cameras",
          () -> {
            detail = false;
            replayStart = 0;
            wallMode = false;
            render();
          });
      wallTitle = text(cameraName(channel), 20, Color.WHITE);
      wallTitle.setTypeface(Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
      wallTitle.setPadding(dp(14), 0, 0, 0);
      chromeTop.addView(wallTitle, new LinearLayout.LayoutParams(0, -1, 1));
      clock = text(replayStart > 0 ? "RECORDED" : "● LIVE", 10, replayStart > 0 ? MUTED : ACCENT);
      clock.setLetterSpacing(.12f);
      chromeTop.addView(clock, new LinearLayout.LayoutParams(-2, -1));
      if (replayStart > 0) {
        add(
            chromeBar,
            "Go live",
            () -> {
              replayStart = 0;
              render();
            });
        add(chromeBar, "Change time", () -> chooseReplay());
      } else add(chromeBar, "Playback", () -> chooseReplay());
      spacer(chromeBar);
      if (replayStart > 0) pauseControl = add(chromeBar, "Pause", () -> pause());
      TextView sound =
          add(
              chromeBar,
              audio ? "Sound on" : "Sound off",
              () -> {
                audio = !audio;
                for (Feed f : feeds) if (f.player != null) f.player.setVolume(audio ? 100 : 0);
              });
      sound.setOnClickListener(
          v -> {
            lastInteraction = SystemClock.uptimeMillis();
            audio = !audio;
            for (Feed f : feeds) if (f.player != null) f.player.setVolume(audio ? 100 : 0);
            sound.setText(audio ? "Sound on" : "Sound off");
          });
      if (replayStart == 0) add(chromeBar, "Talk", () -> showTalk());
      zoomEntry = add(chromeBar, "Zoom", () -> openZoom());
      if (replayStart == 0) add(chromeBar, "Alarm", () -> showAlarm());
      add(chromeBar, "Layout", () -> layout());
      add(chromeBar, "Hide controls", () -> setWallMode(true));
      if (replayStart > 0) buildTimeline();
      Feed f = new Feed(channel, true, replayStart);
      feeds.add(f);
      wallStage.addView(f.view, new FrameLayout.LayoutParams(-1, -1));
      if (resumed) start();
    } else {
      TextView brand = text("CAMERAS", 11, MUTED);
      brand.setLetterSpacing(.18f);
      chromeTop.addView(brand, new LinearLayout.LayoutParams(dp(112), -1));
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout tabs = row();
      for (int i = 0; i < groups.size(); i++) {
        final int index = i;
        tab(
            tabs,
            groups.get(i).name,
            i == groupIndex,
            () -> {
              groupIndex = index;
              page = 0;
              render();
            });
      }
      scroll.addView(tabs);
      chromeTop.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
      ArrayList<Integer> ids = groups.get(groupIndex).ids;
      page = Math.min(page, Math.max(0, (ids.size() - 1) / pageSize));
      int from = page * pageSize, to = Math.min(ids.size(), from + pageSize);
      wallTitle =
          text(
              (ids.isEmpty()
                      ? "No cameras"
                      : (from + 1 == to ? Integer.toString(to) : (from + 1) + "–" + to)
                          + " / "
                          + ids.size())
                  + "   ·   LIVE",
              10,
              MUTED);
      wallTitle.setLetterSpacing(.06f);
      chromeTop.addView(wallTitle, new LinearLayout.LayoutParams(-2, -1));
      add(chromeTop, "Layout " + pageSize, () -> layout());
      cycleControl = add(chromeTop, cycleLabel(), () -> cycleSettings());
      add(chromeTop, "•••", () -> more()).setContentDescription("Workspace settings");
      wallFooter.setText(
          "OK  Open camera     ·     Hold OK  Camera options" + "     ·     Menu  Hide header");
      if (ids.size() > pageSize) {
        add(
            chromeTop,
            "‹",
            () -> {
              page = Math.max(0, page - 1);
              render();
            });
        add(
            chromeTop,
            "›",
            () -> {
              page = Math.min((ids.size() - 1) / pageSize, page + 1);
              render();
            });
      }
      if (profile.combined && pageSize > 4) {
        wall.account(host, user, password);
        wall.setVisibility(View.VISIBLE);
        wall.cameras(new ArrayList<>(ids.subList(from, to)), pageSize);
        if (resumed) wall.resumeWall();
      } else {
        wall.suspendWall();
        wall.setVisibility(View.GONE);
        individualGrid(new ArrayList<>(ids.subList(from, to)));
      }
      if (profile.cameras.isEmpty()) {
        wall.suspendWall();
        wall.setVisibility(View.GONE);
        TextView empty = text("Connect a recorder to get started", 20, Color.WHITE);
        empty.setGravity(Gravity.CENTER);
        focus(empty);
        empty.setOnClickListener(v -> account());
        wallStage.addView(empty, new FrameLayout.LayoutParams(-1, -1));
      }
    }
    setWallMode(wallMode);
    if (replayStart == 0 && (detail || pageSize == 1) && !feeds.isEmpty())
      feeds.get(0).view.requestFocus();
    autoCycle.defer(SystemClock.uptimeMillis());
  }

  void cameraOptions(int id) {
    channel = id;
    new AlertDialog.Builder(this)
        .setTitle(cameraName(id))
        .setItems(
            new String[] {"Live full screen", "Recorded playback", "Choose office", "Edit groups"},
            (d, w) -> {
              if (w == 3) groupMenu();
              else if (w == 2) {
                String[] labels = new String[groups.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = groups.get(i).name;
                new AlertDialog.Builder(this)
                    .setTitle("Choose office")
                    .setItems(
                        labels,
                        (dd, index) -> {
                          groupIndex = index;
                          page = 0;
                          detail = false;
                          render();
                        })
                    .show();
              } else {
                detail = true;
                replayStart = 0;
                wallMode = false;
                render();
                if (w == 1) chooseReplay();
              }
            })
        .show();
  }

  void openZoom() {
    if (!detail || feeds.isEmpty() || !resumed || !AppLock.allowed(this) || zoomOverlay != null)
      return;
    closeTalk();
    closeAlarm();
    setWallMode(true);
    zoomOverlay =
        new ZoomOverlay(
            this,
            cameraName(channel),
            replayStart > 0,
            hasOpticalLens(channel),
            new ZoomOverlay.Listener() {
              public void zoom(int direction) {
                viewport.zoom(direction);
                applyDigitalZoom();
                updateZoom();
              }

              public void move() {
                if (viewport.scale() <= 1) viewport.zoom(1);
                zoomPanning = true;
                applyDigitalZoom();
                updateZoom();
                if (!feeds.isEmpty()) feeds.get(0).view.requestFocus();
              }

              public void reset() {
                viewport.reset();
                applyDigitalZoom();
                updateZoom();
              }

              public void done() {
                closeZoom(true);
              }

              public void controlsFocused() {
                zoomPanning = false;
                updateZoom();
              }

              public void lens(boolean zoomIn) {
                if (optical != null && replayStart == 0) optical.pulse(channel, zoomIn);
              }

              public void stopLens() {
                if (optical != null) optical.stop();
              }
            });
    int panelWidth = Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(48));
    FrameLayout.LayoutParams dock =
        new FrameLayout.LayoutParams(panelWidth, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    dock.bottomMargin = dp(24);
    root.addView(zoomOverlay, dock);
    updateZoom();
    zoomOverlay.focusControls();
    if (replayStart == 0 && hasOpticalLens(channel)) {
      if (optical == null)
        optical =
            new OpticalZoomController(
                this,
                host,
                user,
                password,
                () -> resumed && AppLock.allowed(this),
                (camera, supported, message) -> {
                  if (zoomOverlay != null && channel == camera && resumed && AppLock.allowed(this))
                    zoomOverlay.optical(supported, message);
                });
      optical.inspect(channel);
    }
  }

  boolean hasOpticalLens(int camera) {
    return profile.cameras.containsKey(camera);
  }

  void updateZoom() {
    if (zoomEntry != null)
      zoomEntry.setText(viewport.scale() > 1 ? "Zoom " + viewport.scale() + "x" : "Zoom");
    if (zoomOverlay != null) zoomOverlay.update(viewport.scale(), zoomPanning);
    lastInteraction = SystemClock.uptimeMillis();
  }

  void applyDigitalZoom() {
    for (Feed feed : feeds)
      if (feed.main) {
        int width = feed.video.getWidth(), height = feed.video.getHeight();
        viewport.size(width, height, feed.fittedWidth, feed.fittedHeight);
        feed.surface.setScaleX(viewport.scale());
        feed.surface.setScaleY(viewport.scale());
        feed.surface.setTranslationX(viewport.x);
        feed.surface.setTranslationY(viewport.y);
      }
    android.util.Log.i(
        "VigiZoom",
        "camera="
            + channel
            + " scale="
            + viewport.scale()
            + " offset="
            + viewport.x
            + ","
            + viewport.y);
  }

  void closeZoom(boolean restoreControls) {
    if (!restoreControls) {
      viewport.reset();
      applyDigitalZoom();
    }
    if (zoomOverlay == null) return;
    if (optical != null) optical.stop();
    ZoomOverlay overlay = zoomOverlay;
    zoomOverlay = null;
    zoomPanning = false;
    if (overlay.getParent() != null)
      ((android.view.ViewGroup) overlay.getParent()).removeView(overlay);
    if (restoreControls) {
      setWallMode(false);
      updateZoom();
      if (zoomEntry != null) zoomEntry.requestFocus();
    }
  }

  void chrome(View view, boolean show) {
    view.animate().cancel();
    if (show) {
      view.setVisibility(View.VISIBLE);
      view.animate().alpha(1f).setDuration(180).start();
    } else
      view.animate()
          .alpha(0f)
          .setDuration(180)
          .withEndAction(
              () -> {
                if (view.getAlpha() == 0f) view.setVisibility(View.GONE);
              })
          .start();
  }

  void setWallMode(boolean hide) {
    wallMode = hide;
    if (chromeTop == null) return;
    chrome(chromeTop, !hide);
    chrome(bottomDock, !hide && detail);
    chrome(wallFooter, !hide && !detail);
    if (!detail) {
      FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) wallStage.getLayoutParams();
      p.setMargins(hide ? 0 : dp(12), hide ? 0 : dp(64), hide ? 0 : dp(12), hide ? 0 : dp(26));
      wallStage.setLayoutParams(p);
    }
    if (hide && detail && !feeds.isEmpty()) feeds.get(0).view.requestFocus();
    lastInteraction = SystemClock.uptimeMillis();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (!AppLock.allowed(this)) return true;
    if (event.getAction() == KeyEvent.ACTION_DOWN) lastInteraction = SystemClock.uptimeMillis();
    int key = event.getKeyCode();
    if (zoomOverlay != null) {
      if (key == KeyEvent.KEYCODE_BACK) {
        if (event.getAction() == KeyEvent.ACTION_UP) closeZoom(true);
        return true;
      }
      if (key == KeyEvent.KEYCODE_MENU) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          zoomPanning = false;
          updateZoom();
          zoomOverlay.focusControls();
        }
        return true;
      }
      if (zoomPanning) {
        int
            dx =
                key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0,
            dy = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            viewport.pan(dx, dy);
            applyDigitalZoom();
            updateZoom();
          }
          return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
          if (event.getAction() == KeyEvent.ACTION_UP) {
            zoomPanning = false;
            updateZoom();
            zoomOverlay.focusControls();
          }
          return true;
        }
      }
      if (zoomOverlay.navigate(event)) return true;
      return super.dispatchKeyEvent(event);
    }
    boolean videoFocused = !feeds.isEmpty() && getCurrentFocus() == feeds.get(0).view;
    if (videoFocused
        && (detail || pageSize == 1)
        && getWindow().getDecorView().hasWindowFocus()
        && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        setWallMode(false);
        if (detail)
          (key == KeyEvent.KEYCODE_DPAD_UP ? chromeTop.getChildAt(0) : chromeBar.getChildAt(0))
              .requestFocus();
        else chromeTop.getChildAt(1).requestFocus();
      }
      return true;
    }
    if (replayStart == 0
        && (detail || pageSize == 1)
        && (wallMode || videoFocused)
        && getWindow().getDecorView().hasWindowFocus()
        && (talkDialog == null || !talkDialog.isShowing())
        && (alarmDialog == null || !alarmDialog.isShowing())
        && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
        switchCamera(key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
      return true;
    }
    if (key == KeyEvent.KEYCODE_MENU) {
      if (event.getAction() == KeyEvent.ACTION_UP) {
        setWallMode(!wallMode);
        if (!wallMode) {
          if (detail && scrubber != null) scrubber.requestFocus();
          else if (detail && chromeBar.getChildCount() > 0) chromeBar.getChildAt(0).requestFocus();
          else chromeTop.getChildAt(1).requestFocus();
        }
      }
      return true;
    }
    if (wallMode
        && event.getAction() == KeyEvent.ACTION_DOWN
        && (key == KeyEvent.KEYCODE_DPAD_UP
            || (detail
                && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_CENTER)))) {
      if (detail || (getCurrentFocus() != null && getCurrentFocus().getTop() < dp(20))) {
        setWallMode(false);
        if (detail && scrubber != null) scrubber.requestFocus();
        else if (detail) chromeBar.getChildAt(0).requestFocus();
        else chromeTop.getChildAt(1).requestFocus();
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }

  void switchCamera(int direction) {
    ArrayList<Integer> ids = groups.get(groupIndex).ids;
    int current = detail ? channel : ids.isEmpty() ? 0 : ids.get(Math.min(page, ids.size() - 1));
    int next = CameraNavigation.adjacent(ids, current, direction);
    if (next == 0 || next == current) return;
    if (detail) {
      channel = next;
      page = ids.indexOf(next) / pageSize;
    } else page = ids.indexOf(next);
    render();
  }

  void start() {
    for (int i = 0; i < feeds.size(); i++) {
      Feed f = feeds.get(i);
      ui.postDelayed(
          () -> {
            if (resumed && feeds.contains(f)) f.start();
          },
          i * 180L);
    }
  }

  void stop() {
    closeZoom(false);
    closeTalk();
    closeAlarm();
    for (Feed f : feeds) {
      f.release();
      if (f.view.getParent() instanceof android.view.ViewGroup)
        ((android.view.ViewGroup) f.view.getParent()).removeView(f.view);
    }
    feeds.clear();
    paused = false;
  }

  void layout() {
    String[] labels = {
      "1 camera · full quality", "4 cameras · 2 × 2", "8 cameras · 4 × 2", "16 cameras · 4 × 4"
    };
    int selected = 0;
    for (int i = 0; i < ViewLayout.SIZES.length; i++)
      if (pageSize == ViewLayout.SIZES[i]) selected = i;
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Live camera layout")
            .setSingleChoiceItems(
                labels,
                selected,
                (d, which) -> {
                  int size = ViewLayout.SIZES[which];
                  d.dismiss();
                  if (size > 4 && !profile.combined) overviewSettings(size);
                  else selectLayout(size);
                })
            .setNegativeButton("Cancel", null)
            .create();
    dialog.show();
  }

  void selectLayout(int size) {
    pageSize = size;
    page = 0;
    detail = false;
    replayStart = 0;
    wallMode = false;
    cycleMode = size == 1 ? 1 : 2;
    prefs.edit().putInt("layout", size).putInt("auto_cycle_mode", cycleMode).apply();
    render();
  }

  int pageCount() {
    return groups.isEmpty() ? 0 : ViewLayout.pages(groups.get(groupIndex).ids.size(), pageSize);
  }

  String cycleLabel() {
    return autoCycle.seconds == 0
        ? "Cycle off"
        : pageCount() < 2 ? "Cycle paused" : "Cycle " + autoCycle.seconds + "s";
  }

  void cycleSettings() {
    new CycleSettingsDialog(
            this,
            groups.get(groupIndex).name,
            groups.get(groupIndex).ids.size(),
            pageCount(),
            autoCycle.seconds,
            cycleMode,
            (seconds, cameras) -> {
              cycleMode = cameras ? 1 : 2;
              autoCycle.configure(seconds, SystemClock.uptimeMillis());
              if (seconds > 0 && cameras) {
                pageSize = 1;
                page = 0;
                detail = false;
                replayStart = 0;
                wallMode = false;
              }
              prefs
                  .edit()
                  .putInt("auto_cycle_seconds", seconds)
                  .putInt("auto_cycle_mode", cycleMode)
                  .putInt("layout", pageSize)
                  .apply();
              render();
            })
        .show();
  }

  void runAutoCycle() {
    boolean eligible =
        resumed
            && AppLock.allowed(this)
            && !detail
            && zoomOverlay == null
            && (talkDialog == null || !talkDialog.isShowing())
            && (alarmDialog == null || !alarmDialog.isShowing())
            && !scrubbing
            && (replayPicker == null || !replayPicker.isShowing())
            && getWindow().getDecorView().hasWindowFocus()
            && pendingPlayerStops == 0;
    if (cycleControl != null) {
      String label = cycleLabel();
      if (!label.contentEquals(cycleControl.getText())) cycleControl.setText(label);
    }
    if (autoCycle.ready(SystemClock.uptimeMillis(), eligible, pageCount())) {
      page = (page + 1) % pageCount();
      render();
    }
  }

  @Override
  public void onUserInteraction() {
    super.onUserInteraction();
    long now = SystemClock.uptimeMillis();
    lastInteraction = now;
    autoCycle.defer(now);
  }

  void groupMenu() {
    String[] labels = new String[groups.size() + 1];
    for (int i = 0; i < groups.size(); i++) labels[i] = "Edit " + groups.get(i).name;
    labels[groups.size()] = "Create group";
    new AlertDialog.Builder(this)
        .setTitle("Arrange your units and cameras")
        .setItems(labels, (d, w) -> editGroup(w < groups.size() ? w : -1))
        .setNegativeButton("Close", null)
        .show();
  }

  void editGroup(int index) {
    CameraGroup group = index < 0 ? new CameraGroup("New group") : groups.get(index);
    EditText name = new EditText(this);
    name.setSingleLine();
    name.setText(group.name);
    new AlertDialog.Builder(this)
        .setTitle("CameraGroup name")
        .setView(name)
        .setPositiveButton(
            "Choose cameras",
            (d, w) -> {
              ArrayList<Integer> ids = new ArrayList<>(profile.cameras.keySet());
              String[] labels = new String[ids.size()];
              boolean[] selected = new boolean[ids.size()];
              for (int i = 0; i < ids.size(); i++) {
                labels[i] = profile.name(ids.get(i));
                selected[i] = group.ids.contains(ids.get(i));
              }
              AlertDialog.Builder picker =
                  new AlertDialog.Builder(this)
                      .setTitle("Cameras in " + name.getText())
                      .setMultiChoiceItems(
                          labels, selected, (dd, i, checked) -> selected[i] = checked)
                      .setPositiveButton(
                          "Save group",
                          (dd, ww) -> {
                            group.name = name.getText().toString().trim();
                            if (group.name.isEmpty()) group.name = "CameraGroup";
                            group.ids.clear();
                            for (int i = 0; i < ids.size(); i++)
                              if (selected[i]) group.ids.add(ids.get(i));
                            if (index < 0) groups.add(group);
                            saveGroups();
                            page = 0;
                            render();
                          })
                      .setNegativeButton("Cancel", null);
              if (index >= 0 && groups.size() > 1)
                picker.setNeutralButton(
                    "Delete group",
                    (dd, ww) -> {
                      groups.remove(index);
                      groupIndex = 0;
                      page = 0;
                      saveGroups();
                      render();
                    });
              picker.show();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void loadGroups() {
    groups.clear();
    groups.addAll(
        CameraGroup.load(
            prefs.getString("groups_" + profile.host, "[]"), profile.cameras.keySet()));
  }

  void saveGroups() {
    try {
      prefs.edit().putString("groups_" + profile.host, CameraGroup.save(groups)).apply();
    } catch (JSONException error) {
      toast("Could not save groups");
    }
  }

  EditText field(String hint, String value, boolean secret) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setText(value);
    e.setSingleLine(true);
    e.setTextColor(-1);
    e.setHintTextColor(0xffaaaaaa);
    e.setInputType(secret ? 129 : 1);
    return e;
  }

  void account() {
    if (recorderSetup != null && recorderSetup.dialog.isShowing()) return;
    recorderSetup =
        new RecorderSetup(
            this,
            profile,
            user,
            (connected, username, secret) -> {
              RecorderProfile oldProfile = profile;
              String oldHost = host, oldUser = user, oldPassword = password;
              profile = connected;
              host = profile.host;
              user = username;
              password = secret;
              if (!saveAccount()) {
                profile = oldProfile;
                host = oldHost;
                user = oldUser;
                password = oldPassword;
                return false;
              }
              if (optical != null) {
                optical.close();
                optical = null;
              }
              names = profile.names();
              groups.clear();
              loadGroups();
              groupIndex = 0;
              page = 0;
              detail = false;
              if (!profile.combined) pageSize = Math.min(4, pageSize);
              wall.names = names;
              wall.mapping(profile.cells(), profile.columns, profile.rows);
              ui.post(
                  () -> {
                    if (resumed && AppLock.allowed(this)) render();
                  });
              return true;
            });
  }

  void refreshCameras() {
    if (user.isEmpty() || profile.host.isEmpty() || !profile.host.equals(host)) {
      account();
      return;
    }
    final RecorderProfile source = profile;
    final String username = user, secret = password;
    closeTalk();
    closeAlarm();
    AlertDialog progress =
        new AlertDialog.Builder(this)
            .setTitle("Refresh camera list")
            .setMessage("Finding cameras through your recorder…")
            .setNegativeButton("Cancel", null)
            .create();
    progress.show();
    new Thread(
            () -> {
              try (NvrWebControl api =
                  new NvrWebControl(
                      source.host,
                      source.ports.https,
                      username,
                      secret,
                      ProfileStore.certificate(this, source.host))) {
                api.login();
                RecorderProfile updated = RecorderProfile.parse(source.json());
                updated.cameras.clear();
                updated.cameras.putAll(CameraDiscovery.parse(api.cameraList()));
                if (updated.cameras.isEmpty())
                  throw new java.io.IOException("Recorder returned no cameras");
                updated.inheritOverview(source);
                ui.post(
                    () -> {
                      if (!progress.isShowing()) return;
                      progress.dismiss();
                      if (!resumed || !AppLock.allowed(this) || profile != source) return;
                      try {
                        ProfileStore.save(this, updated);
                        profile = updated;
                        names = profile.names();
                        loadGroups();
                        groupIndex = Math.min(groupIndex, groups.size() - 1);
                        page = 0;
                        detail = false;
                        replayStart = 0;
                        if (!profile.combined) pageSize = Math.min(4, pageSize);
                        wall.names = names;
                        wall.mapping(profile.cells(), profile.columns, profile.rows);
                        render();
                        toast(
                            "Found "
                                + profile.cameras.size()
                                + " cameras"
                                + (profile.overviewNeedsReview
                                    ? ". Review overview positions for 8 / 16 layouts."
                                    : ""));
                      } catch (Exception failed) {
                        toast("Could not save the camera list. Your previous setup was preserved.");
                      }
                    });
              } catch (Exception failed) {
                ui.post(
                    () -> {
                      if (!progress.isShowing()) return;
                      progress.dismiss();
                      if (resumed && AppLock.allowed(this))
                        toast(
                            "Could not refresh cameras. Check recorder connection and account"
                                + " permissions; your saved list was preserved.");
                    });
              }
            },
            "recorder-camera-refresh")
        .start();
  }

  void individualGrid(ArrayList<Integer> ids) {
    int columns = ViewLayout.columns(pageSize), rows = ViewLayout.rows(pageSize);
    LinearLayout grid = new LinearLayout(this);
    grid.setOrientation(LinearLayout.VERTICAL);
    wallStage.addView(grid, new FrameLayout.LayoutParams(-1, -1));
    for (int row = 0; row < rows; row++) {
      LinearLayout line = row();
      grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int column = 0; column < columns; column++) {
        int index = row * columns + column;
        if (index >= ids.size()) {
          FrameLayout empty = new FrameLayout(this);
          empty.setBackgroundColor(0xff0c1017);
          LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, -1, 1);
          gap.setMargins(dp(3), dp(3), dp(3), dp(3));
          line.addView(empty, gap);
          continue;
        }
        int id = ids.get(index);
        Feed feed = new Feed(id, pageSize == 1, 0);
        feeds.add(feed);
        LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(0, -1, 1);
        tile.setMargins(dp(3), dp(3), dp(3), dp(3));
        line.addView(feed.view, tile);
        feed.view.setContentDescription(cameraName(id));
        feed.view.setOnFocusChangeListener(
            (v, focused) -> {
              GradientDrawable border = new GradientDrawable();
              border.setColor(Color.TRANSPARENT);
              border.setStroke(dp(focused ? 2 : 1), focused ? 0xffe4ebff : 0xff202836);
              v.setForeground(border);
            });
        feed.view.setOnClickListener(
            v -> {
              channel = id;
              detail = true;
              wallMode = false;
              render();
            });
      }
    }
    if (resumed) start();
  }

  void overviewSettings() {
    overviewSettings(0);
  }

  void overviewSettings(int requestedSize) {
    OverviewSettings.show(
        this,
        profile,
        requestedSize > 4,
        updated -> {
          try {
            ProfileStore.save(this, updated);
          } catch (Exception error) {
            toast("Could not save overview settings");
            return false;
          }
          profile = updated;
          wall.mapping(profile.cells(), profile.columns, profile.rows);
          ui.post(
              () -> {
                if (requestedSize > 0) selectLayout(requestedSize);
                else {
                  if (!profile.combined) pageSize = Math.min(4, pageSize);
                  render();
                }
              });
          return true;
        });
  }

  javax.crypto.SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("vigi-tv-account")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "vigi-tv-account", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) ks.getKey("vigi-tv-account", null);
  }

  boolean saveAccount() {
    try {
      profile.repairOverview();
      RecorderProfile.parse(profile.json());
      String data =
          new JSONObject().put("host", host).put("user", user).put("password", password).toString();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(1, key());
      String value = Base64.encodeToString(c.doFinal(data.getBytes(StandardCharsets.UTF_8)), 2);
      return prefs
          .edit()
          .putString("account", value)
          .putString("recorder_profile", profile.json().toString())
          .putString("iv", Base64.encodeToString(c.getIV(), 2))
          .commit();
    } catch (Exception ignored) {
      return false;
    }
  }

  void loadAccount() {
    try {
      String value = prefs.getString("account", "");
      if (value.isEmpty()) return;
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(2, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), 2)));
      JSONObject o =
          new JSONObject(new String(c.doFinal(Base64.decode(value, 2)), StandardCharsets.UTF_8));
      host = o.getString("host");
      user = o.getString("user");
      password = o.getString("password");
    } catch (Exception ignored) {
      user = "";
      password = "";
    }
  }

  void chooseReplay() {
    if (replayPicker != null && replayPicker.isShowing()) return;
    Calendar c = Calendar.getInstance();
    if (replayStart > 0) c.setTimeInMillis(replayPosition());
    else c.add(Calendar.MINUTE, -10);
    DatePickerDialog date =
        new DatePickerDialog(
            this,
            (d, y, m, day) -> {
              c.set(y, m, day);
              TimePickerDialog time =
                  new TimePickerDialog(
                      this,
                      (t, h, min) -> {
                        c.set(Calendar.HOUR_OF_DAY, h);
                        c.set(Calendar.MINUTE, min);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        if (c.getTimeInMillis() > System.currentTimeMillis()) {
                          toast("Choose a time in the past");
                          return;
                        }
                        replayStart = c.getTimeInMillis();
                        resetReplayWindow(replayStart);
                        detail = true;
                        wallMode = false;
                        render();
                        returnFocusToTimeline = true;
                      },
                      c.get(Calendar.HOUR_OF_DAY),
                      c.get(Calendar.MINUTE),
                      true);
              replayPicker = time;
              time.setOnDismissListener(
                  dismissed -> {
                    if (replayPicker == time) replayPicker = null;
                    if (detail && replayStart > 0)
                      ui.postDelayed(
                          () -> {
                            if (scrubber != null && !wallMode) scrubber.requestFocus();
                          },
                          200);
                  });
              time.show();
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH));
    replayPicker = date;
    date.setOnDismissListener(
        dismissed -> {
          if (replayPicker == date) replayPicker = null;
        });
    date.show();
  }

  String local(long ms) {
    SimpleDateFormat f = new SimpleDateFormat("dd MMM HH:mm:ss", Locale.UK);
    f.setTimeZone(TimeZone.getDefault());
    return f.format(new Date(ms))
        + " "
        + TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT);
  }

  long replayPosition() {
    return replayStart + (feeds.isEmpty() ? 0 : feeds.get(0).elapsed());
  }

  void resetReplayWindow(long start) {
    replayWindowStart = start;
    replayWindowEnd =
        Math.max(start + 1000, Math.min(System.currentTimeMillis() - 1000, start + 1800000L));
  }

  void showScrubTime(long position, boolean preview) {
    if (scrubTime == null) return;
    scrubTime.setText(
        (preview ? "Seek to  " : "")
            + local(position)
            + (preview ? "   ·   OK to play   /   Back to cancel" : ""));
    if (scrubber != null) scrubber.setContentDescription("Playback timeline, " + local(position));
  }

  void buildTimeline() {
    if (replayWindowStart <= 0 || replayStart < replayWindowStart || replayStart > replayWindowEnd)
      resetReplayWindow(replayStart);
    replayTimeline.setVisibility(View.VISIBLE);
    LinearLayout times = row();
    TextView from = text(shortTime(replayWindowStart), 11, MUTED),
        to = text(shortTime(replayWindowEnd), 11, MUTED);
    to.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    times.addView(from, new LinearLayout.LayoutParams(dp(60), -1));
    scrubTime = text("", 12, Color.WHITE);
    scrubTime.setGravity(Gravity.CENTER);
    times.addView(scrubTime, new LinearLayout.LayoutParams(0, -1, 1));
    times.addView(to, new LinearLayout.LayoutParams(dp(60), -1));
    replayTimeline.addView(times, new LinearLayout.LayoutParams(-1, dp(22)));
    scrubber = new SeekBar(this);
    scrubber.setMax((int) ((replayWindowEnd - replayWindowStart) / 1000));
    scrubber.setKeyProgressIncrement(10);
    scrubber.setProgress((int) ((replayStart - replayWindowStart) / 1000));
    scrubber.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    scrubber.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    scrubber.setFocusable(true);
    scrubber.setFocusableInTouchMode(true);
    replayTimeline.addView(scrubber, new LinearLayout.LayoutParams(-1, dp(24)));
    styleTimeline(scrubber, false);
    scrubber.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (fromUser) {
              scrubbing = true;
              showScrubTime(replayWindowStart + progress * 1000L, true);
            }
          }

          public void onStartTrackingTouch(SeekBar bar) {
            scrubbing = true;
          }

          public void onStopTrackingTouch(SeekBar bar) {
            commitScrub();
          }
        });
    scrubber.setOnKeyListener(
        (v, key, e) -> {
          if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
              int step = e.getRepeatCount() > 12 ? 60 : 10;
              int progress =
                  Math.max(
                      0,
                      Math.min(
                          scrubber.getMax(),
                          scrubber.getProgress()
                              + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -step : step)));
              scrubber.setProgress(progress);
              scrubbing = true;
              showScrubTime(replayWindowStart + progress * 1000L, true);
            }
            return true;
          }
          if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP && scrubbing) commitScrub();
            return true;
          }
          if (key == KeyEvent.KEYCODE_BACK && scrubbing) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
              scrubbing = false;
              showScrubTime(replayPosition(), false);
            }
            return true;
          }
          return false;
        });
    scrubber.setOnFocusChangeListener(
        (v, focused) -> {
          styleTimeline((SeekBar) v, focused);
          if (!focused) scrubbing = false;
        });
    showScrubTime(replayStart, false);
  }

  String shortTime(long ms) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.UK);
    f.setTimeZone(TimeZone.getDefault());
    return f.format(new Date(ms));
  }

  void styleTimeline(SeekBar bar, boolean focused) {
    GradientDrawable base = new GradientDrawable();
    base.setColor(0xff45505f);
    base.setCornerRadius(dp(2));
    GradientDrawable fill = new GradientDrawable();
    fill.setColor(focused ? 0xffdbe5ff : 0xff9aaccd);
    fill.setCornerRadius(dp(2));
    ClipDrawable clip = new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
    LayerDrawable track = new LayerDrawable(new android.graphics.drawable.Drawable[] {base, clip});
    track.setId(0, android.R.id.background);
    track.setId(1, android.R.id.progress);
    bar.setProgressDrawable(track);
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      bar.setMinHeight(dp(3));
      bar.setMaxHeight(dp(3));
    }
    GradientDrawable thumb = new GradientDrawable();
    thumb.setShape(GradientDrawable.OVAL);
    thumb.setColor(focused ? Color.WHITE : 0xffc0cbdf);
    thumb.setSize(dp(focused ? 12 : 7), dp(focused ? 12 : 7));
    bar.setThumb(thumb);
    bar.setThumbOffset(dp(focused ? 6 : 3.5f));
    bar.setBackgroundColor(Color.TRANSPARENT);
  }

  void commitScrub() {
    if (scrubber == null) return;
    long target = replayWindowStart + scrubber.getProgress() * 1000L;
    scrubbing = false;
    seekTo(target, true);
  }

  void seekTo(long target, boolean focusTimeline) {
    replayStart = Math.max(1, Math.min(System.currentTimeMillis() - 1000, target));
    if (replayStart < replayWindowStart || replayStart > replayWindowEnd)
      resetReplayWindow(replayStart);
    render();
    if (focusTimeline && scrubber != null) scrubber.requestFocus();
  }

  void seek(long delta) {
    seekTo(replayPosition() + delta, false);
  }

  void pause() {
    lastInteraction = SystemClock.uptimeMillis();
    if (feeds.isEmpty() || feeds.get(0).player == null) return;
    MediaPlayer p = feeds.get(0).player;
    if (paused) {
      p.play();
      paused = false;
    } else {
      p.pause();
      paused = true;
    }
    if (pauseControl != null) pauseControl.setText(paused ? "Play" : "Pause");
  }

  void closeAlarm() {
    if (alarmDialog != null) {
      AlarmDialog dialog = alarmDialog;
      alarmDialog = null;
      dialog.dismiss();
    }
  }

  void showAlarm() {
    if (!resumed || !AppLock.allowed(this) || !detail || replayStart != 0) return;
    closeTalk();
    closeZoom(true);
    closeAlarm();
    alarmDialog =
        new AlarmDialog(
            this,
            cameraName(channel),
            host,
            user,
            password,
            channel,
            () -> resumed && AppLock.allowed(this));
    alarmDialog.show();
  }

  void closeTalk() {
    if (talkDialog != null) {
      TalkDialog dialog = talkDialog;
      talkDialog = null;
      dialog.dismiss();
    }
  }

  void showTalk() {
    if (!resumed || !AppLock.allowed(this) || !detail || replayStart != 0) return;
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      pendingTalkChannel = channel;
      AppLock.beginPermission(this, 91);
      requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, 91);
      return;
    }
    if (talkDialog != null && talkDialog.isShowing()) return;
    talkDialog =
        new TalkDialog(
            this,
            cameraName(channel),
            host,
            user,
            password,
            channel,
            audio,
            () -> resumed && AppLock.allowed(this),
            volume -> {
              for (Feed feed : feeds) if (feed.player != null) feed.player.setVolume(volume);
            });
    talkDialog.show();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    AppLock.endPermission(this, request);
    if (request == 91) {
      int target = pendingTalkChannel;
      pendingTalkChannel = -1;
      if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
        ui.postDelayed(
            () -> {
              if (resumed && channel == target && AppLock.allowed(this)) showTalk();
            },
            300);
      else toast("Allow microphone access to use Talk or the local mic test.");
    }
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onBackPressed() {
    if (zoomOverlay != null) {
      closeZoom(true);
      return;
    }
    if (detail && viewport.scale() > 1) {
      viewport.reset();
      applyDigitalZoom();
      updateZoom();
      setWallMode(false);
      return;
    }
    if (detail && !wallMode) {
      setWallMode(true);
    } else if (detail) {
      detail = false;
      replayStart = 0;
      wallMode = false;
      render();
    } else if (wallMode) {
      setWallMode(false);
    } else
      new AlertDialog.Builder(this)
          .setMessage("Close Recorder TV?")
          .setPositiveButton("Close", (d, w) -> finish())
          .setNegativeButton("Stay", null)
          .show();
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused && resumed && !AppLock.allowed(this)) {
      root.setVisibility(View.INVISIBLE);
      resumed = false;
      if (wall != null) wall.suspendWall();
      for (Feed f : feeds) f.release();
      AppLock.require(this);
      return;
    }
    if (focused && returnFocusToTimeline) {
      returnFocusToTimeline = false;
      ui.post(
          () -> {
            if (detail && scrubber != null && !wallMode) scrubber.requestFocus();
          });
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!AppLock.require(this)) {
      resumed = false;
      root.setVisibility(View.INVISIBLE);
      return;
    }
    root.setVisibility(View.VISIBLE);
    lastInteraction = SystemClock.uptimeMillis();
    resumed = true;
    if (profile.overviewNeedsReview && !overviewNoticeShown) {
      overviewNoticeShown = true;
      toast("Camera list recovered. Review overview positions before using 8 / 16 layouts.");
    }
    if (AlarmSafety.any(this))
      toast("A camera alarm stop needs confirmation. Open its Alarm panel to stop it.");
    if (user.isEmpty())
      ui.post(
          () -> {
            if (resumed && AppLock.allowed(this)) account();
          });
    if (!detail && wall != null && profile.combined && pageSize > 4) wall.resumeWall();
    start();
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
    closeStreamCheck();
    closeTalk();
    closeAlarm();
    if (!AppLock.permissionPending(this)) AppLock.lock();
    resumed = false;
    if (root != null) root.setVisibility(View.INVISIBLE);
    if (wall != null) wall.suspendWall();
    for (Feed f : feeds) f.release();
    super.onUserLeaveHint();
  }

  @Override
  protected void onPause() {
    closeZoom(false);
    closeStreamCheck();
    closeTalk();
    closeAlarm();
    resumed = false;
    if (root != null) root.setVisibility(View.INVISIBLE);
    if (wall != null) wall.suspendWall();
    for (Feed f : feeds) f.release();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (optical != null) {
      optical.close();
      optical = null;
    }
    if (recorderSetup != null) recorderSetup.close();
    ui.removeCallbacksAndMessages(null);
    stop();
    if (wall != null) wall.close();
    if (engine != null) engine.release();
    super.onDestroy();
  }

  final class Feed {
    final int id;
    final boolean main;
    final long startTime;
    final FrameLayout view, video;
    final TextureView surface;
    final TextView status;
    int fittedWidth, fittedHeight;
    MediaPlayer player;
    long timeBase = -1, elapsedMs = 0;
    boolean released = false;
    int failures = 0;
    Runnable retry;

    Feed(int c, boolean m, long time) {
      id = c;
      main = m;
      startTime = time;
      view = new FrameLayout(NativeActivity.this);
      view.setFocusable(true);
      view.setFocusableInTouchMode(true);
      view.setOnClickListener(
          v -> {
            if (zoomOverlay != null) {
              zoomPanning = false;
              updateZoom();
              zoomOverlay.focusControls();
            } else setWallMode(!wallMode);
          });
      LinearLayout tile = new LinearLayout(NativeActivity.this);
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setPadding(0, 0, 0, 0);
      view.addView(tile, new FrameLayout.LayoutParams(-1, -1));
      video = new FrameLayout(NativeActivity.this);
      video.setBackgroundColor(Color.BLACK);
      tile.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
      video.setClipChildren(true);
      surface = new TextureView(NativeActivity.this);
      surface.setOpaque(!compatibilityDrawing);
      if (compatibilityDrawing) surface.setAlpha(0.99f);
      video.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
      status = text("Waiting for connection", main ? 15 : 11, 0xffbbbbbb);
      status.setGravity(Gravity.CENTER);
      video.addView(status, new FrameLayout.LayoutParams(-1, -1));
      if (!main || !detail) {
        TextView label = text(cameraName(id), 12, -1);
        label.setPadding(dp(5), 0, 0, 0);
        label.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        video.addView(label, new FrameLayout.LayoutParams(-1, dp(20), Gravity.BOTTOM));
      }
    }

    void start() {
      if (player != null || !resumed || !feeds.contains(this)) return;
      if (pendingPlayerStops > 0) {
        status.setText("Preparing playback…");
        ui.postDelayed(() -> start(), 100);
        return;
      }
      if (video.getWidth() < 2 || video.getHeight() < 2 || !surface.isAvailable()) {
        ui.postDelayed(() -> start(), 100);
        return;
      }
      released = false;
      if (user.isEmpty()) {
        status.setText("Open Recorder connection to connect");
        return;
      }
      try {
        player = new MediaPlayer(engine);
        IVLCVout out = player.getVLCVout();
        out.setVideoView(surface);
        out.attachViews(
            (v, w, h, vw, vh, sn, sd) -> {
              if (released || w <= 0 || h <= 0) return;
              android.util.Log.i("VigiSize", "channel=" + id + " " + w + "x" + h);
              int bw = video.getWidth(), bh = video.getHeight();
              if (bw <= 0 || bh <= 0) return;
              double ratio = (double) (vw > 0 ? vw : w) / (vh > 0 ? vh : h);
              if (sn > 0 && sd > 0) ratio *= ((double) sn / sd);
              int width = bw, height = (int) (bw / ratio);
              if (height > bh) {
                height = bh;
                width = (int) (bh * ratio);
              }
              fittedWidth = width;
              fittedHeight = height;
              surface.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
              out.setWindowSize(width, height);
              if (main) applyDigitalZoom();
            });
        out.setWindowSize(Math.max(1, video.getWidth()), Math.max(1, video.getHeight()));
        player.setEventListener(
            e -> {
              if (released) return;
              switch (e.type) {
                case MediaPlayer.Event.Vout:
                  org.videolan.libvlc.interfaces.IMedia.VideoTrack vt =
                      player.getCurrentVideoTrack();
                  if (vt != null)
                    android.util.Log.i(
                        "VigiVideo",
                        "channel="
                            + id
                            + " main="
                            + main
                            + " "
                            + vt.width
                            + "x"
                            + vt.height
                            + " codec="
                            + vt.codec
                            + " fps="
                            + vt.frameRateNum
                            + "/"
                            + vt.frameRateDen);
                  break;
                case MediaPlayer.Event.Playing:
                  status.setVisibility(View.GONE);
                  failures = 0;
                  break;
                case MediaPlayer.Event.Buffering:
                  if (status.getVisibility() == View.VISIBLE) status.setText("Connecting…");
                  break;
                case MediaPlayer.Event.EncounteredError:
                  status.setVisibility(View.VISIBLE);
                  status.setText(
                      startTime > 0
                          ? "Recording unavailable or connection failed. Try another time."
                          : "Connection failed · retrying…");
                  if (startTime == 0) retry();
                  break;
                case MediaPlayer.Event.EndReached:
                  status.setVisibility(View.VISIBLE);
                  status.setText(
                      startTime > 0 ? "End of recording segment" : "Stream ended · reconnecting…");
                  if (startTime == 0) retry();
                  break;
              }
            });
        String stream =
            startTime == 0
                ? RecorderStreams.live(host, profile.ports.rtsp, user, password, id, main)
                : RecorderStreams.replay(
                    host,
                    profile.ports.rtsp,
                    user,
                    password,
                    id,
                    startTime,
                    Math.min(System.currentTimeMillis(), startTime + 1800000L));
        Media media = new Media(engine, Uri.parse(stream));
        // TV hardware may expose only two HEVC decoder instances. Low-resolution grids
        // use independent software decoders; main/full-screen keeps hardware acceleration.
        media.setHWDecoderEnabled(main, false);
        if (!main) media.addOption(":avcodec-hw=none");
        media.addOption(":rtsp-tcp");
        media.addOption(":rtsp-frame-buffer-size=4194304");
        media.addOption(":no-drop-late-frames");
        media.addOption(":no-skip-frames");
        media.addOption(":no-avcodec-corrupted");
        media.addOption(":network-caching=900");
        if (!main || !detail) media.addOption(":no-audio");
        player.setMedia(media);
        media.release();
        player.setVolume(main && detail && audio ? 100 : 0);
        status.setVisibility(View.VISIBLE);
        status.setText("Connecting…");
        player.play();
      } catch (Exception ignored) {
        status.setText("Unable to start video");
        if (startTime == 0) retry();
      }
    }

    long elapsed() {
      if (player == null) return elapsedMs;
      long now = player.getTime();
      if (now < 0) return elapsedMs;
      if (timeBase >= 0 && now >= timeBase && now - timeBase < 60000) elapsedMs += now - timeBase;
      timeBase = now;
      return elapsedMs;
    }

    void retry() {
      if (retry != null) return;
      int wait = Math.min(30000, 2000 * (1 << Math.min(failures++, 4)));
      retry =
          () -> {
            retry = null;
            if (!resumed || !feeds.contains(this)) return;
            dispose();
            start();
          };
      ui.postDelayed(retry, wait);
    }

    void dispose() {
      if (player == null) return;
      MediaPlayer p = player;
      player = null;
      p.setEventListener(null);
      timeBase = -1;
      elapsedMs = 0;
      p.getVLCVout().detachViews();
      pendingPlayerStops++;
      new Thread(
              () ->
                  PlayerCleanup.run(
                      p::stop,
                      p::release,
                      () ->
                          ui.post(() -> pendingPlayerStops = Math.max(0, pendingPlayerStops - 1))),
              "vigi-stop")
          .start();
    }

    void release() {
      released = true;
      if (retry != null) {
        ui.removeCallbacks(retry);
        retry = null;
      }
      dispose();
    }
  }
}
