# Feature × test inventory (Phase 11 T8)

Every user-visible feature in `ROADMAP.md`, and which kind of test looks at it. **GAP** = nothing of that kind does yet; a gap
with a journey name in the last columns was closed by T8 (`MoreJourneysTest`). `tools/check_docs.py` checks this file: every
journey named below (`ClassName#test`) must exist, and every journey test in `android/app/src/androidTest` (`*JourneysTest`,
`FirstLaunchTest`) must be named here. Add a row when you add a feature (skill `add-feature`), a journey name when you add a journey.

| column | meaning |
|---|---|
| unit | a core test class (the core Gradle test task) |
| VM | a ViewModel / state test on the JVM (T3, `android/app/src/test/.../ui`) |
| shots | a Paparazzi class (light / dark, Hebrew / English), or the emulator screens (`ScreensTest`, 9 phone profiles, layout audit) |
| journey | a test that taps through it on an emulator (`Class#test`) |
| faults | `FaultMatrixTest` (T4: 429 / 503 / timeout / garbage / empty / offline, request counts) |
| phone | `BB` = a line in `docs/testing/bug-bash.md` (the owner on a real phone, every release); Test Lab real phones are not set up (`devices.yml`) |

## Searching and the trip

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Search box, geocode, my location, long-press destination | `PlaceSearchTest`, `TypingSearchTest`, `LocalFirstTest`, `AddressCorpusJvmTest` | `AppStateTest` | `PanelsTest`, `s03_search_with_keyboard` | `JourneysTest#j02_search_by_taps_shows_results`, `FirstLaunchTest#j01_deny_location_and_still_search` | `typing_search_*`, `pin_name` | BB |
| Itineraries list + legs on the map | `Phase1Test`, `RecordedTripsTest`, `RoutingInvariantsTest` | `TripStateTest` | `s04_trip_results`, `s05_trip_second_option` | `JourneysTest#j02_search_by_taps_shows_results` | `trip_search` | BB |
| Arrive-by, preferences (walk, transfers, modes, speed) | `UserJsonTest`, `WalkAndSortTest` | `TripStateTest` | `s14_settings_top`, `s15_settings_bottom` | `SystemJourneysTest#mid_flow_theme` | GAP | BB |
| Pick a day (Today, Tomorrow, date) | `DayPickTest`, `DayNoticeTest`, `HolidaysTest` | `DayPickViewModelTest`, `DayNoticeViewModelTest` | `DayPickShotsTest`, `DayNoticeShotsTest` | `JourneysTest#j16_pick_tomorrow_searches_that_day` | `trip_search` | BB |
| Earlier / Later | `TripPagesTest` | `TripStateTest` | GAP | GAP | `earlier_later` | BB |
| Sort chips (fastest, fewest transfers, least walking) in every tab | `SortOptionsTest` | `SortUiTest` | `PolishShotsTest` | GAP | GAP | BB |
| Spare minute at every change, no unreasonable chains | `TransfersTest`, `QualityPassTest` | GAP | GAP | GAP | GAP | BB |
| Tight-transfer chip, big-stop change ("Use this change") | `BigStopChangeTest` | `DetailStateTest` | `BigStopShotsTest` | GAP | `trip_sheet` | BB |
| Ride details: stops on board, get-off stop, collapsed long lists | `RideDetailsTest`, `TripDetailsTest` | `DetailStateTest` | `PolishShotsTest` | GAP | `trip_sheet` | BB |
| Train number, "Fewer stops", accessible-bus badge | `TrainLabelTest`, `AccessibleBadgeTest` | GAP | `LegLabelsShotsTest` | GAP | GAP | BB |
| Accessible routes setting (wheelchair walking) | `UserJsonTest` | GAP | GAP | GAP | GAP | BB |
| Walking directions street by street | `Phase6Test` | GAP | GAP | GAP | GAP | BB |
| "If I miss this: next at …" | `TripPagesTest` | GAP | GAP | GAP | GAP | BB |
| Last trip / Shabbat warning, holiday banner, "no service that day" | `HolidaysTest`, `DayNoticeTest`, `Phase5Test` | `DayNoticeViewModelTest` | `DayNoticeShotsTest` | GAP | GAP | BB |
| Fare estimate "≈ ₪8", fare profiles | `FareTest`, `FareProfileTest`, `FarePropertyTest` | GAP | `s04_trip_results` | GAP | GAP | BB |
| By car comparison (traffic factor) | `CarAndChainTest` | GAP | GAP | GAP | GAP | BB |
| Errands (up to 3 stops, chained) | `CarAndChainTest` | `CarTabsStateTest`, `RestoreTabsStateTest` | `PanelsTest` | GAP | `errands` | BB |
| Rain-aware walking note | `OpenMeteoTest`, `RainAdviceTest` | GAP | `RainShotsTest` | GAP | `rain_note` | BB |
| Share an address / `geo:` / Waze / Maps link into the planner | `SharedPlaceTest` | GAP | `PlacePickShotsTest` | `MoreJourneysTest#m04_shared_place_becomes_the_destination` | GAP | BB |
| Voice search, search by stop number | `StopCodeTest` | GAP | GAP | GAP | GAP | BB |
| Calendar → arrive by | `CalendarSuggestTest` | `CalendarViewModelTest` | `PlacePickShotsTest` | GAP | GAP | BB |

## The car features

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Better start (drive limit slider, falls back to `CAR`) | `FeaturesTest`, `GeoAndParetoTest`, `ParetoPropertyTest` | `CarTabsStateTest`, `RestoreTabsStateTest` | `s08_better_start`, `s09_better_start_none` | `JourneysTest#j07_better_start_slider_searches_once_within_budget`, `JourneysTest#j11_send_to_driver_shares_waze_and_google_links` | `better_start` | BB |
| Let me off on the way (drop-off): pick an option, send to driver | `FeaturesTest`, `LastRideTest` | `CarTabsStateTest` | `s10_drop_off_fields`, `s11_drop_off_results` | `MoreJourneysTest#m01_drop_off_pick_and_send` | `drop_off` | BB |
| Best pick-up point, send to driver | `FeaturesTest` | `CarTabsStateTest` | `s12_pick_up` | GAP | `pick_up` | BB |
| Park & ride, way back to my car | `ParkRideTest` | `CarTabsStateTest`, `RestoreTabsStateTest` | `PanelsTest` | `MoreJourneysTest#m02_park_and_ride_search_and_start` | `park_ride` | BB |

## Stops and timetables

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Stops layer + stop sheet with departures | `StopsCacheTest`, `DepartedFilterTest`, `DepartureCacheTest` | `DetailStateTest` | `s13_stop_departures` | `MoreJourneysTest#m03_stop_sheet_opens_the_day_timetable` | `stop_departures`, `map_stops` | BB |
| Day timetable of a line at a stop, offline timetables | `DayTimetableTest` | `DetailStateTest` | `s19_stop_timetable`, `TimetableShotsTest` | `MoreJourneysTest#m03_stop_sheet_opens_the_day_timetable` | `day_timetable` | BB |
| Platform / floor / stop number | `StopPlatformTest` | GAP | `BigStopShotsTest` | GAP | GAP | BB |
| My lines (☆ a line, next 3 departures, offline) | `FavoriteLineTest` | GAP | GAP | GAP | GAP | BB |
| Trip details + the vehicle on the map | `TripDetailsTest`, `LiveProgressTest` | `DetailStateTest` | GAP | GAP | `trip_sheet` | BB |

## Saved things

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Saved places and trips, one-tap re-plan | `UserJsonTest`, `UserJsonPropertyTest`, `UserDataMigrationTest` | `SavedUiTest` | `s02_home_saved`, `s18_save_trip_dialog` | `JourneysTest#j10_save_trip_then_one_tap_then_delete` | GAP | BB |
| Routines on saved places, Home / Work | `RoutineTest` | `TripHomeViewModelTest` | `s02_home_saved` | GAP | GAP | BB |
| Launcher shortcuts (My lines, saved places) | GAP | GAP | GAP | `JourneysTest#j18_shortcuts_open_through_the_alias` | GAP | BB |
| Offline: last results with an "offline" banner | `TripCacheUpgradeTest`, `CacheLookupTest` | `TripStateTest` | `s06_trip_network_error`, `s07_trip_offline_banner` | `JourneysTest#j08_no_signal_shows_saved_results_then_retry_works` | `trip_search` | BB |
| Search and chosen trip kept across process death | `TabCacheTest` | `RestoreTabsStateTest`, `SavedUiTest` | GAP | `SystemJourneysTest#mid_flow_rotation`, `SystemJourneysTest#mid_flow_language` | GAP | BB |
| Settings: language, theme, first-run tour | `AppearanceTest` | `AppStateTest` | `LookShotsTest` | `SystemJourneysTest#mid_flow_theme`, `SystemJourneysTest#mid_flow_language` | GAP | BB |
| Screen reader (TalkBack): spoken legs and rows, labels, headings, reading order; font ×2.0 | `SpeechTest` | GAP | `PanelsTest` (font ×2.0, Hebrew dark), the layout audit (R5) | `A11yJourneysTest#a11y_journey_hebrew`, `A11yJourneysTest#a11y_journey_english`; ATF after every step of every journey (`A11yHook`) | GAP | BB |
| Crash log (last 5, share) | `CrashLogTest` | `FullDiskStateTest` | `s15_settings_bottom` | `ChaosJourneysTest#low_storage` | GAP | BB |
| Data & battery card, low-data switch | `UsageLogTest`, `LowDataTest` | GAP | `PayDataShotsTest` | GAP | GAP | BB |
| "Pay with…" | `PayAppsTest` | GAP | `PayDataShotsTest` | GAP | GAP | BB |

## On the trip

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Leave reminder (exact alarm, re-check, boot re-arm) | `Phase5Test`, `CountdownTest` | `MissedItStateTest` | `AlertsShotsTest` | `JourneysTest#j03_remind_me_arms_a_reminder`, `SystemJourneysTest#arm_trip_state`, `SystemJourneysTest#trip_state_unchanged` | GAP | BB |
| "Check reminders" + test reminder in 1 minute | `ReminderHealthTest` | GAP | `ReminderCheckShotsTest` | `JourneysTest#j15_test_reminder_rings`, `SystemJourneysTest#arm_test_reminder`, `ChaosJourneysTest#check_reports_the_restriction` | GAP | BB |
| Leave-now countdown, delay alerts while waiting | `CountdownTest`, `LiveProgressTest` | GAP | `AlertsShotsTest` | `JourneysTest#j14_leave_countdown_is_a_live_update` | GAP | BB |
| Get-off alert, ride notification, spoken alert | `RideReplayTest`, `RideTrackerArmingTest`, `RideTrackerPropertyTest` | GAP | GAP | `JourneysTest#j12_ride_alert_fires_near_the_stop` | GAP | BB |
| "I missed it" re-plan from GPS | `MissedItTest` | `MissedItStateTest` | `MissedItShotsTest` | `JourneysTest#j16_missed_it_from_ride_notification`, `JourneysTest#j17_missed_it_from_a_killed_app` | `missed_it` | BB |
| Last trip home alert | `LastTripHomeTest` | GAP | `AlertsShotsTest` | GAP | GAP | BB |
| Night refresh (Wi-Fi + charging) | `NightRefreshTest` | GAP | `NightRefreshShotsTest` | GAP | GAP | BB |
| Quick Settings tile | GAP | GAP | `TileShotsTest` | system job: the real tile tapped (`ui_system_run.sh`, API 34) | GAP | BB |
| Rav-Kav pass-expiry reminder | `PassExpiryTest` | GAP | `ReminderCheckShotsTest` | `SystemJourneysTest#arm_trip_state` | GAP | BB |
| Airplane mode, Doze, reboot | GAP | GAP | GAP | `SystemJourneysTest#airplane_mode` | GAP | BB |
| Clock skew | GAP | GAP | GAP | `ChaosJourneysTest#clock_skew` | GAP | BB |

## History and money

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Trip history + stats, "usually 52 min", insights | `UsualTripTest`, `Phase6Test` | GAP | `s16_history`, `InsightsShotsTest` | GAP | GAP | BB |
| Monthly pass advisor | `PassAdvisorTest` | GAP | `InsightsShotsTest` | GAP | GAP | BB |
| Monthly travel report (CSV + text) | `ReportTest` | GAP | `FareReportShotsTest` | `MoreJourneysTest#m05_monthly_report_exports_a_csv` | GAP | BB |

## The app itself

| feature | unit | VM | shots | journey | faults | phone |
|---|---|---|---|---|---|---|
| Back closes what is open, one Back at a time | GAP | `AppStateTest` | GAP | `JourneysTest#j04_back_closes_before_leaving` | GAP | BB |
| Rotation and dark mode keep the results | GAP | `SavedUiTest` | `LookShotsTest` | `JourneysTest#j05_rotation_and_dark_mode_keep_the_results` | GAP | BB |
| In-app update banner, download, digest check | `InAppUpdateTest` | `AppStateTest` | `s17_update_banner_with_results` | `JourneysTest#j13_update_banner_shows_and_dismisses` | GAP (T6) | BB |
| Release APK installs and starts (network off) | GAP | GAP | GAP | `release-smoke` job (`tools/ui_release_run.sh`) | GAP | BB |
| An update keeps the user's data | `UserDataMigrationTest` | GAP | GAP | `upgrade.yml` (`tools/ui_upgrade_run.sh`) | GAP | BB |
| No leaks (Activity, ViewModel, MapView, service) | GAP | GAP | GAP | LeakCanary after every journey (`AppDriver.close`) | GAP | GAP |

## Gaps left after T8, by size

The five biggest were closed by `MoreJourneysTest`. Next in line (no order): Earlier / Later; sort chips in the car tabs;
pick-up send to driver; My lines; trip details and the vehicle; the data & battery card; the tile; voice search and stop number.
A gap is closed by a row's cell changing, not by deleting the row.
