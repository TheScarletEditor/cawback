# Lyr Format and Packer Spec

_Copied from the Claude Doc of the same name on Nov 4, 2026. Docs only: nothing in this file changes Cawback's code. No song lyrics are quoted here._

Nov 4, 2026

This is the build spec for Lyr v0.1 and its packer, the tool that combines a song's audio, lyrics, word timing, art and metadata into one signed .lyr file. Code should be able to build from it without asking Sam anything; where a choice was open, a default is picked and marked as a decision.

## How to use this spec

Code builds four things from this doc: the Lyr v0.1 spec and schemas, a shared Rust core library, the `lyr` command line tool, and the Lyr Packer desktop app. The player has its own spec (Lyr Player Spec) and reuses the core from this one. Words like MUST, SHOULD and MAY in the format sections carry their usual RFC 2119 meaning.

The demo only needs the CLI. The packer app and the website release come after the demo, so the critical path is sections 3 to 7 plus the CLI.

This spec replaces the v0.1 sketch in the Lyr Demo Plan. Where it differs, this doc wins. Decisions made here, with the reason for each:

| Decision | Default picked | Why |
| --- | --- | --- |
| Language for the core | Rust | One library compiles into the CLI, both desktop apps, Android later and WebAssembly later, so the packer and player can never disagree about the format. |
| App framework | Tauri 2 with a React and TypeScript front end | Small installers, Rust backend shares the core directly, desktop now and Android later. |
| Audio codec | FLAC only in v0.1 | Lossless, open, royalty free and readable everywhere. Other codecs can be added in a later minor version. |
| Media type | `application/vnd.lyr+zip` | The sketch used `application/x-lyr+zip`. The `x-` prefix is discouraged for new types, and a vendor type can be registered with IANA for free later. |
| Learning data | Not stored in the file | Personal progress (from Cawback or Lyrics) is per listener, would break the artist's signature, and leaks listening habits if a file is shared. It lives in each app's encrypted store, keyed by the song id. The `learn/` folder from the sketch is dropped. |
| Melody | Reserved, not defined in v0.1 | The `melody/` folder name is reserved so a later version can add a vocal melody track without a breaking change. |
| Timing engine | wav2vec2 speech model run locally through ONNX Runtime, with our own forced alignment code | The model is Apache 2.0 and can be shipped inside the packer. No Python needed for end users. |
| Vocal separation | An artist supplied vocal stem first; Demucs only as an optional add-on the user installs | Artists usually have their own stems, which align best. Demucs weights carry an unclear license (see Licensing), so the packer does not bundle or download them. |
| Signing | Ed25519, private keys encrypted with a passphrase using age | Small, fast, well reviewed, and easy to explain. |
| What Verified means | A valid signature from a key the listener has chosen to trust | A signature alone proves the file is unchanged since signing, not who signed it. |
| Repos | `lyr` (spec, core, CLI, packer app, shared UI) and `lyr-player` (the demo player, a desktop app styled like Cawback, as Sam confirmed on Nov 4; .lyr support in Cawback comes in a later phase), both private on GitHub for now | The packer goes public first; the player stays private until Sam decides. |

Nothing here needs an answer from Sam before Code starts. The few choices that cost money or are hers to make are listed under Licensing and legal flags.

## Architecture

_Diagram: Lyr architecture: three apps over one core. The picture is in the Claude Doc version of this spec; the text around it covers what it shows._

The CLI and the Packer app call lyr-align for timing; the player never does. The Tauri apps reach lyr-core through their Rust backends, so the front end never parses a .lyr itself.

Repo layout for `lyr`:

```
lyr/
  spec/               lyr-spec.md, schemas/*.schema.json, examples/
  crates/lyr-core/    the format library (no network, no file dialogs)
  crates/lyr-align/   timing engine
  crates/lyr-cli/     the `lyr` binary
  apps/packer/        Tauri 2 + React + TypeScript
  packages/ui/        shared React components and design tokens
  tools/              export_onnx.py, make_fixtures.rs, third-party notices script
  fixtures/           small test files that are safe to publish
  private/            gitignored: purchased songs, demo kit, keys
  docs/               user guides that become website pages
```

`lyr-player` is a separate repo that pulls `lyr` in as a git submodule at `vendor/lyr`. It uses `lyr-core` as a path dependency and `packages/ui` through a pnpm workspace. Use a Cargo workspace for the crates and pnpm for the front end packages. Pin the Rust toolchain in `rust-toolchain.toml` (stable) and Node in `.nvmrc` (current LTS).

## The .lyr container

A .lyr file is a standard zip archive, built the way EPUB is, so anyone can open it with ordinary tools and see everything inside. Nothing is encrypted or hidden.

```
song.lyr
  mimetype                  the 23 bytes application/vnd.lyr+zip, first entry, stored, no extra field
  manifest.json             what the song is and the SHA-256 of every other file
  audio/main.flac           the song, lossless
  lyrics/lyrics.json        sections, lines, words, timing, vocal parts
  art/cover.jpg             optional, JPEG or PNG
  META/signatures.json      zero or more signatures over the manifest
  melody/                   reserved for a later version, MUST be absent in v0.1
  stems/                    reserved for a later version, MUST be absent in v0.1
  ext/<reverse-domain>/     optional extensions, for example ext/com.thescarletcoder.cawback/
```

Rules for writers:

1. `mimetype` is the first entry, compression method 0 (stored), no extra field, no data descriptor, content exactly `application/vnd.lyr+zip` with no newline. This puts the type at byte offset 38, so tools can sniff it without unzipping.
2. `audio/` entries are stored (method 0), because FLAC is already compressed. JSON entries use Deflate. Art is stored.
3. Entry names are UTF-8, use forward slashes, are relative, never contain `..`, a leading `/`, a drive letter, a backslash or a control character, and are unique ignoring case.
4. No zip encryption, no symlinks, no directory entries needed (readers ignore them if present).
5. Every entry except `mimetype` and `META/signatures.json` MUST be listed in `manifest.files` with its size and SHA-256. A reader treats any unlisted entry as tampering.
6. Timestamps inside the zip are set to 1980-01-01 00:00:00 and entries are written in a fixed order (mimetype, manifest, audio, lyrics, art, ext, META). Packing the same inputs twice with the same key and the same `--created` value gives a byte-identical file.
7. Zip64 is allowed but not needed for normal songs.

Limits that readers MUST enforce, to stop zip bombs and bad files: at most 256 entries, total uncompressed size at most 2 GiB, any single JSON file at most 16 MiB, compression ratio of any Deflate entry at most 200 to 1.

Song identity. Every song gets an id from its audio: `lyr:pcm-sha256:<hex>`, the SHA-256 of the decoded samples (packed as described under Processing steps). Re-encoding the FLAC, fixing lyrics or changing metadata keeps the same id, so apps can attach a listener's own data (learning progress, play counts) to the song without storing it in the file.

Versioning. `manifest.lyr` holds the format version as `"MAJOR.MINOR"`, starting at `"0.1"`. A reader MUST refuse a major version it does not know and MUST accept a higher minor version, ignoring fields and files it does not understand (extensions still get their hashes checked). Each schema file carries its own `$id` with the version. Until 1.0, minor versions may break things; from 1.0 on, only a major bump may.

Extensions. Anything not in the core spec goes under `ext/<reverse-domain>/`, owned by whoever owns that domain. Extensions are listed in the manifest and covered by the signature like everything else. A reader that does not know an extension ignores it.

## Schemas

Three JSON files carry all the structure: the manifest, the lyrics and the signatures. Code puts these schemas in `spec/schemas/` as written, then adds `examples/` that validate. All JSON is UTF-8 without a byte order mark, written with 2-space indentation and keys in the order shown. Times are whole milliseconds from the first sample of the audio. Dates are RFC 3339 in UTC.

### manifest.json

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://thescarletcoder.com/lyr/schemas/0.1/manifest.schema.json",
  "title": "Lyr manifest",
  "type": "object",
  "required": ["lyr", "id", "created", "generator", "metadata", "audio", "lyrics", "files"],
  "additionalProperties": true,
  "properties": {
    "lyr": { "type": "string", "pattern": "^0\\.1$" },
    "id": { "type": "string", "pattern": "^lyr:pcm-sha256:[0-9a-f]{64}$" },
    "created": { "type": "string", "format": "date-time" },
    "generator": { "type": "string", "maxLength": 200 },
    "supersedes": { "type": "string", "pattern": "^sha256:[0-9a-f]{64}$", "description": "SHA-256 of the manifest this file replaces, set when lyrics or metadata were edited" },
    "metadata": {
      "type": "object",
      "required": ["title", "artists"],
      "properties": {
        "title": { "type": "string", "minLength": 1, "maxLength": 500 },
        "artists": {
          "type": "array", "minItems": 1,
          "items": {
            "type": "object", "required": ["name"],
            "properties": {
              "name": { "type": "string", "minLength": 1 },
              "role": { "enum": ["primary", "featured", "remixer", "composer", "lyricist", "producer"] }
            }
          }
        },
        "album": { "type": "string" },
        "track": { "type": "integer", "minimum": 1 },
        "disc": { "type": "integer", "minimum": 1 },
        "year": { "type": "integer", "minimum": 1000, "maximum": 9999 },
        "release_date": { "type": "string", "format": "date" },
        "label": { "type": "string" },
        "isrc": { "type": "string", "pattern": "^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$" },
        "genre": { "type": "array", "items": { "type": "string" } },
        "explicit": { "type": "boolean" },
        "copyright": { "type": "string", "description": "For example: 2022 Four Year Strong" },
        "license": { "type": "string", "description": "SPDX id, a URL, or All rights reserved" },
        "credits": {
          "type": "array",
          "items": { "type": "object", "required": ["name", "role"], "properties": { "name": { "type": "string" }, "role": { "type": "string" } } }
        },
        "links": {
          "type": "array",
          "items": { "type": "object", "required": ["url"], "properties": { "label": { "type": "string" }, "url": { "type": "string", "format": "uri", "pattern": "^https://" } } }
        }
      }
    },
    "audio": {
      "type": "object",
      "required": ["path", "codec", "sample_rate", "bits_per_sample", "channels", "total_samples", "duration_ms", "pcm_md5", "pcm_sha256"],
      "properties": {
        "path": { "const": "audio/main.flac" },
        "codec": { "const": "flac" },
        "sample_rate": { "type": "integer", "minimum": 8000, "maximum": 768000 },
        "bits_per_sample": { "type": "integer", "minimum": 16, "maximum": 32 },
        "channels": { "type": "integer", "minimum": 1, "maximum": 8 },
        "total_samples": { "type": "integer", "minimum": 1, "description": "Samples per channel" },
        "duration_ms": { "type": "integer", "minimum": 1 },
        "pcm_md5": { "type": "string", "pattern": "^[0-9a-f]{32}$", "description": "Copied from FLAC STREAMINFO, then checked by decoding" },
        "pcm_sha256": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
        "source": {
          "type": "object",
          "properties": {
            "file_sha256": { "type": "string", "pattern": "^[0-9a-f]{64}$", "description": "SHA-256 of the input FLAC as supplied" },
            "reencoded": { "type": "boolean" }
          }
        }
      }
    },
    "lyrics": {
      "type": "object",
      "required": ["path", "language", "timing"],
      "properties": {
        "path": { "const": "lyrics/lyrics.json" },
        "language": { "type": "string", "description": "BCP 47 tag, for example en" },
        "timing": { "enum": ["word", "line", "none"] }
      }
    },
    "art": {
      "type": "array",
      "items": {
        "type": "object", "required": ["path", "role", "media_type", "width", "height"],
        "properties": {
          "path": { "type": "string", "pattern": "^art/[a-z0-9_-]+\\.(jpg|png)$" },
          "role": { "enum": ["cover", "back", "artist", "other"] },
          "media_type": { "enum": ["image/jpeg", "image/png"] },
          "width": { "type": "integer", "minimum": 1, "maximum": 8192 },
          "height": { "type": "integer", "minimum": 1, "maximum": 8192 }
        }
      }
    },
    "files": {
      "type": "array", "minItems": 2,
      "items": {
        "type": "object", "required": ["path", "size", "sha256", "media_type"],
        "properties": {
          "path": { "type": "string" },
          "size": { "type": "integer", "minimum": 0 },
          "sha256": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
          "media_type": { "type": "string" }
        }
      }
    }
  }
}
```

### lyrics/lyrics.json

A song is a list of sections; a section holds lines; a line holds words. The line's `text` is the exact display text as the artist wrote it. Its words, joined with single spaces, MUST equal `text`. Punctuation stays attached to its word, so `"go,"` is one word.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://thescarletcoder.com/lyr/schemas/0.1/lyrics.schema.json",
  "title": "Lyr lyrics",
  "type": "object",
  "required": ["lyr_lyrics", "language", "parts", "sections"],
  "properties": {
    "lyr_lyrics": { "const": "0.1" },
    "language": { "type": "string" },
    "source": {
      "type": "object",
      "properties": {
        "kind": { "enum": ["official", "transcribed", "unknown"] },
        "note": { "type": "string", "maxLength": 500 }
      }
    },
    "parts": {
      "type": "array", "minItems": 1,
      "items": {
        "type": "object", "required": ["id", "name"],
        "properties": {
          "id": { "type": "string", "pattern": "^[a-z][a-z0-9-]{0,31}$" },
          "name": { "type": "string" },
          "singer": { "type": "string" }
        }
      }
    },
    "sections": {
      "type": "array",
      "items": {
        "type": "object", "required": ["id", "kind", "lines"],
        "properties": {
          "id": { "type": "string", "pattern": "^s[0-9]+$" },
          "kind": { "enum": ["intro", "verse", "pre-chorus", "chorus", "post-chorus", "bridge", "breakdown", "hook", "refrain", "outro", "other"] },
          "label": { "type": "string", "description": "As written, for example Verse 2" },
          "lines": { "type": "array", "items": { "$ref": "#/$defs/line" } }
        }
      }
    }
  },
  "$defs": {
    "line": {
      "type": "object", "required": ["id", "part", "text", "words"],
      "properties": {
        "id": { "type": "string", "pattern": "^l[0-9]+$" },
        "part": { "type": "string" },
        "text": { "type": "string", "minLength": 1 },
        "start": { "type": "integer", "minimum": 0 },
        "end": { "type": "integer", "minimum": 0 },
        "timing_source": { "enum": ["aligned", "manual", "imported", "none"] },
        "words": { "type": "array", "minItems": 1, "items": { "$ref": "#/$defs/word" } }
      }
    },
    "word": {
      "type": "object", "required": ["text"],
      "properties": {
        "text": { "type": "string", "minLength": 1, "pattern": "^\\S+$" },
        "start": { "type": "integer", "minimum": 0 },
        "end": { "type": "integer", "minimum": 0 },
        "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
        "reviewed": { "type": "boolean" }
      }
    }
  }
}
```

Rules the schema cannot express, which `lyr validate` checks: ids are unique across the file; every `part` names a declared part; when `timing` is `word`, every word has `start` and `end`; when it is `line`, every line has `start` and `end`; `end` is greater than `start`; within one part, words never go backwards in time and never overlap; lines of different parts may overlap (backing vocals over lead); `line.start` equals its first word's start and `line.end` its last word's end when words are timed; no time is past `audio.duration_ms`. `confidence` is the aligner's score and is dropped once a word is marked `reviewed`.

One timed word, as stored:

```json
{ "text": "enemy", "start": 61240, "end": 61610, "confidence": 0.94 }
```

### META/signatures.json

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://thescarletcoder.com/lyr/schemas/0.1/signatures.schema.json",
  "title": "Lyr signatures",
  "type": "object",
  "required": ["lyr_signatures", "signatures"],
  "properties": {
    "lyr_signatures": { "const": "0.1" },
    "signatures": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["alg", "key_id", "public_key", "signer", "signed_at", "manifest_sha256", "sig"],
        "properties": {
          "alg": { "const": "ed25519" },
          "key_id": { "type": "string", "pattern": "^LYR-[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$" },
          "public_key": { "type": "string", "description": "32 bytes, base64 standard alphabet with padding" },
          "signer": {
            "type": "object", "required": ["name", "role"],
            "properties": {
              "name": { "type": "string", "minLength": 1, "maxLength": 200 },
              "role": { "enum": ["artist", "label", "publisher", "distributor", "packer", "demo"] },
              "url": { "type": "string", "pattern": "^https://" }
            }
          },
          "signed_at": { "type": "string", "format": "date-time" },
          "manifest_sha256": { "type": "string", "pattern": "^[0-9a-f]{64}$" },
          "sig": { "type": "string", "description": "64 bytes, base64 standard alphabet with padding" }
        }
      }
    }
  }
}
```

## Signing and keys

### In plain words

A signing key is a pair of files. The private key is like a wax seal: only the artist should hold it, and it is locked with a passphrase. The public key is like a picture of the seal that anyone can hold and compare against. When the artist signs a .lyr, the packer presses the seal onto a fingerprint of the whole song. If anyone later changes one letter of the lyrics or one sample of the audio, the fingerprint no longer matches and every player shows the file as Unverified.

A signature proves the file has not changed since that key signed it. It does not prove who holds the key. That part comes from trust: the artist posts their key's short code (for example `LYR-7F3K-Q9M2-XB4T-H8WD`) somewhere only they control, like their website or Bandcamp bio, and listeners add that code to their player once. From then on, files signed by that key show as Verified with the artist's name.

If the private key file is lost, the artist can make a new key and post the new code; old files stay signed by the old key. If the passphrase leaks along with the file, the artist posts that the old code is retired. A signature never proves the signer owns the rights to the music.

### Three states a reader shows

| State | When | What the player says |
| --- | --- | --- |
| Verified | Every hash matches, at least one signature is valid, and its key is in the listener's trusted list | Verified, signed by {signer name} |
| Signed, unknown signer | Every hash matches and a signature is valid, but no valid signature comes from a trusted key | Signed by a key you have not trusted yet: {key code}, says it is {signer name} |
| Unverified | Any hash fails, an unlisted file is present, a signature fails, or there is no signature | Unverified: {the first reason, in plain words} |

A file with no signatures at all reads as Unverified with the reason "not signed". A file whose hashes fail is Unverified even if a signature is present, and the reason names the file that changed, for example "the lyrics changed after signing".

### Exact algorithm

Key generation: 32 random bytes from the OS (`getrandom`) form the Ed25519 seed. `key_id` is the first 10 bytes of SHA-256 over the 32-byte public key, written in Crockford base32 as 16 characters, grouped in fours after `LYR-`.

Key files (two files per key):

- `<name>.lyrkey`: an age file encrypted with a passphrase (age's scrypt recipient). The plaintext inside is JSON: `{"lyr_key":"0.1","alg":"ed25519","seed":"<base64>","public_key":"<base64>","key_id":"LYR-...","name":"...","created":"..."}`. Passphrases shorter than 12 characters are refused. `--no-passphrase` exists only for test keys and prints a warning.
- `<name>.lyrpub`: plain JSON, safe to share: `{"lyr_pubkey":"0.1","alg":"ed25519","public_key":"<base64>","key_id":"LYR-...","name":"...","url":"https://...","created":"..."}`.

What gets signed. First compute `manifest_sha256` = SHA-256 of the exact bytes of `manifest.json` as stored in the zip (no reformatting, so no canonical JSON is needed). Then build this payload as UTF-8 text with `\n` line endings and no trailing newline:

```
lyr-signature-v1
manifest-sha256:<64 hex chars>
key-id:<LYR-....-....-....-....>
signer-name-b64:<base64 of the UTF-8 signer name>
signer-role:<role>
signer-url-b64:<base64 of the url, or empty>
signed-at:<RFC 3339 UTC, second precision>
```

Sign the payload bytes with Ed25519 (pure Ed25519, RFC 8032) and store the 64-byte signature as `sig`. Because the payload includes the signer's name and role, nobody can swap the name on an existing signature.

Verification, in this order, stopping at the first failure:

1. Open the zip with the safety limits. Check `mimetype` is first, stored and exact.
2. Parse `manifest.json`, check the major version, validate it against the schema.
3. Check that the set of zip entries equals `manifest.files` plus `mimetype` and `META/signatures.json` (if present). Any extra or missing entry fails.
4. Hash every listed entry and compare size and SHA-256.
5. Validate `lyrics.json` against its schema and the extra rules.
6. Decode the FLAC and compare its PCM MD5 and PCM SHA-256 with the manifest. This step is skippable with `--fast` for quick listing, but players MUST run it before showing Verified.
7. For each signature: recompute `manifest_sha256`, check it matches the stored value, rebuild the payload, check the Ed25519 signature against `public_key`, and check `key_id` matches `public_key`.
8. Pick the state from the table above.

Editing and re-signing. When lyrics or metadata change, the tool writes a new manifest with `supersedes` set to `sha256:<old manifest_sha256>`, drops every old signature (they no longer cover the content) and asks for a key to sign again. The tool never keeps a stale signature in the file.

Trusted keys on a listener's device. Readers keep a trusted list of public keys, added by importing a `.lyrpub` file or typing a key code plus the public key. The list is local; nothing is looked up online. Key revocation is out of scope for v0.1; the spec says so plainly.

## Inputs

The packer takes one folder per song. Only the audio and the lyrics are required.

| File | Required | What it is |
| --- | --- | --- |
| `track.flac` (any `*.flac` if there is exactly one) | Yes | The master or the purchased download, untouched |
| `lyrics.txt` or `lyrics.json` | Yes | Plain lyrics in the format below, or an already timed Lyr lyrics file |
| `meta.json` | No, but the packer asks for title and artist if missing | Fields of `manifest.metadata`. Any field in the FLAC's own tags fills a gap, never overrides |
| `cover.jpg` or `cover.png` | No | Falls back to the FLAC's embedded front cover picture if present |
| `vocals.flac` or `vocals.wav` | No | An isolated vocal stem of the same length. Used only for timing, never stored in the .lyr |
| `timing.lrc` | No | Line timing, or word timing in enhanced LRC (`<mm:ss.xx>` tags). Used instead of automatic timing |
| A key | Only to sign | Passed with `--key`, never kept in the song folder |

The packer never writes into the input folder unless `--out` points there.

### lyrics.txt format

Plain text, UTF-8. The packer keeps every word exactly as typed. It only converts line endings, trims spaces at the ends of lines, collapses runs of spaces inside a line, and applies Unicode NFC normalization. It never fixes spelling or capitals.

```
[Verse 1]
The kettle sings at half past nine
The window holds a line of rain

[Chorus]
We keep the light on
{backing} (keep it on, keep it on)

# this line is a comment and is ignored
[Chorus]
...
```

Rules:

1. A line that is only `[Label]` starts a section. The kind comes from the label's first word, matched case-insensitively against intro, verse, pre-chorus, chorus, post-chorus, bridge, breakdown, hook, refrain, outro; anything else is `other`, and the label is kept as written.
2. A blank line also starts a new section if the next line is not a `[Label]` line (kind `other`, no label). Lyrics with no labels at all become one section per blank-line block.
3. A line that starts with `{part}` and a space belongs to that vocal part. Part ids are lowercased, for example `{backing}`, `{scream}`, `{guest}`. Lines without a tag are `lead`. The packer declares each part it sees, naming it from the id (`backing` becomes Backing vocal).
4. A line starting with `#` is a comment. To start a lyric with `#` or `[` or `{`, put a backslash before it.
5. `...` alone on a line is kept as a lyric line only if escaped; unescaped it is ignored. (Some lyric sites use it as a placeholder.)
6. Words are split on spaces. Hyphenated words and contractions stay one word.
7. Repeated choruses must be written out in full. Notes like `[Chorus x2]` are kept as labels but do not repeat lines, and the packer warns, because the aligner needs every sung line to be written.

### meta.json example

```json
{
  "title": "Enemy Of The World (Re-Recorded)",
  "artists": [{ "name": "Four Year Strong", "role": "primary" }],
  "album": "Enemy of the World (Re-Recorded)",
  "track": 1,
  "year": 2022,
  "copyright": "2022 Four Year Strong",
  "license": "All rights reserved",
  "links": [{ "label": "Bandcamp", "url": "https://fouryearstrong.bandcamp.com" }]
}
```

The track number and year above are placeholders for the example; Code fills real values from the FLAC tags.

## Processing steps

`lyr pack` runs these steps in order. Each step reports progress, can fail with a coded error (see Errors and validation), and writes nothing final until the last step passes. The output is written to a temp file in the target folder and renamed into place at the end.

### 1. Check the audio

1. Read the FLAC with `claxon`. Refuse anything that is not FLAC (E101).
2. Read STREAMINFO: sample rate, bits per sample, channels, total samples, PCM MD5. Refuse 8-bit, more than 8 channels or a zero total (E103).
3. Decode every sample. Compute the MD5 the FLAC way (each sample as signed little-endian, using bytes per sample = bits rounded up to whole bytes, channels interleaved) and compare with STREAMINFO. A mismatch means the file is damaged (E102). If STREAMINFO's MD5 is all zeros (some encoders skip it), warn and carry on.
4. Compute `pcm_sha256` over the same byte stream. This gives the song id.
5. Hash the input file as supplied (`source.file_sha256`).

### 2. Encode the audio

1. Re-encode the decoded samples with `flacenc` at its strongest settings (largest block search, highest LPC order it supports).
2. Decode the re-encode and confirm its PCM MD5 matches step 1. If not, discard it (this would be a bug; log it).
3. Build a clean copy of the original: same audio frames, with VORBIS\_COMMENT, PICTURE, APPLICATION and PADDING blocks removed and a SEEKTABLE added (one point every 10 seconds). Keep STREAMINFO.
4. Store whichever of the two is smaller as `audio/main.flac`. Set `source.reencoded` accordingly. `--no-reencode` skips 1 and 2.

The point: the song inside is always bit-identical to the master, and never larger than it needs to be. Tags move into the manifest so there is one source of truth.

### 3. Read the lyrics

Parse `lyrics.txt` with the rules in Inputs, or load `lyrics.json` and validate it. Assign ids in reading order (`s1`, `s2`; `l1`, `l2`). Language comes from `--lang`, then `meta.json`, then defaults to `en` with a warning. Mark `source.kind` as `official` only when the user passes `--official` (the GUI asks).

### 4. Time the lyrics

Choose the timing source, first match wins:

1. `lyrics.json` already timed: keep it.
2. `timing.lrc` present or `--timing <file.lrc>`: import it (see LRC import below).
3. `--timing none`: store lyrics untimed (`timing: none`).
4. Otherwise run the timing engine (`--timing auto`, the default). If the model is not installed, stop with E301 and tell the user how to install it or pass `--timing none`.

The timing engine (`lyr-align` crate):

1. Vocal input. Use `vocals.flac` if supplied. Else, if `--separate demucs` is given, run `demucs --two-stems=vocals -o <tmp> <input>` as an external program the user installed, and read the vocals stem. Else use the full mix and warn that timing will be rougher.
2. Prepare audio: downmix to mono, resample to 16 kHz with `rubato`, normalize to peak 0.95.
3. Emissions: run the wav2vec2 base 960h CTC model (ONNX, through the `ort` crate) in 30 second windows with 2 second overlap; keep the middle of each window when stitching. Output is one probability row per 20 ms frame over the model's 32 tokens.
4. Text for alignment: for each word, make an alignment form: uppercase, curly apostrophes to straight, `&` to AND, digits spelled out in English (`24` to TWENTY FOUR, `4th` to FOURTH), every other character not in the model's alphabet dropped. Keep a map from alignment tokens back to the original word. Words that end up empty (`--`, a lone emoji) are marked for interpolation.
5. Forced alignment: a standard CTC Viterbi over the token sequence with blanks between tokens and the word separator `|` between words, in log space. Write it ourselves (about 150 lines with tests); do not depend on torchaudio. Between lines, allow an optional filler state whose score per frame is the frame's best non-blank score minus a fixed penalty (start at 4.0, tune on fixtures) so ad-libs, screams and unwritten parts do not drag the alignment.
6. Word times: start = first frame of the word's first token, end = last frame of its last token plus one, times 20 ms. Confidence = the mean of the per-frame probabilities along the word's path. Interpolated words share time evenly between their neighbours and get confidence 0.
7. Clean-up: within each part, force times to be non-decreasing; set a minimum word length of 60 ms; clamp everything to the audio length. Line start and end come from their words. Set `timing_source: aligned`.
8. Review list: every word under confidence 0.5, every line longer than 15 seconds, and every gap over 8 seconds inside a section. The report prints these as `mm:ss.mmm  line text  [word]`.

Be honest in the docs about accuracy: a speech model aligning sung vocals gets most lines right and many words right, especially with a clean vocal stem, but fast or screamed parts need a hand pass in the editor. Every demo song gets that pass.

Model files. `tools/export_onnx.py` (run once, by Code, with Python, PyTorch and Hugging Face `transformers` installed in a throwaway virtual env) exports `facebook/wav2vec2-base-960h` to ONNX with opset 17, plus an int8 dynamically quantized copy, and writes `vocab.json`, `LICENSE` (Apache 2.0) and a `NOTICE` with the source and a SHA-256 of each file. Code measures both on the fixtures and ships int8 unless its median word boundary error is more than 10 ms worse than the full model. The result is the timing model pack `lyr-model-en-wav2vec2-base-1.zip`. The CLI looks for it in the platform data folder (`dirs` crate: for example `%APPDATA%\lyr\models` on Windows) or at `--model <path>`, and checks the hashes in `NOTICE` before loading.

LRC import. Parse `[mm:ss.xx]` line tags and enhanced `<mm:ss.xx>` word tags. Match LRC lines to lyric lines in order using text similarity (normalized Levenshtein, threshold 0.6); unmatched lines are reported. Line tags give line starts, and each line ends where the next begins or at most 10 seconds later. Word tags give word starts and ends. Set `timing_source: imported`.

### 5. Build the manifest

Fill `lyr`, `id`, `created` (now, or `--created` for reproducible builds), `generator` (`lyr <version>`), metadata, audio fields, lyrics fields, art (read width and height with the `image` crate; JPEG and PNG only; refuse images over 8192 px on a side or over 20 MB) and `files` with size, SHA-256 and media type for every entry. Validate against the schema.

### 6. Sign

If `--key` is given, unlock it (passphrase from the terminal prompt, or `LYR_PASSPHRASE` for scripts, documented as less safe), and add one signature as in Signing and keys. `--signer-name` and `--signer-role` default to the key's name and `artist`. Without a key, write the file unsigned and say so in the report.

### 7. Write the container

Write entries in the fixed order with the rules in The .lyr container. Then fsync and rename the temp file into place. Refuse to overwrite an existing file without `--force` (E501).

### 8. Verify itself

Re-open the written file with the full verification from Signing and keys, including the PCM decode. If anything fails, delete the output and exit with E601. Then print the report.

The report (`--report json` for machines) contains: output path and size; audio format and duration; sizes of audio, lyrics, art and everything else, with percentages; whether the audio was re-encoded and how many bytes that saved; lyric counts (sections, lines, words, parts); timing source, mean confidence and the review list; signature state; and the song id.

## CLI and GUI

### Commands

One binary, `lyr`, built with `clap`. Every command takes `--json` for machine output, `--quiet`, and `--verbose`. Help text is written for a musician, not a programmer.

| Command | What it does |
| --- | --- |
| `lyr keygen --name <name> [--url <https url>] [--out <dir>]` | Makes `<name>.lyrkey` and `<name>.lyrpub`, prompts for a passphrase twice, prints the key code and a short note on backing up the private key |
| `lyr key show <file.lyrpub or .lyrkey>` | Prints name, key code and public key |
| `lyr pack <song-folder> [-o out.lyr] [--key k.lyrkey] [--timing auto, none or <file.lrc>] [--vocals <file>] [--separate demucs] [--lang en] [--official] [--no-reencode] [--created <time>] [--force] [--report json]` | Runs every processing step |
| `lyr verify <file.lyr> [--trust <dir or .lyrpub>]... [--fast]` | Full verification; prints the state and reasons |
| `lyr inspect <file.lyr>` | Prints manifest summary, sizes, parts, timing coverage, signatures, without the full decode |
| `lyr validate <file.lyr or .json>` | Schema and rule checks only |
| `lyr sign <file.lyr> --key <k.lyrkey> [--signer-name] [--signer-role]` | Adds a signature to an unchanged file (a label countersigning, for example) |
| `lyr update <file.lyr> [--lyrics <lyrics.json or .txt>] [--meta <meta.json>] [--cover <img>] --key <k.lyrkey> [-o out.lyr]` | Replaces parts, sets `supersedes`, drops old signatures and re-signs |
| `lyr unpack <file.lyr> <dir>` | Extracts safely (no paths outside `<dir>`) |
| `lyr export-lrc <file.lyr> [--enhanced] [-o file.lrc]` | Writes LRC for older players; `--enhanced` adds word tags |
| `lyr align <audio.flac> <lyrics.txt> [--vocals <file>] -o lyrics.json` | Timing only, for testing or for other tools |
| `lyr model status` and `lyr model install <zip>` | Shows or installs the timing model pack from a file the user downloaded |

Exit codes: 0 success or Verified; 10 Signed by an unknown signer; 20 Unverified; 30 invalid or unreadable file; 40 bad arguments; 50 missing timing model; 60 key or passphrase problem; 70 I/O error; 1 anything else. `lyr verify` uses 0, 10 and 20 so scripts can tell the states apart.

The CLI never touches the network. It has no update check.

### The Lyr Packer app

A small Tauri 2 app over the same core, for artists who do not use a terminal. Same design system as the player: Cawback's ScarletTokens v1 colors in both themes, with Fraunces, Inter and the raven copied from the website repo (see the Lyr Player Spec's Visual design). Five steps in a left rail, each can be revisited:

1. Song. Drop a folder or files. Shows an audio card (format, length, the PCM check with a tick) and the cover. Missing pieces show as empty drop zones.
2. Lyrics. Paste or drop text. Live preview on the right shows sections, lines and parts as the parser sees them, with warnings inline (for example a `[Chorus x2]` note). A checkbox: these are the official lyrics.
3. Details. A form for the metadata, prefilled from the FLAC tags.
4. Timing. Three choices: automatic (with an optional vocal stem drop zone), import an LRC file, or no timing. Automatic shows step progress and, at the end, the review list.
5. Review and sign. The timing editor from `packages/ui`: lyric lines on the left, a waveform strip with word blocks on the right. Click a line to play from it. Drag a word block's edges, or select a word and use `[` and `]` to nudge start and `{` and `}` to nudge end by 10 ms (hold Shift for 50 ms), `E` to edit its text, `R` to mark reviewed. Low-confidence words are outlined in scarlet until reviewed. Then choose a key (or make one in a guided sheet that explains keys in the plain words above and shows the key code to post), and Save as. The app runs verify and shows the report as a card.

A separate Keys screen lists keys, shows each key code large enough to read aloud, exports the `.lyrpub`, and copies a ready-to-post line such as "Our Lyr signing key: LYR-7F3K-Q9M2-XB4T-H8WD".

Opening an existing .lyr in the app shows the verify result and lets the owner edit lyrics or timing and re-sign (the same as `lyr update`).

The app has no network permission: Tauri's HTTP plugin is not included, the content security policy sets `connect-src 'none'`, and there is no updater. A Help menu item opens the website's docs page in the system browser only when clicked.

## Libraries and licenses

Every dependency below is permissive (MIT, Apache 2.0, BSD or ISC) or, for fonts, the SIL Open Font License, so the packer can be given away and its source published under MIT OR Apache 2.0. Licenses were read from crates.io and Hugging Face on 2026-11-04; Code re-checks with `cargo deny` in CI, which fails the build on any GPL, AGPL, LGPL, SSPL or unknown license.

| Job | Library | License |
| --- | --- | --- |
| FLAC decode | `claxon` 0.4 | Apache 2.0 |
| FLAC encode | `flacenc` 0.5 | Apache 2.0 |
| FLAC metadata blocks | `metaflac` 0.2, or a small hand-written block rewriter | MIT |
| Zip read and write | `zip` | MIT |
| SHA-256, MD5 | `sha2`, `md-5` (RustCrypto) | MIT OR Apache 2.0 |
| Signing | `ed25519-dalek` 3 | BSD 3-Clause |
| Key file encryption | `age` 0.12 (scrypt passphrase recipient) | MIT OR Apache 2.0 |
| Passphrase prompt, memory wiping | `rpassword`, `zeroize` | Apache 2.0, Apache 2.0 OR MIT |
| Random bytes | `getrandom` | MIT OR Apache 2.0 |
| Base32 and base64 | `base32` (Crockford alphabet), `base64` | MIT OR Apache 2.0 |
| JSON and schema checks | `serde`, `serde_json`, `jsonschema` | MIT OR Apache 2.0, MIT |
| Text | `unicode-normalization`, `strsim` | MIT OR Apache 2.0, MIT |
| Images | `image` (JPEG and PNG features only) | MIT OR Apache 2.0 |
| Resampling | `rubato` 5 | MIT OR Apache 2.0 |
| Model runtime | `ort` 2 with ONNX Runtime | MIT OR Apache 2.0; ONNX Runtime is MIT |
| Timing model | `facebook/wav2vec2-base-960h` exported to ONNX | Apache 2.0, ship its LICENSE and NOTICE |
| CLI, paths, errors | `clap`, `dirs`, `thiserror`, `anyhow` | MIT OR Apache 2.0 |
| Desktop app | Tauri 2, React, Vite, TypeScript | MIT OR Apache 2.0, MIT, MIT, Apache 2.0 |
| Fonts | Fraunces, Inter, bundled as files | SIL OFL 1.1 |
| Optional vocal separation | Demucs, installed by the user, never bundled | Code is MIT; weights unclear (see legal flags) |

If `flacenc` cannot match the original's size on the fixtures, keep the stripped original (step 2 already does) and note it; switching to \`libflac-sys\` (BSD 3-Clause) is allowed only if the original is often more than 2 percent smaller, since it adds a C build to every platform. The third-party notices file is generated with `cargo about` for Rust and `license-checker` for the front end, and ships inside every download.

## Errors and validation

Every failure has a code, a one-line message in plain words, and a fix hint. The core returns typed errors (`thiserror`); the CLI and apps only format them. Warnings never stop a pack.

| Code | Message | Fix hint |
| --- | --- | --- |
| E101 | This isn't a FLAC file | Export or download the song as FLAC |
| E102 | The FLAC file is damaged: its audio doesn't match its own checksum | Download it again |
| E103 | This FLAC format isn't supported (8-bit, over 8 channels, or empty) | Re-export at 16 or 24 bit |
| E104 | The vocal stem is a different length from the song | Export the stem from the same session, full length |
| E201 | No lyrics found | Add lyrics.txt or lyrics.json |
| E202 | The lyrics file isn't valid UTF-8 | Save it as UTF-8 |
| E203 | lyrics.json doesn't match the schema | The message names the field |
| E204 | Timed lyrics break a rule | The message names the line and the rule |
| E301 | The timing model isn't installed | Run `lyr model install`, or pack with `--timing none` |
| E302 | Timing failed: the lyrics and the singing don't line up | Check the lyrics are complete and in order, or supply a vocal stem |
| E303 | Demucs was requested but isn't installed | Install it yourself or supply a vocal stem |
| E304 | The LRC file couldn't be matched to the lyrics | The message lists unmatched lines |
| E401 | Wrong passphrase | Try again; there is no recovery |
| E402 | The key file is damaged or isn't a Lyr key | Use your backup |
| E403 | Passphrase too short | Use at least 12 characters |
| E501 | The output file already exists | Choose another name or add `--force` |
| E502 | Couldn't write the output | The message names the folder |
| E601 | The packed file failed its own check | This is a bug; the report is saved next to the song for a bug report |
| E701 | Not a .lyr file, or damaged | Get a fresh copy |
| E702 | Made by a newer Lyr version this tool can't read | Update the tool |
| E703 | The file has something inside that isn't listed | Treat it as tampered |
| E704 | The file is too big or unpacks too large | Treat it as unsafe |

Warnings (W codes) cover: STREAMINFO MD5 missing (W101), using the full mix for timing (W301), low-confidence words found (W302), `[Chorus x2]` style notes (W201), language defaulted to English (W202), file left unsigned (W401).

Reader conformance rules, for anyone building a Lyr reader (these go in the spec as a checklist):

1. MUST run the verification order in Signing and keys before showing Verified.
2. MUST refuse an unknown major version and accept higher minor versions.
3. MUST enforce the container limits and path rules before extracting anything.
4. MUST treat unlisted entries as tampering.
5. MUST NOT show Verified for a valid signature whose key the user has not trusted.
6. MUST NOT fetch anything over the network to verify a file.
7. SHOULD play the audio bit-identically up to the output device and SHOULD say when it resamples.
8. SHOULD show the signer's name together with the key code, never the name alone, unless the key is trusted.
9. MAY ignore extensions it does not know, but MUST still check their hashes.

Golden rule for the code: the same `verify()` function in `lyr-core` serves the CLI, the packer app and the player. No app gets its own shortcut.

## Test fixtures and tests

The public repo holds only fixtures that are safe to publish: generated audio, Sam's own words, or openly licensed clips with attribution. Purchased songs and their lyrics live in `private/`, which is gitignored and never used by CI.

`tools/make_fixtures.rs` (a small Rust binary) generates most fixtures, so they are reproducible:

| Fixture | Contents | Used for |
| --- | --- | --- |
| `tone-16-44k.flac` | 3 s, 440 Hz sine, 16-bit, 44.1 kHz, stereo | Container, hashing, re-encode, bit-perfect checks |
| `tone-24-96k.flac` | 3 s, 24-bit, 96 kHz | High resolution path |
| `tone-tagged.flac` | Same as the first, with tags, embedded cover and padding | Tag stripping, cover fallback |
| `tone-no-md5.flac` | STREAMINFO MD5 zeroed | W101 |
| `tone-corrupt.flac` | One frame's bytes flipped | E102 |
| `clicks-120bpm.flac` with `clicks.lyrics.json` | 20 s of clicks, one per "word", times known exactly | Player sync tests and LRC export round trips |
| `speech-librispeech.flac` with its transcript | About 15 s from the LibriSpeech dev-clean set (CC BY 4.0), attribution in `fixtures/ATTRIBUTION.md` | Timing engine regression |
| `lyrics/*.txt` | Hand-written cases: no labels, every section kind, parts, comments, escapes, curly quotes, numbers, emoji, very long lines, `[Chorus x2]`, empty file | Parser |
| `bad/*.lyr` | mimetype not first, mimetype compressed, missing manifest, unlisted entry, hash mismatch, bad signature, wrong key id, unknown major version, `../` path, duplicate names differing in case, zip bomb (small file that inflates past the limit) | Every E7xx path and every Unverified reason |
| `keys/test.lyrkey` and `.lyrpub` | Test key with the passphrase `correct horse battery staple` written in the README | Signing tests only, clearly named as a test key |

Optional, recommended later: a 30 s recording of Sam singing a public domain song (for example Amazing Grace) as `sung-public-domain.flac`. It would make the timing tests far more realistic. Code should not block on it.

Tests Code writes:

1. Unit tests in each crate: parser cases, schema validation, Crockford key ids, payload bytes exactly as specified (a golden payload file), CTC Viterbi on toy emissions with a known best path, number spelling.
2. Round trips: pack then unpack gives back identical audio PCM, identical lyrics JSON and identical metadata; `export-lrc` then pack with `--timing` that LRC gives the same line starts within 10 ms.
3. Tamper tests: for every byte range in a packed fixture (sampled, not exhaustive: 500 random single-byte flips), verify is never Verified.
4. Reproducibility: packing twice with the same inputs, key and `--created` gives identical bytes.
5. Timing regression: aligning the LibriSpeech clip gives word starts within 40 ms of the stored golden timings for at least 90 percent of words. The golden file is the first output after a hand check, stored in the repo.
6. Fuzzing: a `cargo fuzz` target on the container reader and the lyrics parser, run for 10 minutes in a nightly CI job.
7. CLI tests with `assert_cmd`: every command, every exit code, `--json` output parses.
8. Cross-platform: CI runs the test suite on Windows, macOS and Linux.

## Packaging and release

Releases are built by GitHub Actions on a version tag (`v0.1.0`). The installers bundle the timing model, so each one is around 100 MB or more, and thescarletcoder.com runs on Cloudflare Pages, which refuses any single file over 25 MiB. So the installers are attached to a GitHub Release on the `lyr` repo once it is public, and the website links to them with their checksums. Until the repo is public, Sam shares builds by hand. (Cawback and Keyward are small enough to sit in the site's own `/downloads`; the packer is not.)

What each release contains:

| Download | Platforms | Approximate size |
| --- | --- | --- |
| Lyr Packer app installer | Windows x64 (NSIS `.exe`), macOS universal (`.dmg`), Linux x64 (`.AppImage` and `.deb`) | about 110 MB, timing model included |
| `lyr` CLI | Same platforms plus Linux arm64, as `.zip` or `.tar.gz` | about 10 MB |
| Timing model pack | One zip for all platforms | about 95 MB (int8) |
| `SHA256SUMS` and `SHA256SUMS.minisig` | All | small |

Sizes are estimates for the download page copy; Code fills real numbers from the build.

Build details:

1. One workflow with a matrix of `windows-latest`, `macos-14` (builds universal with both Rust targets) and `ubuntu-22.04`. Tauri's own action builds the app; `cargo build --release --locked` builds the CLI.
2. The model pack is built once by `tools/export_onnx.py` on a maintainer machine, committed to a separate private release, and its SHA-256 pinned in the repo. CI downloads it from there with a repo token; nothing at runtime downloads it.
3. `cargo deny check` and the third-party notices step run before any build.
4. Checksums are signed with minisign (ISC license, widely used). The minisign public key is printed on the download page and in the README. Users can check a download with `minisign -Vm <file> -P <key>` or compare the SHA-256 by eye.
5. Code signing. Windows SmartScreen warns on unsigned apps and macOS Gatekeeper blocks unsigned, un-notarized apps by default. Signing costs money (an Apple Developer membership and a Windows signing service), so it is Sam's call. Default until she decides: ship unsigned, and put clear steps on the download page (Windows: More info, then Run anyway; macOS: right-click, Open; or use the CLI). The workflow has signing steps written but switched off unless the signing secrets exist (map them to env vars and test the env var in each step's condition).
6. No auto-updater. The app shows its version in About; the website lists the latest version and a changelog.

Website download page (content Code drafts in `docs/website/download.md` for Sam to place as src/pages/tools/lyr.astro in the website repo, built like the existing Keyward page, with the version and SHA-256 kept in src/consts.ts the way Keyward's are): one line on what Lyr is, the three platform buttons, version and date, size, checksum, how to verify, what the unsigned warning looks like and why, system requirements (Windows 10 or later, macOS 12 or later, a 64-bit Linux with WebKitGTK 4.1), privacy line (no network, no accounts, no telemetry), license, and links to the guides.

## Licensing, legal flags and docs

### Licenses to use

| What | License | Why |
| --- | --- | --- |
| The format spec text (`lyr-spec.md`) | CC BY 4.0 | Anyone may copy and build on it with credit, the way open specs usually work |
| JSON schemas and examples | CC0 1.0 | So they can be pasted into any codebase with no strings |
| `lyr-core`, `lyr-align`, `lyr` CLI, Lyr Packer app, `packages/ui` | MIT OR Apache 2.0 (dual, the Rust norm) | Lets any player, including other people's, adopt the format. Apache 2.0 adds a patent grant |
| A royalty-free promise | A short `PATENTS.md` | States that Sam will not assert any patent she holds against implementations of the spec. Adoption depends on that promise |
| The Lyr name, the raven and The Scarlet Coder marks | Kept by Sam, not licensed | Others can implement the format but cannot call a fork the official Lyr app |

The player's license is decided later, when it leaves its private repo. If Sam wants forks of her apps to stay open, GPL 3.0 is the usual choice for an app; the core stays permissive either way.

### Legal flags

1. Lyrics and audio copyright. The packer ships no lyrics and no music. Users supply their own and must hold the rights to share what they pack. The README, the app's first run screen and the download page say so in one plain sentence. The demo's Four Year Strong files and lyrics stay in `private/` and on Sam's devices; they are shown to the band, not shared.
2. A signature is not proof of ownership. Docs and the UI say "signed by this key", never "owned by". The demo signs with Sam's own key, named as a stand-in for the band (see the player spec), so nobody is misled.
3. Demucs weights. The Demucs code is MIT and its Hugging Face model card says MIT, but people on that page question whether the weights were meant to be MIT, citing earlier statements that they were for research and the MUSDB training data terms. So the packer never bundles or downloads Demucs; users who choose it install it themselves. The artist's own vocal stem is the recommended input anyway.
4. LRCLIB. The comparison data is user-contributed lyrics owned by their rights holders. It is fetched only for the private demo, never packed into a .lyr and never published.
5. Name and extension. Before the public launch, run a trademark search on "Lyr" in software and music (as Sam did for the raven) and check that `.lyr` is not in common use by another popular program. Not a blocker for building.
6. Encryption in a public download. The tool uses standard, published algorithms (Ed25519, scrypt, ChaCha20-Poly1305 through age) and its source will be public. Open source tools like this are generally treated as publicly available under US export rules, but it is worth a one-time check before charging money for anything that includes it.
7. Third-party notices ship in every download, including the Apache 2.0 NOTICE for the timing model.

### Docs a stranger needs

Code writes these in `docs/`, short and in plain words, each usable as a website page:

1. README: what Lyr is in three sentences, a screenshot, install, the five-line quick start, links.
2. Quick start: make a key, pack a song, verify it, play it.
3. Making your first .lyr (the app, with screenshots).
4. Writing lyrics.txt (the format rules, with examples).
5. Keys, explained (the plain-words section above, plus backup advice: keep two copies of the `.lyrkey` offline, and a passphrase manager).
6. What Verified means, and what it does not.
7. Using the CLI (reference generated from `clap`, plus recipes such as packing an album with a shell loop).
8. Troubleshooting (the error catalog with fixes).
9. Privacy: the tool makes no network connections, collects nothing, needs no account.
10. The spec itself, the schemas, and a conformance checklist for people writing their own reader.
11. CONTRIBUTING, SECURITY (how to report a flaw privately, using an email address Sam chooses), CHANGELOG, LICENSE files, THIRD-PARTY-NOTICES.

## Acceptance criteria and build order

### Done means

- [ ] `spec/lyr-spec.md` covers every rule in this doc; the three schemas validate every example and every file the core writes.
- [ ] `lyr pack` turns each of the four demo song folders into a .lyr that `lyr verify` reports as Verified (with the demo key trusted) and as Signed by an unknown signer (without it).
- [ ] The audio inside each demo .lyr decodes to PCM whose SHA-256 equals the PCM SHA-256 of the purchased FLAC.
- [ ] Each demo .lyr is no larger than the purchased FLAC plus 200 KB.
- [ ] Changing any single byte of a packed file, or adding a file, never yields Verified (tamper test passes).
- [ ] Packing twice with the same inputs, key and `--created` gives byte-identical files.
- [ ] Automatic timing runs offline on Sam's Windows laptop in under 2 minutes for a 4 minute song, CPU only, and writes a review list.
- [ ] The LibriSpeech timing regression passes (90 percent of word starts within 40 ms).
- [ ] Every error code in the catalog is reachable by a test and prints its fix hint.
- [ ] With the network disabled, every command and the packer app work fully; a firewall log shows zero connection attempts during a full pack.
- [ ] CI is green on Windows, macOS and Linux, including `cargo deny`.
- [ ] Installers for all three platforms install, run, pack a fixture and uninstall cleanly.
- [ ] The docs in the list above exist and a person who has never seen Lyr can make and verify a .lyr from the quick start alone.

### Build order for Code

Each step is sized for one Code session and ends with passing tests and a commit. Steps 1 to 7 are on the demo's critical path; the player spec's first steps can start after step 3.

1. Spec and fixtures. Repo skeleton (Cargo and pnpm workspaces, CI with tests and `cargo deny` on three platforms), `spec/lyr-spec.md`, the three schemas with examples, `tools/make_fixtures.rs` and the generated fixtures.
2. Container and manifest. `lyr-core`: zip read and write with all the rules and limits, manifest model, file hashing, schema validation, the bad-file fixtures, typed errors.
3. Keys and signing. Key generation, age-encrypted key files, key codes, the signature payload, verify with the three states, trusted-key list type. Golden payload test and tamper test.
4. Audio. FLAC decode with the MD5 check, PCM SHA-256, re-encode, tag strip with seek table, keep the smaller.
5. Lyrics. The lyrics.txt parser, lyrics.json model and rule checks, LRC import and export.
6. CLI. Every command except `align`, exit codes, report, `--json`, CLI tests. Pack the four demo songs untimed with the demo key and hand them to the player work.
7. Timing. `tools/export_onnx.py`, `lyr-align` (preprocessing, ONNX emissions, text normalization, Viterbi with filler, confidence, review list), the optional Demucs hook, `lyr align` and `lyr model`, the LibriSpeech regression. Run it on the four demo songs and repack them. Budget two sessions; this is the riskiest step.
8. Packer app, part one. Tauri shell with the content security policy and no network permission, design tokens and fonts from `packages/ui`, the Song, Lyrics and Details steps.
9. Packer app, part two. Timing step and the review editor (shared with the player's artist mode), keys screen, save and report.
10. Release. Release workflow, installers, checksums and minisign, notices, the docs set and the website download page draft.
11. Later, optional: a WebAssembly build of the core for a no-install web packer that runs entirely in the browser, without automatic timing at first.
