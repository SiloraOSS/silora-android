# Announce a verified Silora release

Matt authorized one automatic embed in `1568502343631114260`
(#📜・silo-daily-changelog) per newly published release from this skill. The
design preview is a one-time review. Future posts under this standing
exception need no user-facing preview or separate approval.

After this live execution verifies public publication and asset read-back,
record the verified tag and full source SHA. Read the published GitHub
release notes for that exact tag. Write a few short, user-facing bullets in
a private `release-highlights.md` file. Use the changes in this release and
include meaningful Silora-specific changes from the committed range since
the preceding public build when that baseline can be verified. A cumulative
upstream list can include work already shipped. Keep one combined list with
one bullet per line. End each bullet with `*(Silo upstream)*` for an official
upstream change or `*(Silora)*` for a change introduced on the fork,
including imported patches. Check the origin against the release commits.
Link to the full notes. Run the helper from the repository checkout.

```sh
python3 -B scripts/announce-silora-release.py \
  FULL_PUBLIC_RELEASE_TAG FULL_VERIFIED_SOURCE_SHA \
  --highlights-file "$execution_dir/release-highlights.md" --publish
```

This applies to GitHub builds and local APK releases, including current-branch
releases and dry runs promoted to live. A tag already public before this
execution, a skipped build, an unapproved preview, or an unverified release
does not qualify. On resumption, finish a pending announcement only for a
verified release published by that execution.

The helper verifies the exact published GitHub tag and both APKs before
sending. It uses the SigmaUtils `discord-tools announce` command for a
single teal embed with the release title and Pacific publication date, short
highlights, build-specific APK links and the full GitHub release notes.
After SigmaUtils's persistent view is deployed and verified, the post pings
only **Silora Updates** (`1573885206174371840`) and includes **Get Silora
Updates** and **Stop Silora Updates** on the same embed. The buttons add or
remove only that role for the member who clicks. Before deployment the helper
can still post the release embed with no pings or buttons. Shorten the
highlights if they exceed the embed limit.

The local readiness marker is
`Staging/silora-upstream-automation/config/silora-updates.ready`. Record the
verified SigmaUtils deployment in it once the running bot includes the
tested controls. Remove it on rollback to a version without the view. The read-only embed preview
always shows the proposed role and buttons. An authorized automated release
uses the marker to choose the live payload and sends no extra audit post.

Per-release state stays private under
`Staging/silora-upstream-automation/release-announcements/`. A delivered
receipt returns the verified message link. For an uncertain send, use the
frozen announcement and recorded message ID or Discord channel history to
reconcile read-only. Record delivery only after the bot author, destination
and exact embed match. Preserve uncertain intents and avoid duplicate sends.

Save the message link in the execution receipt and report it with the GitHub
release. Report Discord failures separately from the confirmed release and
preserve both receipts. On the local route, attempt the staff milestone and
the changelog announcement independently.
