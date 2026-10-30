package com.fantacode.vigitv;

public final class PermissionSessionSelfTest {
  static void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
  }

  public static void main(String[] args) {
    Object owner = new Object(), other = new Object();
    PermissionSession session = new PermissionSession();
    check(!session.active(owner, 7, 100), "no ambient exception");
    session.begin(owner, 92, 7, 100);
    check(session.active(owner, 7, 101), "owner request retained");
    check(!session.active(other, 7, 101), "other activity denied");
    check(!session.active(owner, 8, 101), "lock epoch invalidates");
    check(!session.finish(owner, 91, 7, 101), "unrelated result ignored");
    check(session.active(owner, 7, 101), "wrong result cannot consume");
    check(session.finish(owner, 92, 7, 102), "matching result");
    check(!session.active(owner, 7, 103), "result consumes exception");
    session.begin(owner, 92, 7, 100);
    check(!session.active(owner, 7, 120100), "deadline expires");
    session.begin(owner, 92, 7, 100);
    session.cancel();
    check(!session.active(owner, 7, 101), "Home or screen off cancels");
    session.begin(owner, 92, 7, 100);
    session.begin(other, 91, 7, 101);
    check(!session.active(owner, 7, 102), "new request replaces owner");
    System.out.println(
        "PASS: permission ownership, request matching, single use, lock generation, timeout and"
            + " cancellation.");
  }
}
