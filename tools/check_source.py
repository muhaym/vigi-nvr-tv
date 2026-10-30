"""Reject private installation material from the public source tree."""
from pathlib import Path
import hashlib
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXCLUDE = {".git", ".gradle", "build", ".idea"}
FORBIDDEN = {".p12", ".pfx", ".jks", ".keystore", ".cer", ".crt", ".der", ".pem", ".key", ".apk", ".idsig", ".mp4", ".wav", ".pcm", ".webm", ".mov", ".mkv", ".mp3", ".m4a", ".aac", ".heic", ".sqlite", ".db", ".exe", ".dll", ".so", ".aar", ".png", ".jpg", ".jpeg"}
errors = []
for path in ROOT.rglob("*"):
    relative = path.relative_to(ROOT)
    if not path.is_file() or set(relative.parts) & EXCLUDE or path.name == "local.properties":
        continue
    if path.name in {"legacy_profile.json", "credentials.json"} or (path.name.startswith(".env") and path.name != ".env.example"):
        errors.append(f"Private configuration in source: {relative}")
    if path.suffix.lower() in FORBIDDEN:
        errors.append(f"Private or release artifact in source: {relative}")
    if path.suffix == ".jar":
        if relative.as_posix() != "gradle/wrapper/gradle-wrapper.jar":
            errors.append(f"Unexpected binary JAR: {relative}")
        continue
    try:
        text = path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError:
        errors.append(f"Unexpected binary file: {relative}")
        continue
    if re.search(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", text):
        errors.append(f"Private key: {relative}")
    if re.search(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})\b", text):
        errors.append(f"Credential-shaped token in source: {relative}")
    if "/src/main/" in relative.as_posix() and re.search(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3})\b", text):
        errors.append(f"Hardcoded private address in app: {relative}")
wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
expected = "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
if hashlib.sha256(wrapper.read_bytes()).hexdigest() != expected:
    errors.append("Gradle wrapper differs from verified upstream 8.11.1 JAR")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: no private installation artifacts; verified Gradle wrapper")
