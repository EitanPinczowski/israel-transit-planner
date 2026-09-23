---
name: add-feature
description: Recipe for adding any user-visible feature end to end - core logic, API call, tests, ViewModel, Compose screen, Hebrew and English strings. Use for "add X to the app", "new screen", "new setting".
---

# Adding a feature, in order

1. **Check `dead-ends`** and `ROADMAP.md` — is it planned, or already rejected?
2. **Logic in `core/` first**, as plain Kotlin with no Android imports. If it calls
   Transitous, go through `TransitApi` and load `transitous-api`. If it can issue more
   than one request per user action, wrap it in `BudgetedTransitApi` and pin the budget
   in a test.
3. **Test in `core/src/test`** with `FakeTransitApi` (scripted, records every call) or a
   fixture + `MockWebServer`. Run `./gradlew -p core test -q`.
4. **App side** (`android/app/src/main/java/il/transit/planner/`):
   - a `ViewModel` holding UI state as an immutable data class in a `StateFlow`;
   - a `@Composable` screen that only renders state and sends events;
   - network via one app-wide `GuardedTransitApi(MotisClient())`.
5. **Strings**: every visible text in BOTH `res/values/strings.xml` (English) and
   `res/values-iw/strings.xml` (Hebrew). Load `i18n-rtl`.
6. **Settings** go in DataStore, not SharedPreferences.
7. **Tick the item in `ROADMAP.md`** in the same commit.
8. CI must be green; the `android` job's APK artifact is how the owner tries it.

Keep screens thin and logic in `core`: `core` is the only part a cloud session can compile.
