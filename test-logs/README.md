# Minecraft performance test logs

This folder is reserved for diagnostic logs collected while testing Zalith 2 Reforged.

## What to upload

For each test session, add:

- `game_diagnostics_YYYYMMDD_HHMMSS.log` — device, CPU, memory, thermal and process measurements.
- `game_output_YYYYMMDD_HHMMSS.log` — Minecraft/launcher output captured for the same session.

Keep the original filenames so the two files can be matched by timestamp. If you run multiple tests, place each session's pair here.

## Before uploading

Review logs for private information. Remove account identifiers, access tokens, passwords, server addresses, personal file paths, or other information you do not want published. Never upload session cookies, OAuth tokens, or credentials.

## Test conditions

When possible, include a short note with each session describing:

- Minecraft version and whether the test is vanilla or modded;
- renderer and graphics settings;
- render distance and simulation distance;
- approximate test duration;
- whether the device was already warm or charging.

Do not edit the diagnostic measurements themselves. Put test notes in a separate `.md` file.

These logs are for performance diagnosis; they are not automatically collected or uploaded by the launcher.
