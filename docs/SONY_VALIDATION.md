# Sony 3.8 validation

Target: Sony BRAVIA ATV2 CN, Android9/API28, personal build60 (`3.8-sony.3`).

## Passed

- Release build, R8/resource shrinking, signature verification; 281 JVM tests, zero failures/errors. Lint has zero errors (153 warnings and 146 informational findings remain).
- All 87 recitation files decode on the computer; Android reads all 87 durations. All 90 deployed USB library files match their source SHA256 values. About 30.8 MB of audio stays on USB, outside the APK and Git.
- On the real TV, muted: the Home poem opens listening with its current blurred background; title/author/full verse and meaning are present; previous/next, pause/resume, grade selection, restart, last-to-first wrap, cancellation of delayed next and player release on exit pass. The Home poetry setting switches both ways and the test restores the owner's value/progress.
- Build60 measured completion-to-next delay: 20.046 seconds, within scheduling tolerance of the configured 20-second gap. The test seeks near the recording end, then observes natural completion and the actual delayed transition; it does not wait through every recording in full.
- Window PixelCopy captures were inspected while Sony picture-off kept the panel dark. These verify the app's own rendered window, not a photograph of the TV panel. Recordings were muted; audible quality has not been accepted by a human listener.
- Resource/layout parity, scene captions with the expanded media suffix policy, theme-key documentation and tool-index checks pass.

## Boundaries

The upstream cleanup/tool suite is not fully green on this Windows machine. A clean upstream archive also reproduces 65 failures and 38 errors out of 663 tests (12 skipped), involving shell-launch/encoding/platform behaviour. The final personal-fork run has 664 tests and exactly the same failure/error case names; no additional failing case remains. Additional personal-fork resource/documentation failures found by this run were repaired. This does not claim the upstream tools suite passes; use a supported POSIX test environment before releasing those cleanup tools.

No final 24-hour/multiweek soak, physical remote acceptance of the new poem focus flow, audible narration evaluation, USB hot removal, reboot or HDMI long-run acceptance was performed. Prior mouse-frame measurements were not an equivalent final-build pass, so no universal smoothness or maximum-performance claim is made. All failed build/device attempts are retained in ignored local evidence, including attempts made after the TV's existing idle timer put it in standby.

Device logs, snapshots, actual installation hashes and private media metadata are kept in local `work/poetry-20261023/` and `work/validation.json`; no family photos, recordings, authentication cookies, signing keys or credentials are committed. ADB remains under the owner's control.
