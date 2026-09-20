# Error Log Plan

Phase 1 includes a minimal `CrashLogger` that installs an uncaught-exception handler and writes the latest stack trace to the app-private file `last_crash.txt`.

A later diagnostics increment should add:
- A settings screen action to copy or share the latest log
- A bounded rolling log rather than one crash file
- User-visible non-fatal error messages
- Explicit privacy controls and a clear-log action

No logs are sent over the network in Phase 1.
