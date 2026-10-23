# Documentation index

| Folder | File | What it is |
|---|---|---|
| `project/` | `DECISIONS.md` | Every design decision (D-01…D-89, D-M01…), the open owner items (O-*) and the build-guard log |
| | `MERGE_MAP.md` | How each file of the March app was ported, adapted, converted or dropped into `:market` |
| | `MISSING_COPY.md` | Wording keys the code wants but the content JSON does not define yet |
| | `ROADMAP.md` | What comes next, in order |
| `release/` | `RELEASE.md` | Build, sign, upload, staged rollout, rollback |
| | `STORE_LISTING.md` | Google Play listing (fa/en), privacy policy text, Data safety and policy answers |
| | `RELEASE_NOTES.md` | User-facing notes per build |
| `testing/` | `TESTING.md` | Test strategy, suites, results, release smoke S1–S18 |
| | `MANUAL_TEST.md` | Full device acceptance script (F1–F10, A1–A12, M*-*, AU-*) |
| `audit/` | `AUDIT.md` | Findings register (P0–P3, CVSS/CWE, file:line), health score, verdict |
| | `play_policy_report.md` | Play policy review of permissions and data safety |
| | `sbom.txt` | Runtime dependency list (SBOM) scanned on OSV |
| `reference/` | `march_strings_en.json` | March's English strings, kept for reference only (not packaged, D-M11) |

The change history is `../CHANGELOG.md`. The prompts and specifications the app was built from are in
`../prompts/` and `../spec/`.
