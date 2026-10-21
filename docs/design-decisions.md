# Design decisions

## 2026-10-14: Phase 1 NFC reader

CAD uses Android reader mode with NFC-A and IsoDep. It starts all exchanges and contains no `HostApduService`.

Every APDU goes through `CardTransport`. `IsoDepCardTransport` serializes exchanges off the main thread and uses a configurable 15-second timeout because later RSA work may be slow. `InMemoryCardTransport` implements only the Phase 1 read surface and does not expose development commands.

Phase 1 is unauthenticated by specification. No authentication, write, development, or record commands are sent.

The current `minSdk` is 31. Reader mode itself supports lower Android versions; 31 is retained from the project baseline and is compatible with the planned Keystore work.

## 2026-10-21: Phase 2 Record Read & Write

Commands: `ADD_RECORD` (`80 10 00 00 Lc blob 00`) and `GET_RECORDS` (`80 12 P1 P2 00`).
Phase 2 commands are unauthenticated by specification; this is explicitly documented and enforced.

Record encoding:
- `RecordCodec` encodes records into opaque binary blobs (max 180 bytes) following S-MAPLE v1 layout: header (version 1 in bits 7-5, presence flags in bits 4-0), uint24 UTC timestamp (minutes since 2025-01-01), 1-8 byte ASCII HP ID, optional BP (systolic - 40, diastolic - 20), temperature (tenths C - 300), heart rate (bpm), medicines (semicolon-delimited ASCII string, max 60 chars), and notes/description (max 100 chars).
- Validation enforces strict ranges: systolic 40..295, diastolic 20..275, temperature 30.0..55.5 °C, heart rate 0..255 bpm, printable ASCII (0x20..0x7E) only.

Verification and atomicity:
- Write & verify flow: `MCardReader.writeAndVerify` issues `ADD_RECORD` followed immediately by `GET_RECORDS(start = 0)` to guarantee the card stored the exact record sent.
- If identical newest record is sent, card returns `01` (already stored) without duplicate insertion.
- `InMemoryCardTransport` implements the 10-record ring buffer with oldest-first eviction and 200-byte page filling.
- Written records are persisted locally in `RecordStore` (backed by SQLite/Room table `notes`) with `pending_upload = 1` to prevent data loss upon card eviction.

UI & flow:
- Vitals form implements the Step Bar (`Scan` -> `Vitals` -> `Save`), per-field mic touch targets (>= 48 dp), Celsius/Fahrenheit toggle, human-readable review screen, and unsaved changes confirmation dialog.
