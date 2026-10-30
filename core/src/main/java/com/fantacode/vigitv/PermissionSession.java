package com.fantacode.vigitv;

/** One in-memory exception for a permission prompt opened by the unlocked activity. */
final class PermissionSession {
  private Object owner;
  private int request;
  private long epoch, until;

  synchronized void begin(Object activity, int code, long generation, long now) {
    owner = activity;
    request = code;
    epoch = generation;
    until = now + 120000;
  }

  synchronized boolean active(Object activity, long generation, long now) {
    return owner == activity && generation == epoch && now < until;
  }

  synchronized boolean finish(Object activity, int code, long generation, long now) {
    if (request != code || !active(activity, generation, now)) return false;
    cancel();
    return true;
  }

  synchronized Object owner() {
    return owner;
  }

  synchronized void cancel() {
    owner = null;
    until = 0;
  }
}
