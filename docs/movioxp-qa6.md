# MovioXP 3.1.0-qa6

Android QA, version code 100097, based on QA5. Central V2.3.1 remains the server baseline.

- Phone sign-in shows a smaller QR with the pink PIN directly underneath.
- Discover has a denser TV rail and poster grid, a sports category and the same search engine as Home.
- TV card sizing is profile-local and excluded from Central sync.
- Playback offers Resume or Restart for meaningful progress and a Restart control with confirmation. Automatic starts prefer the last successful source and can try alternatives.
- Mobile movie and series selections open information. Episodes are grouped by season and show supplied metadata and viewing indicators.
- Long-press Up or Down jumps within loaded Home and Discover/Library content. TV search fields defer keyboard activation.
- About uses the white and pink MovioXP wordmark, and the Home header icon is sized to match the profile control.
- Dormant activation policy evaluator, signature verification, restriction screen, and simulated policy tests. No live provider or enforcement wiring is configured.

## Central priority compatibility

The Android client reads sort_order or priority, falling back to response order. Incoming changes supersede a local override when the server list changes. The known write operation replaces the entire list and the known read operation can omit disabled assignments. Safe write-back needs an atomic reorder operation or a complete snapshot with a revision. Existing installs, removals and sync keep their current contract.

## Future activation contract

A trusted provider must return signed canonical bytes binding account ID, activation support and enforcement, blocked and permanent status, expiry, server time, optional grace period and administrator message. A future integration must pin trust keys and persist the policy and time anchor in encrypted storage. Use a deliberate migration for current accounts. The proposed administration default is 30 days. QA has no configured key, endpoint or grace policy.

A future integration must refresh on startup, login, foreground return, network recovery, manual checks and at bounded intervals. It must retain accepted policy through failures, stop playback and guard content and sync entry points on confirmed restriction, while retaining authentication and updates. Reinstallation protection depends on the server.

## Physical QA

Check the PIN and QR on short screens, real-account sign-in, TV focus and keyboard, card sizing and pinch, search, sports artwork, resume/restart and source fallback, audio and subtitles, episode history, priority display and upgrade from QA5. Activation tests simulate the future contract.
