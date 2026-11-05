## F-Droid publication status

Package ID: `com.shinjikai.dictionary`

Existing request: [F-Droid RFP #3650](https://gitlab.com/fdroid/rfp/-/work_items/3650)

The request points to the old GitHub URL `obj44/shinjikai`, which now redirects to
the current source repository: <https://github.com/objx0/Shinjikai-Android>. The
request description should be refreshed before asking F-Droid maintainers to
package a release.

### Current app details

- App source is MIT-licensed (`LICENSE`).
- The current app is an offline-first Japanese-Arabic dictionary. It bundles
  dictionary data and images, supports local Yomitan imports, bookmarks,
  history, and Anki export.
- Online access is used by some app features. The app does not require a
  Firebase configuration to build; Firebase dependencies are included only
  when a local `app/google-services.json` exists.
- The app includes Japanese deinflection code marked
  `GPL-3.0-or-later` in `JapaneseDeinflector.kt`; retain its attribution and
  license when preparing the F-Droid metadata.
- Fastlane English listing text exists, as do two phone screenshots under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- The checked-out app version is `1.13` (version code 13), but the newest Git
  tag in this checkout is `v1.10`. F-Droid needs a stable public source ref
  that matches the version being packaged.

### Bundled data license

The rights holder has confirmed in writing that the bundled dictionary data
and images may be redistributed under CC BY 4.0. The notice is included at
`app/src/main/assets/bundled_dictionary/LICENSE.txt`. Keep the email as the
permission record and provide it to F-Droid reviewers if requested; do not
publish private correspondence without permission. The app source remains MIT,
and Japanese deinflection code remains GPL-3.0-or-later.

The previous submission guide also described the dataset as optional and
downloaded at runtime; both statements are stale. The current app bundles the
dataset, and the asset-refresh script downloads it during development.

### Suggested RFP update

After preparing a matching public release tag, add a comment to RFP #3650 with
the current repository URL, version/tag, package ID, build instructions, and
corrected app description. State clearly which features access the network
and list the licenses for app code, deinflection code, and dictionary data.

The app build is Gradle-based. F-Droid should build from the source tag rather
than use the signed APK published through GitHub Releases.
