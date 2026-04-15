# CR Intake Spec - Profilo e Correzioni UI/Macro

## Title
Intake specification for profile setup calorie estimate, plus button alignment, and macro percent-symbol correction.

## Problem statement
The app currently has three user-facing issues that reduce trust and usability in a key nutrition flow for Italian users: profile setup lacks a clear calorie estimate experience, the plus button appears visually misaligned, and macro input/display fields show duplicated percent symbols. These issues impact onboarding clarity, visual polish, and data-entry confidence.

## Goals
- Define a clear scope for introducing/adjusting profile setup behavior to include calorie estimate presentation.
- Define expected UX behavior for proper plus button alignment across supported layouts.
- Define expected behavior to prevent duplicated percent symbols in macro fields.
- Provide testable acceptance criteria for all three CR points.
- Preserve the existing app architecture while implementing changes.
- Reflect Italian business intent in copy and behavior assumptions (profilo, fabbisogno calorico, macro percentuali).

## Non-goals
- Implementing source code changes.
- Running tests.
- Redesigning unrelated screens or navigation.
- Refactoring app architecture or data models outside what is strictly required.

## User stories
- As a new user, I want to complete profile setup and see an estimated daily calorie target so I can start planning meals with a realistic baseline.
- As an Italian-speaking user, I want profile and nutrition wording to reflect Italian business intent so the app feels coherent and local.
- As a user, I want the plus button to look aligned and intentional so primary actions are easy to find and trust.
- As a user entering macro targets, I want percent fields to show a single percent symbol so I can avoid confusion and input mistakes.
- As a returning user, I want existing behavior outside these CR areas to remain unchanged.

## Functional requirements
- FR-001: Profile setup flow must include a visible calorie estimate output derived from available profile inputs.
- FR-002: The calorie estimate must be presented in a way that is understandable in the profile context and consistent with app terminology.
- FR-003: Plus button alignment must be corrected so its visual position is consistent with intended layout on relevant screens/states.
- FR-004: Macro percent fields must render a single percent symbol in all relevant states (initial, edited, restored).
- FR-005: Input handling for macro percent fields must avoid appending duplicate percent symbols during typing, focus changes, or value rebind.
- FR-006: The scope is limited to the three CR items; no unrelated functional behavior should change.

## Non-functional requirements
- Performance: No perceptible UI lag should be introduced in profile setup or macro input interactions.
- Security: No new sensitive personal data collection beyond existing profile inputs; no logging of private profile values in plaintext for this CR scope.
- Usability: UI text and visual behavior should be clear for Italian users; controls must remain legible and tappable at common mobile sizes.
- Compatibility: Changes should work across currently supported Android versions and common screen densities/orientations targeted by the app.
- Maintainability: Keep implementation within current architecture boundaries.

## Edge cases
- EC-001: User provides partial profile data; calorie estimate behavior must handle missing optional fields gracefully.
- EC-002: User changes profile inputs repeatedly; calorie estimate should update consistently without stale values.
- EC-003: Profile setup reopened from saved state or process recreation; calorie estimate display remains correct.
- EC-004: Plus button alignment on small-width devices.
- EC-005: Plus button alignment in right-to-left or mirrored layout environments if supported by system settings.
- EC-006: Macro percent field prefilled from persisted values containing a percent sign.
- EC-007: Macro percent field empty value, zero value, and max expected value handling without duplicate symbols.
- EC-008: Macro field formatting after focus gain/loss, keyboard actions, and recomposition/rebind cycles.
- EC-009: Copy consistency when localization resources fall back (Italian intent should remain understandable).
- EC-010: Accessibility text/labels remain coherent after UI alignment and symbol fixes.

## Assumptions
- Existing profile inputs are already sufficient to produce an estimate without introducing new mandatory fields.
- Business intent requires Italian-facing terminology consistency, not a full localization overhaul.
- The plus button issue is present in currently active UI paths and is not intentional by design.
- Duplicate percent symbol behavior is a defect, not a deliberate formatting rule.
- This intake covers specification only; implementation details are deferred to later agents.

## Product and UX risks
- R-001: If calorie estimate logic is perceived as opaque, users may distrust recommendations.
- R-002: Alignment fixes may regress on specific device form factors if not validated broadly.
- R-003: Percent formatting fixes may unintentionally alter numeric parsing if display and model formats are tightly coupled.
- R-004: Italian wording inconsistencies across screens may remain if only partial copy is adjusted.

## Definition of Done
- The session contains a complete intake spec with clear scope and non-scope for all three CR items.
- Acceptance criteria are explicit, testable, and machine-readable in acceptance.json.
- Scope explicitly includes: profile setup calorie estimate, plus button alignment, and duplicated percent symbol fix.
- Assumptions, constraints, and edge cases are documented with minimal ambiguity.
- Status artifact is initialized and consistent with INTAKE state requirements.

## Acceptance Criteria
- AC-001: Scope explicitly includes profile setup calorie estimate behavior.
- AC-002: Scope explicitly includes plus button alignment correction.
- AC-003: Scope explicitly includes duplicate percent-symbol correction in macro fields.
- AC-004: Spec reflects Italian business intent in wording/behavior expectations.
- AC-005: Non-goals explicitly exclude direct implementation and test execution in this step.
- AC-006: Edge-case coverage includes state restoration, input formatting cycles, and device layout variations.
- AC-007: Constraints include preserving existing architecture.
- AC-008: Acceptance criteria are mapped in .agents-work/2026-04-07_cr-profilo-ui-macro/acceptance.json with verification methods.
