package com.fantacode.vigitv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import org.videolan.libvlc.*;
import org.videolan.libvlc.MediaPlayer;

/** One NVR decoder; GPU draws selected camera regions into an adaptive native wall. */
final class MosaicWall extends FrameLayout {
  interface Listener {
    void select(int id);

    void options(int id);
  }

  final LibVLC engine;
  final Listener listener;
  final GLSurfaceView gl;
  final FrameLayout labels;
  final TextView status;
  final Handler ui = new Handler(Looper.getMainLooper());
  final Renderer renderer = new Renderer();
  final ArrayList<Integer> channels = new ArrayList<>();
  String[] names;
  volatile int[] sourceCell = new int[RecorderProfile.MAX_CHANNELS + 1];
  volatile int sourceColumns = 4, sourceRows = 4;

  void mapping(int[] cells, int columns, int rows) {
    sourceCell = cells;
    sourceColumns = columns;
    sourceRows = rows;
  }

  volatile List<Tile> tiles = Collections.emptyList();
  MediaPlayer player;
  Surface inputSurface;
  boolean active = false, closed = false;
  int attempts = 0;
  Runnable retry;
  String host = "", user = "", password = "";
  int layoutSize = 4;

  static final class Tile {
    final int id;
    final float x, y, w, h;

    Tile(int i, float a, float b, float c, float d) {
      id = i;
      x = a;
      y = b;
      w = c;
      h = d;
    }
  }

  MosaicWall(Context context, LibVLC e, String[] cameraNames, Listener l) {
    super(context);
    engine = e;
    names = cameraNames;
    listener = l;
    setBackgroundColor(Color.BLACK);
    gl = new GLSurfaceView(context);
    gl.setEGLContextClientVersion(2);
    gl.setPreserveEGLContextOnPause(true);
    gl.setRenderer(renderer);
    gl.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    gl.setFocusable(false);
    addView(gl, new LayoutParams(-1, -1));
    gl.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    labels = new FrameLayout(context);
    labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    addView(labels, new LayoutParams(-1, -1));
    status = new TextView(context);
    status.setText("Connecting to camera overview…");
    status.setTextColor(Color.WHITE);
    status.setTextSize(15);
    status.setGravity(Gravity.CENTER);
    status.setBackgroundColor(0xb0000000);
    addView(status, new LayoutParams(-1, -1));
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  void account(String h, String u, String p) {
    boolean changed = !h.equals(host) || !u.equals(user) || !p.equals(password);
    host = h;
    user = u;
    password = p;
    if (changed) {
      stopPlayer();
      if (active) startPlayer();
    }
  }

  void cameras(List<Integer> ids, int layout) {
    channels.clear();
    channels.addAll(ids);
    layoutSize = ViewLayout.normalize(layout);
    arrange();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    ui.post(() -> arrange());
  }

  void arrange() {
    int width = getWidth(), height = getHeight();
    float gap = dp(6);
    if (width <= gap * (ViewLayout.columns(layoutSize) - 1)
        || height <= gap * (ViewLayout.rows(layoutSize) - 1)) return;
    boolean restoreFocus = labels.hasFocus() || getRootView().findFocus() == null;
    String focusedCamera =
        labels.findFocus() == null
            ? ""
            : String.valueOf(labels.findFocus().getContentDescription());
    labels.removeAllViews();
    ArrayList<Tile> next = new ArrayList<>();
    for (int index = 0; index < layoutSize; index++) {
      ViewLayout.Cell cell = ViewLayout.cell(layoutSize, index, width, height, gap);
      FrameLayout card = new FrameLayout(getContext());
      GradientDrawable outline = new GradientDrawable();
      outline.setColor(Color.TRANSPARENT);
      outline.setStroke(dp(1), 0xff202836);
      card.setForeground(outline);
      if (index < channels.size()) {
        final int id = channels.get(index);
        next.add(new Tile(id, cell.x, cell.y, cell.width, cell.height));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setContentDescription(names[id - 1]);
        card.setOnFocusChangeListener(
            (v, focused) -> {
              GradientDrawable border = new GradientDrawable();
              border.setColor(Color.TRANSPARENT);
              border.setStroke(dp(focused ? 2 : 1), focused ? 0xffe4ebff : 0xff202836);
              v.setForeground(border);
            });
        card.setOnClickListener(v -> listener.select(id));
        card.setOnLongClickListener(
            v -> {
              listener.options(id);
              return true;
            });
        TextView caption = new TextView(getContext());
        caption.setText(names[id - 1]);
        caption.setTextColor(0xffedf1f7);
        caption.setTextSize(layoutSize == 16 ? 10 : 12);
        caption.setSingleLine();
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setTypeface(
            android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        caption.setGravity(Gravity.BOTTOM);
        caption.setPadding(dp(10), 0, dp(10), dp(8));
        caption.setBackground(
            new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0xc9000000}));
        card.addView(caption, new LayoutParams(-1, dp(36), Gravity.BOTTOM));
      } else {
        card.setBackgroundColor(0xff0c1017);
        TextView empty = new TextView(getContext());
        empty.setText("Empty");
        empty.setTextSize(11);
        empty.setTextColor(0xff495362);
        empty.setGravity(Gravity.CENTER);
        card.addView(empty, new LayoutParams(-1, -1));
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      }
      LayoutParams position = new LayoutParams(Math.round(cell.width), Math.round(cell.height));
      position.leftMargin = Math.round(cell.x);
      position.topMargin = Math.round(cell.y);
      labels.addView(card, position);
    }
    tiles = next;
    gl.requestRender();
    labels.requestLayout();
    ui.postDelayed(
        () -> {
          if (restoreFocus
              && !channels.isEmpty()
              && active
              && (labels.hasFocus() || getRootView().findFocus() == null)) {
            View target = labels.getChildAt(0);
            for (int i = 0; i < labels.getChildCount(); i++)
              if (focusedCamera.equals(
                  String.valueOf(labels.getChildAt(i).getContentDescription())))
                target = labels.getChildAt(i);
            target.requestFocus();
          }
        },
        150);
  }

  void resumeWall() {
    if (closed) return;
    active = true;
    gl.onResume();
    startPlayer();
  }

  void suspendWall() {
    active = false;
    if (retry != null) {
      ui.removeCallbacks(retry);
      retry = null;
    }
    stopPlayer();
  }

  void startPlayer() {
    if (!active || closed || player != null || inputSurface == null || !inputSurface.isValid())
      return;
    if (user.isEmpty()) {
      status.setText("Open Account to connect to your recorder");
      status.setVisibility(VISIBLE);
      return;
    }
    try {
      android.util.Log.i("WallStream", "Opening shared overview");
      MediaPlayer p = new MediaPlayer(engine);
      player = p;
      p.getVLCVout().setVideoSurface(inputSurface, null);
      p.getVLCVout().attachViews();
      p.getVLCVout().setWindowSize(1920, 1080);
      p.setEventListener(
          event -> {
            if (player != p) return;
            if (event.type == MediaPlayer.Event.EncounteredError
                || event.type == MediaPlayer.Event.EndReached) {
              status.setText("Camera overview disconnected · reconnecting…");
              status.setVisibility(VISIBLE);
              scheduleRetry();
            } else if (event.type == MediaPlayer.Event.Playing) attempts = 0;
          });
      Media media =
          new Media(
              engine,
              Uri.parse(
                  RecorderStreams.combined(
                      host,
                      RecorderServices.profile(getContext(), host).ports.rtsp,
                      user,
                      password)));
      media.setHWDecoderEnabled(true, false);
      media.addOption(":rtsp-tcp");
      media.addOption(":rtsp-frame-buffer-size=4194304");
      media.addOption(":network-caching=800");
      media.addOption(":no-audio");
      media.addOption(":no-skip-frames");
      p.setMedia(media);
      media.release();
      p.setVolume(0);
      p.play();
    } catch (Exception ignored) {
      status.setText("Unable to open camera overview");
      status.setVisibility(VISIBLE);
      scheduleRetry();
    }
  }

  void scheduleRetry() {
    if (retry != null || !active) return;
    retry =
        () -> {
          retry = null;
          if (active && !closed) {
            stopPlayer();
            startPlayer();
          }
        };
    ui.postDelayed(retry, Math.min(30000, 2000 * (1 << Math.min(attempts++, 4))));
  }

  void stopPlayer() {
    MediaPlayer p = player;
    player = null;
    if (p == null) return;
    p.setEventListener(null);
    try {
      p.getVLCVout().detachViews();
    } catch (Exception ignored) {
    }
    new Thread(
            () -> {
              try {
                p.stop();
              } catch (Exception ignored) {
              }
              p.release();
            },
            "vigi-wall-stop")
        .start();
  }

  void close() {
    closed = true;
    suspendWall();
    ui.removeCallbacksAndMessages(null);
    gl.onPause();
  }

  final class Renderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    int texture, program, position, uv, matrix, sampler;
    SurfaceTexture incoming;
    Surface surface;
    volatile boolean pending = false;
    final float[] transform = new float[16];
    final FloatBuffer
        vertex = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(),
        coords = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    int shader(int type, String source) {
      int s = GLES20.glCreateShader(type);
      GLES20.glShaderSource(s, source);
      GLES20.glCompileShader(s);
      return s;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      program = GLES20.glCreateProgram();
      GLES20.glAttachShader(
          program,
          shader(
              GLES20.GL_VERTEX_SHADER,
              "attribute vec2 p;attribute vec2 uv;uniform mat4 tx;varying vec2 v;void"
                  + " main(){gl_Position=vec4(p,0.,1.);v=(tx*vec4(uv,0.,1.)).xy;}"));
      GLES20.glAttachShader(
          program,
          shader(
              GLES20.GL_FRAGMENT_SHADER,
              "#extension GL_OES_EGL_image_external : require\n"
                  + "precision mediump float;varying vec2 v;uniform samplerExternalOES image;void"
                  + " main(){gl_FragColor=texture2D(image,v);}"));
      GLES20.glLinkProgram(program);
      position = GLES20.glGetAttribLocation(program, "p");
      uv = GLES20.glGetAttribLocation(program, "uv");
      matrix = GLES20.glGetUniformLocation(program, "tx");
      sampler = GLES20.glGetUniformLocation(program, "image");
      int[] ids = new int[1];
      GLES20.glGenTextures(1, ids, 0);
      texture = ids[0];
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      incoming = new SurfaceTexture(texture);
      incoming.setDefaultBufferSize(1920, 1080);
      incoming.setOnFrameAvailableListener(this);
      surface = new Surface(incoming);
      android.opengl.Matrix.setIdentityM(transform, 0);
      Surface ready = surface;
      ui.post(
          () -> {
            if (closed) {
              ready.release();
              return;
            }
            stopPlayer();
            inputSurface = ready;
            startPlayer();
          });
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
      GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture texture) {
      pending = true;
      gl.requestRender();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
      GLES20.glClearColor(0, 0, 0, 1);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      if (incoming == null) return;
      if (pending) {
        pending = false;
        try {
          incoming.updateTexImage();
          incoming.getTransformMatrix(transform);
          ui.post(() -> status.setVisibility(GONE));
        } catch (Exception ignored) {
          return;
        }
      }
      int width = getWidth(), height = getHeight();
      if (width < 2 || height < 2) return;
      GLES20.glUseProgram(program);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
      GLES20.glUniform1i(sampler, 0);
      GLES20.glUniformMatrix4fv(matrix, 1, false, transform, 0);
      GLES20.glEnableVertexAttribArray(position);
      GLES20.glEnableVertexAttribArray(uv);
      for (Tile t : tiles) {
        float vw = t.w, vh = vw * 9 / 16f;
        if (vh > t.h) {
          vh = t.h;
          vw = vh * 16 / 9f;
        }
        float x = t.x + (t.w - vw) / 2, y = t.y + (t.h - vh) / 2;
        float left = 2 * x / width - 1,
            right = 2 * (x + vw) / width - 1,
            top = 1 - 2 * y / height,
            bottom = 1 - 2 * (y + vh) / height;
        vertex.clear();
        vertex.put(new float[] {left, bottom, right, bottom, left, top, right, top}).position(0);
        int cell = sourceCell[t.id];
        if (cell < 0) continue;
        int col = cell % sourceColumns, row = cell / sourceColumns;
        float u0 = (float) col / sourceColumns,
            u1 = (float) (col + 1) / sourceColumns,
            v0 = 1 - (float) (row + 1) / sourceRows,
            v1 = 1 - (float) row / sourceRows;
        coords.clear();
        coords.put(new float[] {u0, v0, u1, v0, u0, v1, u1, v1}).position(0);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertex);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, coords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      }
    }
  }
}
