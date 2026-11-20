# Silora admin console

Silora offers **Profile menu → Admin** on phone and TV for administrator
accounts. The server requires the account’s primary profile for admin calls;
the console prompts administrators on another profile to switch. The ordinary
user Requests screens keep their request, cancellation and status controls.

The administrator's primary profile can enable the menu entry with **Show Admin
in profile menu** in **Settings → Administrator** on phone or **Settings →
General → Administrator** on TV. It defaults off and saves separately for each
server, account and profile on the current device. The setting stays available
when the entry is hidden, so the administrator can enable it at any time. It controls
menu visibility; server permissions still govern admin operations.

Dashboard and now playing, request moderation, tasks/scans and logs have
dedicated screens. Activity, People, Media and Server groups expose the other
operations in the bundled admin API through generic forms. Edits load current
values when a matching read exists. Destructive operations ask for confirmation.
Generic forms ask for confirmation before every non-read request and preserve
command identities when retrying a lost reply. Dedicated screens keep their
action-specific confirmations. These controls depend on the connected server’s
API and permissions.

The asset lists 474 operations, including plugin capabilities and provisioning
status. The generic console filters out file uploads,
binary downloads and raw-protocol operations such as the log WebSocket; these
require web admin. It binds item actions using path positions, supports editable
key/value maps and explicit clearing of form values, and masks credential fields.
JSON operations remain subject to the connected server’s version and schema
requirements. The dashboard shows series and episode titles with both current
and older servers and shows server-provided transcode reasons. Running tasks
show their start time when the server supplies it.

Matt requested the ten-commit range from edrock200/silo-android
[`3cb8886ed`](https://github.com/edrock200/silo-android/commit/3cb8886ed5b5cb96f3140b8d7786d323143f2814)
through
[`3792ac473`](https://github.com/edrock200/silo-android/commit/3792ac473b58d283dc5bdd67dc05ceccb638f084)
on November 8, 2026. This is the `admin-console` entry in
[FORK_DIFFERENCES.md](../FORK_DIFFERENCES.md).

## Implementation

- `shared/.../admin/`: `AdminSpec`, `AdminCatalog`, `AdminForm`, `AdminDisplay`
  and the typed `AdminModels`; `network/apiv2/AdminV2Api.kt` executes calls.
- `android-shared/.../common/admin/`: `AdminSpecLoader`, `AdminAccess`,
  `AdminMenuPreferences`, `AdminConsoleViewModel`, `AdminService`, the
  dashboard/requests/tasks/logs view models and `AdminPresentation`.
  `common/di/AdminModule.kt` is loaded by both applications.
- Phone: `screens/admin/AdminConsoleScreen.kt`, `AdminHandBuiltScreens.kt`,
  `Route.AdminConsole`, and `LocalAdminConsole` in navigation/profile menu.
- TV: `screens/admin/TvAdminConsoleScreen.kt`, `TvAdminHandBuiltScreens.kt`,
  `TvRoute.AdminConsole`, and `onOpenAdmin`/`showAdmin` in `TvMainShell`.
  Preserve `requestFocusUntilObserved` and `ReclaimPageFocus` for D-pad focus.

## Refreshing the API contract

The current asset matches Edrock v49 at `f640a73de80299c08ca3b1ff53d5f6e013dbcb30`.
It regenerates from the official silo-server OpenAPI contract at
`975bbf41b33ccdce5bdc1d14daa9742748042be9` and contains 474 admin operations.
The `info.source` stamp, `2c2dc7c 2026-11-19`, identifies the last commit that
changed the contract file in that server checkout.
The console shows that stamp and does not fetch a new spec at runtime.
To update from a deliberately selected server checkout, run:

```sh
python3 -I scripts/admin-spec.py /path/to/silo-server/contracts/api/v2/openapi.json
./gradlew :android-shared:testReleaseUnitTest --tests 'org.siloserver.silo.common.admin.*' :shared:testReleaseUnitTest --tests 'org.siloserver.silo.admin.*'
```

`AdminHandBuiltContractTest` checks the operation IDs, response fields, query
parameters and request bodies used by the dedicated screens. When a server
change breaks this test, update `AdminModels.kt`, `AdminScreensViewModels.kt`,
`AdminPresentation.kt`, both UIs and the corresponding expectations together.
Use the server handlers and web admin pages to confirm what changed. Add new
controls to both clients when they belong in these dedicated screens; other
operations are picked up from the regenerated asset. Record the server source
and validation in the changelog. Keep Silora’s existing release workflow.

`AdminPlaybackSessionTest` checks the Now Playing labels for both response
formats. The current contract uses `series_title` and `media_title`. Older
servers can send `series_name` and a separate `episode_name`.

## November 15, 2026 update

Matt requested importing missing Admin fixes from edrock's
`feat/hevc-level-override-v42`. The cumulative update includes supported-operation
filtering, item binding, form clearing and credential masking, confirmation and
retry handling, stale version-tag recovery, preserved list state, deduplicated
pagination and notices, stable task rows, and retryable menu-access checks.
The original Silora pagination regression tests remain in place.

Silora retains its default-off, device-local **Show Admin in profile menu**
setting and primary-profile gating. The access cache is adapted to the complete
server/account/profile credential identity rather than a profile ID alone;
late answers after an identity switch are discarded, and cancellation stops
retries. `AdminAccessTest` covers those boundaries. API permissions remain
enforced by the server. See the root changelog for exact source commits and
validation; no device or real-server Admin operation was exercised in this update.
