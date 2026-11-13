# TON Wallet Core — Agent Guide (Android, Kotlin)

> Purpose: give an AI coding agent (e.g. Gemini) a single, ordered source of truth for building the **core** (non-UI) of a Tonkeeper-like, **non-custodial, TON-only** Android wallet.
> Build the core first (Compose UI is kept minimal/placeholder until the human supplies the design).

---

## START HERE (read this first, 1 minute)

**What you are building:** a non-custodial wallet for the TON network (native coin **GRAM**, formerly Toncoin; plus **USDT** Jetton). Android, **Kotlin + Jetpack Compose only**. Environment limits: **no NDK / no native C/C++ code**, no GitHub export for Android projects in this environment, so keep the project small, buildable and easy to copy out manually.

**How you must work (7 rules):**
1. Work on **ONE phase at a time**. Current phase: **PHASE 0**. (The human updates this line when moving on.)
2. Before any code: write a **plan of max 10 lines** (files to create/change, SDK calls you will use and where you verified them, tests to add). Then wait for the human's "go".
3. Only read the sections listed for the current phase in the table below. Ignore the rest until needed.
4. **Never invent SDK class/method names.** If you cannot verify one, write `UNKNOWN` and ask.
5. Do not touch files outside the current phase scope. Do not refactor finished phases.
6. If rules conflict, priority is: Section 2 (security) > Section 7 (tx state) > Section 15 (hygiene) > everything else.
7. When the phase is done, send a **Phase Report** (format below) and **STOP**. Do not start the next phase.

**Which sections to read per phase:**

| Phase | Read sections |
|---|---|
| 0 Spike | 1, 3, 15, 16 |
| 1 Keys/mnemonic/address | 2, 3, 10, 15, 16 |
| 2 Import | 2, 3, 15, 16 |
| 3 Network + balance | 2, 9, 15, 16 |
| 4 Receive | 2, 15, 16 |
| 5 Send GRAM | 2, 7, 9, 10, 15, 16 |
| 6 History | 8, 9, 15, 16 |
| 7 Jettons/USDT | 2, 7, 9, 15, 16 |
| 8 Polish | 2, 9, 15, 16 |
| 9 Pre-release | 2, 10, 11, 15, 16 |

**Phase Report format (always exactly this):**
```
PHASE N REPORT
- Done: (bullet list of what works)
- Files changed: (list)
- SDK/library versions used: (exact)
- Verified real API names used: (list)  |  UNKNOWN items: (list)
- Tests run and result: (pass/fail per item from the Section 16 checklist)
- Not done / risks: (honest list)
- Hygiene search (Section 15) result: (clean / hits removed)
- Next phase proposal: (one line, do NOT start it)
```

---

## 0. How the agent must use this file

1. Read the whole file once. Then work **one phase at a time** (Section 6). Never start a phase before the previous phase's acceptance criteria pass.
2. **Never invent APIs.** Class and method names of any SDK must be verified against the real SDK source/docs for the exact version in use. If unsure, say so and ask. Code samples in this file are *design contracts*, not guaranteed-compilable SDK calls.
3. Tags used below:
   - **[VERIFIED]** checked against official docs/specs at time of writing (Nov 2026).
   - **[UNVERIFIED]** must be tested in a spike before relying on it.
   - **[RULE]** non-negotiable.
4. If a requirement conflicts with a [RULE], the rule wins. Ask the human.
5. Prefer small, testable, pure functions in the core. No Android UI classes in the core module except where Section 10 requires Keystore/Biometric.

---

## 1. Product scope

**Goal:** TON-only non-custodial wallet: create/import wallet, receive, send TON, send Jettons (incl. USDT), transaction history, address book, balance.

**Non-goals (do NOT build in early phases):** swaps, staking, NFTs, TON Connect / dApp browser, multi-chain, custodial features, user accounts/server login.

**Constraints from the human:**
- No own backend/domain for now. App talks directly to public TON APIs.
- Free tier first, must tolerate thousands of users → minimize requests, cache aggressively, multi-provider fallback.
- Security and never losing/mis-showing transactions are top priority.

**Naming (important, changed in 2026) [VERIFIED]:**
- The native coin formerly called **Toncoin (ticker TON)** was renamed **Gram (ticker GRAM)** on 15 June 2026. The **network is still called TON / The Open Network**.
- It is a rename only: same addresses, balances and on-chain state; no migration or swap. Any site/bot/message asking users to "claim GRAM" or "migrate" is a scam — show a warning about this in the app's help text.
- In code and UI use: coin = `GRAM`, network = `TON`. Libraries, docs, APIs and old data may still say "TON"/"Toncoin" for the coin — treat as the same asset. Do not create a second asset type.
- Ecosystem asked apps to show "Gram (prev. Toncoin)" during a ~3-month transition (from June 2026); after that plain "Gram"/"GRAM" is fine. Decimals are assumed unchanged (9) — **[UNVERIFIED]** confirm against the SDK/API before hardcoding.
- Wherever this guide says "TON" for the coin (e.g. "Send TON", `Money` in nano), read it as **GRAM**.

---

## 2. Hard security rules [RULE]

1. Mnemonic, seed, private key (and any decrypted form) **never** leave the device: not to any server, log, analytics, crash report, clipboard (except explicit user action for address only), or messenger.
2. Never store mnemonic/private key in SharedPreferences, SQLite/Room, plain files, or `String` objects that live long. Use `ByteArray`/`CharArray` and zero them after use (best effort; JVM gives no guarantee).
3. Private key at rest = encrypted with an AES-256-GCM key stored in **Android Keystore** (non-exportable). Details in Section 10.
4. Every send requires: user confirmation screen data (full destination address, amount, fee estimate, network) + user authentication (biometric/PIN) before signing.
5. Never trust API responses for security decisions. Validate addresses/amounts locally. Indexed APIs (TON Center v3, TonAPI) return **no proofs** [VERIFIED].
6. Money = `BigInteger` in nano units (TON: 9 decimals). Never `double`/`float`. Jettons use the token's own `decimals` from its metadata/master.
7. Mainnet/Testnet separation must be enforced by types (see Section 5). Never accept a Testnet address in a Mainnet flow.
8. No logging of addresses, amounts, keys in release builds. Release: `debuggable=false`, R8 on, `allowBackup=false`.
9. Dependencies pinned to exact versions. Review license before adding (see Section 3: TON4J is GPL-3.0).

---

## 3. Technology decisions

| Topic | Decision | Status |
|---|---|---|
| Wallet contract | **Wallet V5R1** for new wallets | [VERIFIED] TEP-3 recommends V5R1 |
| New mnemonic | **12-word BIP-39 "multichain"** mnemonic; derivation BIP-39 seed → SLIP-0010 Ed25519 path `m/44'/607'/0'` | [VERIFIED] TEP-3 (recommended). Do not create 24-word multichain mnemonics (TEP-3: SHOULD NOT) |
| Import support | 12-word multichain, 24-word TON mnemonic, 24-word multichain | [VERIFIED] TEP-3 §9. For 24-word: validate against both schemes; ~0.4% valid in both → derive both and let the user pick; never choose silently |
| V5 wallet_id / subwallet id | Mainnet `0x7FFFFF11`, Testnet `0x7FFFFFFD` (differ per network, also replay protection) | [VERIFIED] TEP-3 §6 |
| Address derivation | Depends on: wallet contract type + public key + workchain + wallet_id/network. **Mnemonic alone does not determine the address.** | [VERIFIED] docs.ton.org |
| Import discovery | On import, find accounts for V3R1…V5R1 for the key, show all (even zero balance), let user choose | [VERIFIED] TEP-3 §7 (SHOULD) |
| Library A | **TON WalletKit Android** (official; Signer → Adapter → Wallet pattern; V5R1 + V4R2) | [VERIFIED] exists; docs samples are Kotlin. Works inside this build environment **[UNVERIFIED]** → Phase 0 |
| Library B | **TON4J** `org.ton.ton4j` (Java ≥ 11, latest seen 2.1.0, Jun 2026) | [VERIFIED] exists. **License GPL-3.0** → can force open-sourcing the app. Android compatibility (native libs, JVM-only APIs) **[UNVERIFIED]** |
| Language | **Kotlin + Jetpack Compose only** (environment restriction). Public facade should avoid exposing `suspend` where a Java caller might exist; otherwise Flow/coroutines are fine | fixed |
| Local DB | **Room** (SQLite) as cache only | decision |
| HTTP | OkHttp (+ Retrofit optional). **`java.net.http` is NOT available on Android.** | [VERIFIED] |

**Phase 0 spike must confirm Library A works in this Kotlin/Compose environment** (see Section 6). Library B is excluded unless the human approves open-sourcing and it passes the no-native-code limit. Do not write wallet logic until the spike result is known.

---

## 4. Architecture and modules

```
app/                      (UI, human-built; depends on :core facade only)
core/
 ├─ wallet/               WalletEngine, MnemonicService, AddressService, TransferBuilder, Signer
 ├─ keystore/             SecureKeyStore, BiometricGate  (only place touching Keystore/Biometric)
 ├─ network/              ProviderRouter, TonCenterClient, TonApiClient, LiteServerClient(optional), RateLimiter, Cache
 ├─ data/                 Room: TxDao, AddressBookDao, BalanceCacheDao; Repositories
 ├─ tx/                   TxStateMachine, TxTracker (confirmation polling/streaming)
 └─ model/                Network, TonAddress, Money, Tx, Jetton, errors
```

Rules:
- `wallet/` has **no** network code and **no** Android deps except through interfaces.
- `network/` never sees keys.
- Only `keystore/` and `wallet/Signer` ever touch secret bytes.
- `app/` talks to a single facade: `WalletCore` (Section 5).

---

## 5. Core contracts (design; adapt names to chosen SDK)

```kotlin
enum class Network { MAINNET, TESTNET }

// Address must carry its network; parsing rejects mismatches.
data class TonAddress(val raw: String, val network: Network)

// Always nano / smallest unit. Never double.
data class Money(val units: java.math.BigInteger, val decimals: Int)

interface WalletCore {
    fun createWallet(network: Network): NewWallet            // returns address + mnemonic ONLY for one-time backup display
    fun importWallet(words: CharArray, network: Network): List<CandidateAccount>
    fun selectAccount(candidate: CandidateAccount)
    fun getReceiveAddress(): TonAddress
    fun observeBalance(): Flow/LiveData/Callback<BalanceState>   // cached first, then fresh
    fun observeHistory(): ...
    fun prepareTransfer(req: TransferRequest): TransferPreview   // validates + estimates fee, no signing
    fun confirmAndSend(preview: TransferPreview, auth: AuthResult): TxHandle
    fun txState(handle: TxHandle): TxState
}

sealed class TransferRequest {
    data class Ton(val to: TonAddress, val amount: Money, val comment: String?) : TransferRequest()
    data class Jetton(val master: TonAddress, val to: TonAddress, val amount: Money, val comment: String?) : TransferRequest()
}
```

Notes:
- `NewWallet.mnemonic` is returned once, shown on a `FLAG_SECURE` screen, then zeroed. Backup confirmation (user re-enters N random words) must pass before the wallet is marked "backed up".
- `prepareTransfer` must not sign. `confirmAndSend` signs only after `AuthResult` is valid.

---

## 6. Phased build plan (strict order)

### Phase 0 — Spike (1 small throwaway project) 
Goal: decide the library and prove Android feasibility.
- Add WalletKit Android (Kotlin). Create a **Testnet** V5R1 wallet and show its address on a placeholder screen. State your chosen min API level and why (Section 13).
- Do NOT try TON4J (GPL-3.0 and possible native code) unless the human explicitly approves. If WalletKit cannot be added or does not build, STOP and report the exact error; do not switch library on your own.
- Output: a short written decision (which library, why, known gaps). **Acceptance:** Testnet address generated on device; decision recorded.

### Phase 1 — Keys, mnemonic, address
- Mnemonic generation (12-word multichain) with `SecureRandom`/SDK-provided secure generator. Do not hand-roll crypto.
- Derivation: `m/44'/607'/0'` SLIP-0010 Ed25519.
- V5R1 address for Mainnet and Testnet (use correct wallet_id per network).
- `SecureKeyStore` (Section 10). Backup-confirmation flow logic (core side).
- **Acceptance (unit tests):** known test vectors for mnemonic→key→address; Testnet address ≠ Mainnet address for same key; address round-trips; wrong-network address rejected; restart app → key decrypts only after auth.

### Phase 2 — Import
- Import 12/24-word phrases per TEP-3 rules; both-scheme detection for 24 words; account discovery V3R1…V5R1; return a list, never auto-pick.
- **Acceptance:** tests for each mnemonic type, the ambiguous-24 case, and invalid-checksum rejection.

### Phase 3 — Network layer + balance
- `ProviderRouter` with ordered providers and fallback (Section 9). Balance with cache-then-refresh.
- **Acceptance:** simulated HTTP 429/500/timeouts → router falls back and backs off; no crash; stale cache shown with "stale" flag.

### Phase 4 — Receive
- Address display, QR payload (`ton://transfer/<address>` style link — verify format against current docs before coding), address-validity checks.

### Phase 5 — Send TON
- Steps: validate → fetch `seqno` + account state → estimate fee → build message → **choose bounce flag from destination state** → sign → broadcast (`sendBoc`) → track.
- **Bounce rule [VERIFIED, TEP-2]:** if destination is uninitialized, send **non-bounceable**; otherwise bounceable. Do not hardcode `bounceable=true`.
- Use short `valid_until` (message expiry) so stale messages cannot execute later.
- **Acceptance (Testnet):** send, see CONFIRMED; send to uninitialized address works non-bounceable; app killed mid-send → on restart state is recovered (Section 7); duplicate send prevented.

### Phase 6 — History
- Use an indexed API (TON Center v3 / TonAPI). Store in Room; fetch only newer than last stored `lt`/timestamp. Reconcile with local pending txs.

### Phase 7 — Jettons / USDT
- Jetton balance = balance of the **user's Jetton Wallet contract** for that master (get via `get_wallet_address` on the master, or indexer). Seeing an incoming tx or a token name/logo is **not** proof of an asset.
- Only allow Jetton masters from a **curated allow-list** obtained from an official source. Never trust name/symbol/logo.
- Transfer message per TEP-74 (`transfer#0f8a7ea5`: `query_id`, `amount`, `destination`, `response_destination`, `custom_payload`, `forward_ton_amount`, `forward_payload`). Attach enough TON for fees/forward. Amount via `BigInteger` using the Jetton's decimals.
- **Acceptance (Testnet):** send/receive a test Jetton; wrong decimals test; fake-Jetton display test.

### Phase 8 — Polish
- Address book, price display (display only), app lock, rate-limit tuning, optional streaming for "tx confirmed" on open screens.

### Phase 9 — Pre-release
- Independent security review of `keystore/` and signing code, dependency/license audit, Mainnet with tiny amounts, then release. Do not ship to real funds without review.

---

## 7. Transaction state machine (core of "never lose a tx")

States: `DRAFT → SIGNED → BROADCASTED → CONFIRMED | FAILED | EXPIRED`

Rules:
1. Persist the tx in Room **before** broadcasting (state `SIGNED`, with `seqno`, `valid_until`, destination, amount, network). Writes are atomic (Room transaction).
2. After broadcast: `BROADCASTED`. Poll/stream the chain; match by account state/seqno advance and message/tx hash.
3. `CONFIRMED` only when seen on-chain. "API accepted sendBoc" ≠ confirmed.
4. `EXPIRED` only when `valid_until` has passed **and** the wallet's `seqno` did not advance for that message. Only then is re-signing a new message safe.
5. Never re-broadcast a different message with the same `seqno` while the first can still be valid.
6. On app start: run recovery — for every non-final tx, re-check the chain and update state.
7. The chain is the source of truth. Room is a rebuildable cache; deleting it must lose nothing important (except the address book unless backed up).

---

## 8. Local data (Room) — cache only

Tables (suggested):
- `tx(id, network, accountAddr, hash, seqno, direction, kind[TON|JETTON], jettonMaster?, amountUnits(TEXT), counterparty, comment?, state, validUntil, lt?, createdAt, updatedAt)`
- `balance_cache(accountAddr, network, assetId, amountUnits(TEXT), fetchedAt)`
- `address_book(id, name, address, network)`
- `settings` → DataStore, not Room.

Rules: store big numbers as TEXT (decimal string) or BLOB, never as REAL. **No secrets in any table.** Room DB is not encrypted by default — assume it is readable on a compromised device and keep it free of secrets.

---

## 9. Network layer (free-tier survival)

**Provider facts [VERIFIED, Nov 2026]:**
- TON Center v2 = HTTP proxy to liteservers (supports proofs); v3 = indexed (Jettons/NFTs/history; no proofs). Base: `toncenter.com/api/v2|v3`, `testnet.toncenter.com/api/v2|v3`. Verify endpoint paths against current docs before coding.
- TON Center limits: no key = 1 req/s; Free key = 10 req/s, 1 key/network; Plus 25 rps; Advanced 100 rps. **Limits are total per key, not per user.** A key embedded in the APK is shared by all users and extractable.
- TonAPI free default 1 rps; it offers a special token with **per-client-IP limits** suitable for client-side apps — free availability **[UNVERIFIED]**, check tonconsole.
- Public liteservers: mostly rate-limited, not recommended for production (use only as last fallback). Direct ADNL on Android **[UNVERIFIED]**.

**Design:**
1. `ProviderRouter` tries providers in order: (1) TonAPI per-IP token (if confirmed), (2) TON Center, (3) optional liteserver. On 429/5xx/timeouts: next provider + exponential backoff with jitter. Per-provider circuit breaker.
2. Cache TTLs: balance 20–30 s; account state/seqno short (and always re-fetched immediately before signing); history incremental; Jetton metadata long.
3. Fetch only when the relevant screen is open; no background polling in early phases.
4. After the user's own send: poll only that tx/account, not full history.
5. Validate every response (address format, numeric ranges). Treat balances as display values.
6. If a private backend is added later (ton-http-api / own liteserver), it must be just another provider in the router. Do **not** centralize all reads behind one proxy IP — that collapses per-IP limits into one shared limit.
7. No raw API keys in git; keep them in `local.properties`/build config; assume extractable.

---

## 10. Key storage spec (Android Keystore)

- Create an AES-256 key in `AndroidKeyStore`, purpose ENCRYPT|DECRYPT, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, non-exportable.
- Store only `IV || ciphertext` of the private key (or seed) in an app-private file/DB blob.
- `setUserAuthenticationRequired(true)` **without** a validity duration means every cipher use (even encrypt) requires a BiometricPrompt with a `CryptoObject`. Decide explicitly, and implement one of:
  - (a) authenticate at wallet creation as well, or
  - (b) use `setUserAuthenticationParameters(timeout, types)` and a short auth window.
- Handle key invalidation (user changes biometrics / removes lock screen): detect `KeyPermanentlyInvalidatedException`, force re-import via recovery phrase.
- After signing: zero `ByteArray`/`CharArray`. Use `FLAG_SECURE` on the recovery-phrase and send-confirm screens.
- Exclude secret files from backup (`allowBackup=false` + data extraction rules).
- Fallback if no secure lock screen: refuse to create a wallet.

---

## 11. Test checklist (must exist before Mainnet)

- Mnemonic → key → address vectors (12-word multichain, 24 TON, 24 multichain, ambiguous 24).
- Address parsing: raw/friendly, bounceable flags, wrong network rejected, checksum errors.
- `Money`/`BigInteger`: no precision loss; Jetton decimals ≠ 9; overflow/negative rejected.
- Bounce flag selection by destination state.
- Tx state machine: crash between each state transition → correct recovery; no double-send; expiry logic.
- Router: 429/500/timeout/garbage JSON/partial response.
- Security: no secrets in logs/DB/backups (grep + instrumentation test); release build has logging stripped; FLAG_SECURE set.
- Network separation: Testnet message cannot be valid on Mainnet (wallet_id check).
- Dependency list + licenses documented.

---

## 12. Common mistakes (do NOT do)

- Using `double` for money. Using `String` for secrets. Logging anything sensitive.
- Hardcoding `bounceable = true`. Trusting token name/logo. Treating `sendBoc` success as confirmation.
- Using `java.net.http` on Android. Shipping a shared API key as if it were secret.
- Mixing mainnet/testnet. Deriving the address from the mnemonic alone (wallet type, workchain, wallet_id matter).
- Auto-picking one account on import. Creating 24-word multichain mnemonics for new wallets.
- Adding TON4J without checking GPL-3.0 and Android compatibility.
- Building swaps/NFT/staking/TON Connect before send/receive is rock solid.
- Writing "plausible" SDK calls from memory. Verify against the real SDK.

---

## 13. Decisions already made by the human (answers)

1. **Language:** Kotlin + Jetpack Compose (fixed by the build environment). No NDK/native code: any library that needs native binaries is excluded.
2. **Open-source / license:** **NOT YET DECIDED by the human.** Until told otherwise, assume the app is **closed-source** → **do NOT use TON4J (GPL-3.0)**; use WalletKit Android (and permissively licensed libraries only). Every dependency's license must be listed. If WalletKit cannot do something and only a GPL library can, stop and ask the human.
3. **Min Android API level:** the agent decides and justifies in Phase 0 (must support Android Keystore AES-GCM, BiometricPrompt, and the chosen SDK). Report the choice to the human.
4. **Assets (v1):** native coin **GRAM** (formerly TON) and **USDT (Jetton)** only. No other Jettons in v1. The USDT master address must come from an official source and be confirmed by the human — never guessed by the agent.
5. **App lock:** **both** biometrics and PIN. Biometrics preferred; PIN as fallback and for devices without biometrics. Keystore auth design (Section 10) must support both.

---

---

## 14. First message to start with

"Read TON_WALLET_CORE_AGENT_GUIDE.md. Start **Phase 0 only**: create a minimal Android project, add TON WalletKit Android, generate a Testnet V5R1 wallet address, and show the address on a simple placeholder screen. Do not write any other phase yet. First send me your plan (max 10 lines) and wait for my go. List the exact SDK version and the real class/method names you used."

---

## 15. Project identity and code hygiene [RULE]

**Package / applicationId**
- Use exactly this value (human fills in before Phase 0): `applicationId = "<HUMAN_PROVIDES>"`, same value for `namespace` and the Kotlin package root. The agent must **not** invent or change it.
- Format rules: at least two dot-separated segments; each segment starts with a letter; only letters, digits, underscore. Must be globally unique on Google Play and **cannot be changed after publishing**.
- Do not use `com.google`, `com.android`, `org.ton`, or names containing Telegram/Tonkeeper/TON Foundation trademarks.

**No tool/assistant traces in the codebase**
- Do not write the words "AI", "Gemini", "Google AI Studio", "generated by", "assistant", "LLM", "prompt", or similar in: source comments, KDoc, identifiers, string resources, README, commit messages, manifest labels, test names, TODOs, log messages, or file headers.
- Comments are written in a neutral engineering voice, only where they explain *why* (e.g. "non-bounceable: destination uninitialized").
- No template banners, no "Auto-generated" headers, no emojis in code.
- Before finishing each phase, run a search over the whole project for: `AI`, `Gemini`, `generated`, `assistant`, `LLM`, `prompt` (case-insensitive, word match) and remove hits (except legitimate third-party library names/licenses).
- Third-party license notices required by dependency licenses must stay intact.

---

## 16. Testnet testing (mandatory before Mainnet)

**Setup**
- Build with `Network.TESTNET` as the default. Show a permanent red **"TESTNET"** banner in the UI in all testnet builds.
- Testnet endpoints: TON Center `https://testnet.toncenter.com/api/v2|v3`; explorers `testnet.tonviewer.com` / `testnet.tonscan.org`. Mainnet explorers cannot see testnet. **[VERIFIED]**
- Get test coins from the faucet bot `@testgiver_ton_bot` (about 2 GRAM per hour). Only give it a **testnet** address. **[VERIFIED]**
- A fresh wallet account shows state `uninit` (contract not deployed) until its first outgoing transaction. This is normal. **[VERIFIED]**
- **Never** import a real Mainnet recovery phrase into a testnet build. Always create a new wallet for testing.

**Checklist (mark pass/fail in each Phase Report)**

| # | Test | Phase |
|---|---|---|
| T1 | Create wallet, show phrase, backup-confirm (re-enter N random words) | 1 |
| T2 | Delete app data, restore from the phrase → **same address** | 1, 2 |
| T3 | Testnet address differs from Mainnet address for the same key | 1 |
| T4 | Import: 12-word, 24-word TON, 24-word multichain, invalid checksum, ambiguous 24-word (shows both) | 2 |
| T5 | Faucet funds arrive; balance shown; stale-cache indicator when offline | 3 |
| T6 | HTTP 429 / 500 / timeout → fallback provider, backoff, no crash | 3 |
| T7 | Receive screen address + QR valid; Mainnet address rejected in testnet build | 4 |
| T8 | Send small GRAM to second own testnet wallet → CONFIRMED | 5 |
| T9 | Send to an `uninit` address → sent **non-bounceable**, funds arrive | 5 |
| T10 | Kill app between SIGNED and CONFIRMED → on restart state recovered, **no double send** | 5 |
| T11 | Message expires (valid_until passes) → state EXPIRED only if seqno did not advance | 5 |
| T12 | History matches explorer; incremental fetch only new items | 6 |
| T13 | Test Jetton send/receive; wrong decimals rejected; unknown/fake Jetton not displayed as USDT | 7 |
| T14 | Airplane mode / bad network during every screen: no crash, clear message | 3–8 |
| T15 | Logs/DB/backups contain no secrets (grep + manual check) | all |

**Rules**
- A phase is "done" only when its checklist rows pass on a **real device** (not only the emulator).
- Testnet can be slow or unstable and may lack history. Retry once before declaring a bug; record it either way.
- Real USDT does not exist on testnet. Test the Jetton flow with a test Jetton; real USDT is verified later on Mainnet with a tiny amount.
- Mainnet only after Phase 9 (independent review), starting with a very small amount.

---

*Sources checked (Nov 2026): TON TEP-2 and TEP-3, docs.ton.org (wallets, API/rate limits, WalletKit Android), TON4J README/Maven listing, TON Center rate-limit docs. Re-verify versions, endpoints and limits before relying on them.*
