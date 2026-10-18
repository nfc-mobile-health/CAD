# Design decisions

## 2026-10-14: Phase 1 NFC reader

CAD uses Android reader mode with NFC-A and IsoDep. It starts all exchanges and contains no `HostApduService`.

Every APDU goes through `CardTransport`. `IsoDepCardTransport` serializes exchanges off the main thread and uses a configurable 15-second timeout because later RSA work may be slow. `InMemoryCardTransport` implements only the Phase 1 read surface and does not expose development commands.


Phase 1 is unauthenticated by specification. No authentication, write, development, or record commands are sent.

The current `minSdk` is 31. Reader mode itself supports lower Android versions; 31 is retained from the project baseline and is compatible with the planned Keystore work.
