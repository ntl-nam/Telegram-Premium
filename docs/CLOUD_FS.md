# TelePremium Cloud FS

## Status

Cloud FS Pass 1 introduces an account-scoped persistent filesystem metadata layer backed by Telegram Saved Messages. It is integrated into TelePremium File Center and copied into the upstream Telegram tree by the existing Concept 5 patch pipeline.

## Invariants

- Telegram Cloud is the persistence source for committed Cloud FS metadata.
- Local application storage must never be the only copy of the hierarchy.
- Every Telegram account owns an independent Cloud FS manager.
- Folders are virtual nodes, not Telegram groups/channels.
- Every node has a stable UUID.
- Rename/move operations change metadata and never require re-uploading file bytes.
- Telegram server Premium state is unrelated to TelePremium Cloud.
- Technical Telegram IDs are not exposed in normal UI.

## Current implementation

Packages:
- `org.telegram.messenger.telepremium.cloud.CloudNode`
- `CloudSnapshot`
- `TelegramCloudMetadataStore`
- `TelePremiumCloudManager`
- `org.telegram.ui.TelePremiumCloudActivity`

Metadata records are stored in Saved Messages with the marker:

`#TELEPREMIUM_CLOUD_V1`

Bootstrap reads recent Saved Messages, validates schema and selects the highest generation. If no Cloud FS exists, a root filesystem is initialized automatically.

Current UI supports:
- automatic bootstrap;
- nested folders;
- persistent rename;
- favorite flag;
- trash state;
- account isolation;
- automatic recovery of hierarchy after clean install/login.

## Recovery model

The client never requires a local backup to rediscover the filesystem. After authentication it reads cloud metadata, selects the newest valid generation and rebuilds the in-memory projection.

Malformed metadata messages are ignored, so an older valid generation remains recoverable.

Future passes must retain multiple valid generations and may compact old generations only after a verified newer snapshot exists.

## File payload phase

File payload upload/download must use Telegram's existing FileLoader/SendMessagesHelper infrastructure. Do not introduce a second HTTP uploader.

A CloudNode file record must bind the logical node to the Telegram message/document identity. The logical display name remains independent from the original Telegram document filename.

Required follow-up implementation:
1. Android file picker and upload queue.
2. Telegram document binding.
3. Download/offline state.
4. SHA-256 deduplication.
5. restore/trash UI.
6. move/copy picker.
7. incremental journal and snapshot compaction.
8. integrity checker and deep recovery.
9. categories/search.
10. large-scale tests (10k/50k nodes).

## Acceptance tests for Pass 1

1. Create nested folders.
2. Rename a folder.
3. Kill/restart app: hierarchy remains.
4. Clear local app data, sign in again: hierarchy is recovered from Saved Messages.
5. Install on another device and sign in: hierarchy is recovered.
6. Verify Account 0 and Account 1 do not share managers or cloud metadata state.
7. Inject a malformed older metadata message: bootstrap still selects the newest valid generation.

## Security

No auth keys, session tokens, passcodes or credentials may be written to Cloud FS metadata.


## Pass 2 — payload foundation and filesystem operations

Implemented:
- file-node metadata for SHA-256, original name and offline state;
- streaming SHA-256 calculation;
- Android URI import staging without loading the whole file into memory;
- deduplication lookup by SHA-256;
- logical copy reusing an existing Telegram payload binding;
- move support through parentId mutation;
- global favorites projection;
- trash projection and restore primitive;
- transfer-state model;
- source verifier coverage for Cloud FS core.

The physical Telegram document-send binding is intentionally kept behind `CloudPayloadRepository`. It must use the upstream Telegram send/file infrastructure; no parallel HTTP transport is allowed.


## Pass 3 — Telegram payload binding

Implemented:
- Android Storage Access Framework file selection from Cloud folders;
- streaming staging and SHA-256 calculation;
- pre-upload deduplication against existing CloudNode payload bindings;
- document send through Telegram's existing `SendMessagesHelper.prepareSendingDocument` path to Saved Messages;
- confirmation through Telegram `messageReceivedByServer` before the logical file is committed;
- persistence of Telegram message/document identity in CloudNode;
- temporary staging cleanup after server confirmation;
- source-verifier enforcement of the payload path.

A logical Cloud file is therefore committed only after Telegram has acknowledged its backing document. Duplicate content can reuse an existing Telegram payload identity instead of uploading bytes again.


## Pass 3B — download/offline lifecycle

Implemented:
- resolve a cloud payload after reinstall from its persisted Telegram message identity;
- obtain the current Telegram Document instead of persisting transient file references;
- download through the upstream FileLoader;
- observe Telegram fileLoaded/fileLoadFailed notifications;
- Cloud-only, Downloading, Offline and Error states;
- File Center state badges;
- explicit Keep offline/download action;
- Release local space deletes only the local FileLoader copy and never the Telegram message/document;
- verifier coverage for the complete download/offline lifecycle.

This closes the durable round-trip: a committed file can be rediscovered from cloud metadata after login, resolved to its Telegram document and downloaded again without any local database or original filesystem path.


## Pass 4 — filesystem UX and integrity

Implemented:
- dedicated Favorites and Trash projections in File Center;
- restore from Trash;
- recursive folder destination picker;
- metadata-only Move;
- logical Copy with payload reuse;
- filename search API;
- virtual category API for images, video, audio, documents, APK and archives;
- Telegram payload reference counting primitive;
- permanent metadata deletion primitive separated from physical payload deletion;
- integrity checker for duplicate UUIDs, orphan parent links and directory cycles;
- source verifier coverage for the new filesystem layer.

### Deletion invariant
A logical CloudNode may be removed independently. A Telegram payload must not be physically deleted while another live CloudNode references the same Telegram document. This is required for deduplication and logical copies.

### Integrity invariant
The root and every active descendant must form an acyclic parent graph. Duplicate UUIDs, missing parents and cycles are treated as Cloud FS corruption and must be surfaced to recovery before destructive operations.


## Pass 5 — recovery, generations and multi-device safety

Implemented:
- operation journal model with operation UUID, node UUID, generation, client ID, timestamp and payload;
- snapshot clientId and journalGeneration fields;
- retrieval of multiple persisted Cloud FS generations;
- recovery engine selects the newest structurally valid generation;
- conflict resolver compares cloudId/generation/updatedAt;
- commits re-read remote generation before publishing a new generation;
- stale clients do not silently overwrite a newer remote generation;
- diagnostics UI exposes Cloud ID, generation, node count and integrity errors;
- verifier coverage for recovery/conflict/diagnostics.

### Commit rule
Before committing generation N+1, the client verifies the currently persisted remote generation. If another device has already advanced the cloud beyond the local base generation, the local mutation is not blindly published; the client adopts the newer state and asks the operation to be retried.

### Recovery rule
Bootstrap examines available generations and ignores structurally invalid snapshots. The highest valid generation is selected. This means a malformed latest record does not make the whole Cloud FS undiscoverable.
