# Genrix UI redesign: report

## Build verification
**The Gradle build was NOT run in the environment where this was written** (no Android SDK or network there). Nothing here is claimed as compiled or tested on a device. Verify by pushing to GitHub: the "Build APK" workflow runs `:app:assembleDebug`. If it fails, the error text shows exactly which line to fix.

## What changed (presentation only)
- `ui/Theme.kt`: new dark-first palette tokens (`GenrixColors`), semantic names (`Accent`, `AccentAlt`, `Raised`, `Muted`, `MutedDim`, `Positive`, `Warning`), type scale, shapes (14 to 28 dp), light scheme.
- `ui/Components.kt`: card sections with an accent tick, premium switch colours, `ScreenHeader`, spring `pressScale`.
- `ui/Orb.kt`: new AI core (glow, thin rings, particles, organic core, luminous rim, execution burst, error tint). Same call as before plus optional `executing`, `error`, `idleMotion`.
- `ui/HomeScreen.kt`: Neural Command Center layout, GENRIX + NEURAL ASSISTANT, large core, state label, new mic button, neutral quick-action cards, bubbles.
- `ui/GenrixApp.kt`: tab styling and soft tab transitions. Same four destinations.
- `ui/AutomationsScreen.kt`: command search, expandable categories, Local / Approval / AI badges, monospace examples, delete confirmation.
- `ui/ActivityScreen.kt`: card timeline, empty state, header.
- `ui/SettingsScreen.kt`: new header only; rows and sections restyle through the shared components.
- `orb/FloatingOrb.kt` (no idle animation over other apps), `control/ControlOverlay.kt` (colours), `Branding.kt` (descriptor), resources (colours, launcher icon).

## Preserved
Every callback, setting, permission flow, routine, local command, PC bridge, notification and persistence path is untouched: no assistant, voice, Gemini, command, service or data code was edited. The only behaviour added is a confirmation dialog before deleting a routine, a request in the redesign brief.

## Genuine limitations
- Manrope and JetBrains Mono are not bundled (no network when this was built). `GenrixFonts` uses the system sans and monospace; swap two values to bundle them.
- The app has no "executing" or "error" phase of its own. The orb derives them from existing state: a local command running, and a startup problem while idle.
- Activity entries carry no success or failure flag, so none is shown.
- Not checked on a device: layouts on small screens, animation performance, reduced-motion behaviour.

## Round 2: matched to the storyboard (`design/storyboard-reference.png`)
Done: centred gradient GENRIX wordmark with NEURAL ASSISTANT; the AI core now has planet-style orbit rings and a "G" mark; a STANDBY / LISTENING / EXECUTING / SPEAKING status pill; quick actions with drawn Torch, Battery, Time and Pause icons; mic button with a glow; glass cards with a cyan-to-violet edge; soft violet and blue "nebula" light behind every screen; filter chips and icon tiles on Automations (All / Local / AI / Approval) and Activity (All / On phone / Gemini); icon tiles on Settings rows and a Genrix card at the top.

Deliberately not done, because the app has no such feature or data (nothing was faked):
- "Pro" and "Premium Edition" badges, and "Explore 100+ Commands".
- Success check marks on Activity rows (entries carry no success flag) and the storyboard's All / Commands / System / Other categories (replaced by On phone / Gemini, which the data supports).
- The raised mic button inside the bottom bar (navigation was left as is).
- The Permissions, Command Result and Voice States sheets as separate screens. Onboarding keeps its existing flow and picks up the new theme.

Still not built or run anywhere: push to GitHub and read the "Build APK" result.
