# Performance diagnostics

The launcher writes a per-session performance trace while the Minecraft JVM is running.

## Where to find it

After starting and then closing Minecraft, look in the launcher's external app files directory:

`Android/data/<launcher.package.name>/files/logs/`

The trace is named `game_diagnostics_YYYYMMDD_HHMMSS.log`. It is also included in the launcher's existing **Pack logs** archive because it is stored in the normal logs directory.

## What is recorded

- Device model, manufacturer, hardware, Android version, supported ABIs and reported processor count.
- Java runtime version and VM name.
- A sample every five seconds: process CPU time and estimated CPU utilization, Java heap used/committed/max, Dalvik/native/total PSS, native heap allocation, system available/total RAM, low-memory state, thermal status, process RSS/high-water mark and thread count.
- A once-per-second `[ZALITH_PERF]` FPS sample with the active renderer identifier, written to the game process stderr stream and expected in the game log.

Sampling is deliberately coarse (five seconds) to keep overhead and log size low. The trace does not record account tokens, passwords, JVM arguments or environment variable values.

## Recommended test procedure

1. Close Minecraft completely.
2. Start the same Minecraft version and renderer that you want investigated.
3. Load a reproducible world and keep the game running for at least 5–10 minutes.
4. Include a period of normal play, a period with chunk loading/generation, and (if relevant) a period with shaders.
5. Exit Minecraft normally so the final sample and exit code are written.
6. Upload the matching `game_diagnostics_*.log`, `latest_game.log` (if present), and the launcher log archive.

For a useful before/after comparison, keep the Minecraft version, world, render distance, simulation distance, renderer, shader pack and mod list unchanged.

## Privacy note

The new performance trace avoids account credentials and command-line/environment contents. Existing launcher/game logs may still contain local file paths, account names, server addresses, mod names or other user-provided text. Review those files before making them public; redact anything private without removing timestamps, exception traces or performance lines.
