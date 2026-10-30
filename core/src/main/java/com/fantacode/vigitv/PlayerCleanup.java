package com.fantacode.vigitv;

/** Always releases the player and completes the start barrier, even if stop / release fails. */
final class PlayerCleanup {
  interface Work {
    void run() throws Exception;
  }

  static void run(Work stop, Work release, Runnable complete) {
    try {
      stop.run();
    } catch (Exception ignored) {
    } finally {
      try {
        release.run();
      } catch (Exception ignored) {
      } finally {
        complete.run();
      }
    }
  }
}
