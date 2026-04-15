# Design Specification: History Tab, Nutrient Display, Swipe Navigation & Override Button

**Session**: 2026-04-13_history-nutrients-navigation  
**Version**: 1.0  
**Last Updated**: 2026-04-13

---

## 1. Executive Summary

This design specification defines the complete UI/UX for enriching the History tab with:
1. **Horizontal swipe-based day navigation** (left/right gestures with visual feedback)
2. **Meal card component** displaying all 12 nutrients (4 macronutrients + 8 enriched: saturated fat, sugar, minerals, vitamins)
3. **Nutrient grouping strategy** (collapsed macros/minerals/vitamins tabs or expandable sections)
4. **Override button** for manual nutrient value corrections (not inline)
5. **Dashboard integration** (macronutrient card becomes clickable, navigates to History)
6. **Responsive design** for mobile (320dp, 411dp) and tablet
7. **Accessibility** with WCAG 2.1 AA compliance (focus indicators, semantic labels, screen reader text)
8. **Empty state UI** when no meals logged for selected day
9. **Confirmation dialogs** for destructive actions (meal deletion)

The design follows Material 3 design system, existing Jetpack Compose patterns, and Italian localization conventions currently used in the app.

---

## 2. Material 3 Design Tokens

### Color Palette (Light Mode)
| Token | Value | Usage |
|-------|-------|-------|
| **primary** | `colorScheme.primary` | CTA buttons, essential highlights |
| **primaryContainer** | `colorScheme.primaryContainer` | Card backgrounds, subtle highlights |
| **secondary** | `colorScheme.secondary` | Alternative actions, secondary buttons |
| **tertiary** | `colorScheme.tertiary` | Accent for minerals/vitamins sections |
| **error** | `colorScheme.error` | Delete button, error states |
| **errorContainer** | `colorScheme.errorContainer` | Confirmation dialog background |
| **background** | `colorScheme.background` | Screen background |
| **surface** | `colorScheme.surface` | Cards, surfaces |
| **surfaceVariant** | `colorScheme.surfaceVariant` | Dividers, muted borders |
| **outline** | `colorScheme.outline` | Borders, disabled state |
| **onPrimary** | `colorScheme.onPrimary` | Text on primary buttons |
| **onSurface** | `colorScheme.onSurface` | Default text color |
| **onSurfaceVariant** | `colorScheme.onSurfaceVariant` | Secondary text, captions |

### Dark Mode
- Automatically applied via `darkColorScheme()` (androidx.compose.material3)
- Ensure sufficient contrast for all text (minimum 4.5:1 for AA compliance)
- Test on actual dark mode devices

### Typography
| Style | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| **headlineSmall** | 24sp | Medium | 32sp | Page title (e.g., "Storico") |
| **titleLarge** | 22sp | Medium | 28sp | Daily date header |
| **titleMedium** | 16sp | Medium | 24sp | Meal type label, section headers |
| **titleSmall** | 14sp | Medium | 20sp | Card titles (food name) |
| **bodyLarge** | 16sp | Regular | 24sp | Primary body text |
| **bodyMedium** | 14sp | Regular | 20sp | Nutrient values, secondary text |
| **bodySmall** | 12sp | Regular | 16sp | Metadata, portion info |
| **labelMedium** | 12sp | Medium | 16sp | Button labels, badges |
| **labelSmall** | 11sp | Medium | 16sp | Captions, disabled text |

---

## 3. History Tab Layout

### 3.1 Screen Structure (High Level)

```
┌─────────────────────────────────────────────────────┐
│  "Storico" (title)                                  │  ← headlineSmall
├─────────────────────────────────────────────────────┤
│                                                     │
│  [◀ Prev]         [date]         [Next ▶]          │  ← Navigation row (buttons or swipe hints only)
│  [Mon, Apr 13]    (hidden on swipe interaction)    │
│                                                     │
├─────────────────────────────────────────────────────┤
│  Daily Totals Card                                  │
│  ├─ kcal: 2150 | Carbs: 280g | Fat: 65g | Protein  │
│  └─ Saturated fat: 18g | Sugar: 45g | [Minerals] ▼ │  ← Expandable/tab section
├─────────────────────────────────────────────────────┤
│  Meals List                                         │
│  ├─ [Colazione - Breakfast]                         │
│  │  ├─ Pane Tostato (Toasted Bread)                 │
│  │  ├─ 100g                                         │
│  │  ├─ Nutrients: 245 kcal | 10g Pro | …           │
│  │  └─ [Edit] [Delete] [Override nutrients ▼]       │
│  │                                                  │
│  ├─ [Pranzo - Lunch]                               │
│  │  ├─ Spaghetti Pomodoro                           │
│  │  └─ (similar structure)                          │
│  │                                                  │
│  └─ [+ Add Meal] button                             │
│                                                     │
├─────────────────────────────────────────────────────┤
│  Gesture Hints (optional, fades on first swipe)    │
│  ◀────────────────────────────────► (chevron line)  │
│  "Scorri per navigare" (Swipe to navigate)          │
└─────────────────────────────────────────────────────┘
```

### 3.2 Daily Header Section

**Location**: Below main title  
**Height**: 56dp (single line) + padding

**Components**:
- **Left Arrow Button** (icon-only, `Icons.Filled.ChevronLeft`)
  - Material 3 TextButton variant (no background)
  - Disabled state when on oldest available day
  - testTag: `"history_prev_button"`

- **Date Display** (centered, grows with container)
  - Format: `"Mon, Apr 13, 2026"` (localized day + date)
  - Typography: **titleLarge** (22sp)
  - Color: `onSurface`
  - testTag: `"history_date_display"`

- **Right Arrow Button** (icon-only, `Icons.Filled.ChevronRight`)
  - Material 3 TextButton variant
  - Disabled state when on today
  - testTag: `"history_next_button"`

**Swipe Gesture Indicator** (optional, appears below when history first loads):
- Horizontal chevron/line animation with directional arrows
- Text: "Scorri per navigare" (swipe to navigate) in bodySmall + onSurfaceVariant color
- Fades out after first swipe or 3 seconds
- Used for first-time user guidance

---

## 3.3 Daily Totals Card

**Location**: Below navigation header  
**Card Type**: Material 3 Card (with primaryContainer background, 0.5 alpha for subtle effect)  
**Padding**: 12.dp internal spacing  
**Height**: Adaptive (expandable or tabbed)

**Content Structure**:

#### Main Row (Always Visible)
```
┌─────────────────────────────────────────┐
│ 🔥 Kcal: 2150 | Carbs: 280g | Fat: 65g │
│    Protein: 85g                         │
└─────────────────────────────────────────┘
```

- **Fire Icon** (`Icons.Filled.LocalFireDepartment`, tinted with primary color)
- **MacronutrientsRow**: Grid or horizontal layout
  - Kcal (large, bold)
  - Carbs, Fat, Protein (medium)
- **Typography**: titleMedium (labels) + bodyMedium (values)
- **Spacing**: 8dp between macro items

#### Expandable/Tab Section (Enriched Nutrients)
Two design options (choose one based on UX testing):

**Option A: Collapsible Sections** (simpler, less cognitive load)
```
├─ Macronutrients (always visible)
├─ ▼ Saturated Fat & Sugar (collapsible)
│  └─ Sat. Fat: 18g | Sugar: 45g
└─ ▼ Minerals & Vitamins (collapsible)
   └─ Fe: 8mg | Ca: 450mg | Mg: 120mg | Zn: 5mg
      Vit C: 65mg | Vit D: 2mcg | B12: 1.2mcg
```

**Option B: Tabs** (more compact, familiar pattern)
```
[Macros] [Enriched] [Minerals] [Vitamins]
```

**Recommended**: Option A (collapsible) for easier touch targets and progressive disclosure on small screens.

---

## 3.4 Meals List Section

**Container**: LazyColumn or Column with verticalScroll modifier  
**Height**: Fill remaining space (screen height - header - totals card)  
**Spacing**: 12.dp between meal entries

### 3.4.1 Meal Card Component

**Card Type**: Material 3 Card (surface, no special color)  
**Padding**: 16.dp  
**Elevation**: CardDefaults.cardElevation (default)  
**Corner Radius**: 12.dp (Material 3 default)  
**Modifier**: Modifier.fillMaxWidth()  
**testTag**: `"meal_card_<mealId>"`

**Layout Structure**:

```
┌─────────────────────────────────────────────────┐
│ [🥐] Colazione (Breakfast)    [10:30 AM]       │  ← Header
├─────────────────────────────────────────────────┤
│ Food Name                                       │
│ Pane Tostato (Toasted Bread), Butta brand      │  ← titleSmall, brand in gray
├─────────────────────────────────────────────────┤
│ Portion: 100 g                                  │  ← bodySmall, onSurfaceVariant
├─────────────────────────────────────────────────┤
│ MACRONUTRIENTS (always visible)                │  ← Bold label
│ ├─ Energy: 245 kcal                            │
│ ├─ Protein: 9.2 g                              │
│ ├─ Carbs: 42.1 g                               │
│ └─ Fat: 3.8 g                                  │
│                                                 │
│ ▼ SATURATED FAT & SUGAR (collapsible)          │  ← Expandable
│   (Collapsed by default on small screens)      │
│   ├─ Saturated Fat: 1.2 g                      │
│   └─ Sugar: 2.5 g                              │
│                                                 │
│ ▼ MINERALS (collapsible)                       │  ← Expandable
│   (Collapsed by default on small screens)      │
│   ├─ Iron: 2.1 mg                              │
│   ├─ Calcium: 125 mg                           │
│   ├─ Magnesium: 32 mg                          │
│   └─ Zinc: 0.8 mg                              │
│                                                 │
│ ▼ VITAMINS (collapsible)                       │  ← Expandable
│   (Collapsed by default on small screens)      │
│   ├─ Vitamin C: 8.5 mg                         │
│   ├─ Vitamin D: 0.1 mcg                        │
│   └─ Vitamin B12: 0.2 mcg                      │
├─────────────────────────────────────────────────┤
│ Action Row (bottom of card)                     │
│ [Edit] [Delete] [Override Nutrients ▼]         │  ← All buttons/icons
└─────────────────────────────────────────────────┘
```

### 3.4.2 Meal Card Content Details

**Header Row** (Meal Type + Time):
- **Icon**: Meal type icon (🥐 breakfast, 🍽️ lunch, 🍲 dinner, 🥤 snacks)
- **Label**: "Colazione" / "Pranzo" / "Cena" / "Snack" (titleMedium, primary color)
- **Time** (if available): Align right, bodySmall, onSurfaceVariant
- **Separator**: Divider below header

**Food Name Section** (always visible):
- **Name**: titleSmall, font weight Bold (e.g., "Pane Tostato")
- **Brand** (if available): bodySmall, onSurfaceVariant, italics (e.g., "(Butta brand)")
- **Source Badge** (optional): "OFF" or "Manual" tag badge in secondary color

**Portion Display** (always visible):
- Format: `"150 g"` or `"2 medium"` (quantity + unit)
- Typography: bodySmall
- Color: onSurfaceVariant
- Left-aligned with 4dp indentation (visual continuity)

**Nutrient Sections** (Collapsible on mobile, expanded on tablet):

#### Macronutrients (always visible, never collapsed):
```
MACRONUTRIENTS (or just 🥄 Macros)
├─ Energy:  245 kcal          [alignment: right]
├─ Protein: 9.2 g             [grams unit always included]
├─ Carbs:   42.1 g
└─ Fat:     3.8 g
```
- **Section Label**: labelMedium, bold, color: onSurface
- **Nutrient rows**: bodyMedium, with visual grid layout (2 columns on mobile, 4 on tablet)
- **Values right-aligned**: Use monospace font where possible for alignment
- **Missing values**: Show "—" (dash) in onSurfaceVariant color

#### Saturated Fat & Sugar (collapsed by default on 320dp, expanded on 411dp+):
```
▼ SATURATED FAT & SUGAR
  ├─ Saturated Fat: 1.2 g
  └─ Sugar: 2.5 g
```
- **Toggle Icon**: `Icons.Filled.ExpandMore` / `Icons.Filled.ExpandLess` (rotate when expanded)
- **onClick**: Toggle expansion state (tracked in ViewModel)

#### Minerals (collapsed by default on 320dp, expanded on 411dp+):
```
▼ MINERALS
  ├─ Iron (Fe): 2.1 mg
  ├─ Calcium (Ca): 125 mg
  ├─ Magnesium (Mg): 32 mg
  └─ Zinc (Zn): 0.8 mg
```
- Similar collapse behavior
- **Symbol display**: "(Fe)", "(Ca)", etc. for clarity

#### Vitamins (collapsed by default on 320dp, expanded on 411dp+):
```
▼ VITAMINS
  ├─ Vitamin C: 8.5 mg
  ├─ Vitamin D: 0.1 mcg (note mcg vs mg)
  └─ Vitamin B12: 0.2 mcg
```
- Similar collapse behavior

**Action Row** (always visible, at bottom):
- **Height**: 48dp (Touch-friendly minimum)
- **Spacing**: 8.dp between buttons

**Buttons** (left-to-right):
1. **Edit Button** (`[✎ Edit]`)
   - Material 3 TextButton
   - Icon: `Icons.Filled.Edit`
   - Label: `stringResource(R.string.meal_edit)`
   - onClick: Navigate to MealLoggingScreen with meal pre-filled
   - testTag: `"meal_edit_<mealId>"`

2. **Delete Button** (`[🗑️ Delete]`)
   - Material 3 TextButton
   - Icon: `Icons.Filled.Delete`
   - Label: `stringResource(R.string.meal_delete)`
   - Color: error (red)
   - onClick: Show confirmation dialog
   - testTag: `"meal_delete_<mealId>"`

3. **Override Button** (`[Override Nutrients ▼]`)
   - Material 3 OutlinedButton or TextButton (depends on preference)
   - Icon: `Icons.Filled.Edit` (or custom pencil + values icon)
   - Label: `stringResource(R.string.meal_override_nutrients)` ("Modifica valori nutrizionali" in Italian)
   - onClick: Show override dialog with all 12 nutrients
   - testTag: `"meal_override_<mealId>"`
   - **States**:
     - **Enabled**: User can edit
     - **Disabled**: If no meal selected or meal is being processed (grayed out)
     - **Loading**: Show spinner or pulse animation during save
     - **Overridden**: Visual indicator (e.g., slightly darker background or icon change)

---

## 3.5 Empty State (No Meals for Day)

**Trigger**: When selected day has zero meal entries  
**Location**: Replace meals list section  
**Layout**:
```
┌─────────────────────────────────────────────────────┐
│                                                     │
│           [🍽️ large icon, faded]                    │
│                                                     │
│     Nessun pasto registrato                        │
│     (No meals logged for this day)                 │
│                                                     │
│     Vuoi aggiungere un pasto?                      │
│     (Want to add a meal?)                          │
│                                                     │
│     [+ Add Meal] button                            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

- **Icon**: `Icons.Filled.DiningService` or custom, 64dp, color: surfaceVariant
- **Title**: "Nessun pasto registrato" (titleMedium, onSurface)
- **Subtitle**: "Vuoi aggiungere un pasto?" (bodyMedium, onSurfaceVariant)
- **CTA Button**: Material 3 Button, "+ Aggiungi pasto" (Add Meal)
  - onClick: Navigate to MealLoggingScreen with selected date pre-filled
  - testTag: `"empty_state_add_meal_button"`

---

## 3.6 Add Meal Button (Floating or Fixed)

**Placement**: Below meals list (fixed at bottom or as FAB)  
**Type**: Material 3 Button (filled)  
**Label**: `"+ Aggiungi pasto"` (Add Meal)  
**Icon**: `Icons.Filled.Add`  
**Height**: 48dp (touch-friendly)  
**Width**: Adaptive (match card width or full width)  
**Color**: secondary or tertiary  
**onClick**: Navigate to MealLoggingScreen with date context set to selected day (not today)  
**testTag**: `"history_add_meal_button"`

---

## 4. Swipe Gesture Navigation

### 4.1 Gesture Mechanics

**Detector**: `Modifier.pointerInput` with `detectHorizontalDragGestures`  
**Threshold**: Android standard (≥ 100dp drift distance, velocity-aware)  
**Debounce**: 500ms between swipes to prevent "flicker"  
**Boundaries**: Allow swipe but no-op at oldest/today boundaries  

### 4.2 Gesture Direction Mapping

| Gesture | Action | Boundary Behavior |
|---------|--------|-------------------|
| **Swipe Right** (drag left to right) | Show **previous day** | Disabled button + visual feedback (subtle shake or dim) |
| **Swipe Left** (drag right to left) | Show **next day** | Disabled button + visual feedback at today boundary |
| **Rapid Swipes** | Debounce: last action wins | Prevent state corruption |

### 4.3 Visual Feedback During Swipe

1. **Initial Swipe Detection**:
   - Meal cards slightly offset in swipe direction
   - Parallax effect (40-60% of swipe distance)
   - Opacity fade as threshold neared (0.8 → 0.6)

2. **After Swipe Release**:
   - **Successful swap**: Animate date header and totals card update (200ms cross-fade)
   - **Out-of-bounds**: Spring back animation (400ms ease-out)
   - **Failed**: Vibration feedback (if haptics enabled) + toast message "Nessun dato anteriore" (No earlier data)

### 4.4 Accessibility for Gestures

- **Fallback buttons** always visible and fully functional (Previous/Next arrow buttons in header)
- **Screen reader hints**: 
  - Initial swipe gesture hint: "Scorri a destra per il giorno precedente, a sinistra per il prossimo" (Swipe right for previous day, left for next)
  - After swipe: "Mostrando {date}" (Showing {date})
- **Keyboard navigation**: Tab to Previous/Next buttons; Enter/Space to activate

---

## 5. Override Nutrients Dialog

### 5.1 Dialog Structure

**Type**: Material 3 AlertDialog  
**Title**: `"Modifica valori nutrizionali"` (Edit nutrient values)  
**Icon**: `Icons.Filled.Edit` (optional, top-aligned)  
**Width**: 90% of screen (max 500dp on large screens)  
**testTag**: `"meal_override_dialog_<mealId>"`

### 5.2 Dialog Content

```
┌─────────────────────────────────────────────┐
│  Modifica valori nutrizionali              │  ← Title
├─────────────────────────────────────────────┤
│  Pane Tostato (150g)                        │  ← Meal identifier (reference)
│  (Di seguito i valori correnti per porzione)│  ← Subtitle
│                                              │
│  MACRONUTRIENTS                             │  ← Section label
│  ├─ Energy (kcal)      [________] kcal      │  ← Text input with unit
│  ├─ Protein (g)        [________] g         │
│  ├─ Carbs (g)          [________] g         │
│  └─ Fat (g)            [________] g         │
│                                              │
│  SATURATED FAT & SUGAR                      │  ← Section label
│  ├─ Saturated Fat (g)  [________] g         │
│  └─ Sugar (g)          [________] g         │
│                                              │
│  MINERALS                                   │  ← Section label
│  ├─ Iron (mg)          [________] mg        │
│  ├─ Calcium (mg)       [________] mg        │
│  ├─ Magnesium (mg)     [________] mg        │
│  └─ Zinc (mg)          [________] mg        │
│                                              │
│  VITAMINS                                   │  ← Section label
│  ├─ Vitamin C (mg)     [________] mg        │
│  ├─ Vitamin D (mcg)    [________] mcg       │
│  └─ Vitamin B12 (mcg)  [________] mcg       │
│                                              │
│  [ ] Save as Manual values (checkbox)       │  ← Metadata
│                                              │
├─────────────────────────────────────────────┤
│ [Cancel] [Save Changes]                     │  ← Action buttons
└─────────────────────────────────────────────┘
```

### 5.3 Text Input Fields

- **Type**: Material 3 TextField (outlined style)
- **Input Type**: Number (decimal allowed, 1-2 decimal places)
- **Placeholder**: Current value or "0.0"
- **Keyboard Type**: keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
- **Validation**: 
  - Non-negative numbers only
  - Max 9999.99 (reasonable upper bound for nutrients)
  - Show error message: "Valore non valido" (Invalid value)
- **Unit Label**: Right-aligned in or after the field (kcal, g, mg, mcg)
- **Height**: 56dp (standard Material 3 TextField height)
- **Spacing**: 12dp between fields

### 5.4 Dialog Actions

**Cancel Button** (`[Annulla]`)
- Material 3 TextButton
- onClick: Dismiss dialog, discard changes
- testTag: `"meal_override_cancel_btn"`

**Save Changes Button** (`[Salva modifiche]`)
- Material 3 Button (filled)
- Color: primary
- onClick: 
  1. Validate all inputs
  2. Save to NutritionOverrideEntity
  3. Recalculate daily totals
  4. Update meal card nutrients display
  5. Close dialog with success toast
- Loading state: Show progress spinner during save (async operation)
- testTag: `"meal_override_save_btn"`
- Disabled state: Until at least one field is modified

### 5.5 Metadata & Clarity

**"Save as Manual values" Checkbox**:
- Label: `stringResource(R.string.meal_override_manual_source)`
- Checked by default when any override is entered
- Updates `overrideSource` to "Manual" (vs "OFF" for OFF database values)
- Used for UI indicator: Show "Manual override" badge on meal card after save

**Current Values Display**:
- Subtitle: "(Di seguito i valori correnti per porzione)" — "Below are the current values per portion"
- Source indicator: "Da: OFF" or "Da: Manual" (From: OFF or Manual)
- Reference: Link to FAQs if OFF value seems incorrect (optional, low priority)

---

## 6. Meal Deletion Confirmation Dialog

**Trigger**: User taps Delete button on meal card  
**Type**: Material 3 AlertDialog  
**Title**: `"Eliminare questo pasto?"` (Delete this meal?)  
**Icon**: `Icons.Filled.Warning` or `Icons.Filled.Delete`, error color  
**testTag**: `"meal_deletion_confirmation_<mealId>"`

### Dialog Content

```
┌─────────────────────────────────────────┐
│  ⚠️ Eliminare questo pasto?             │  ← Title
├─────────────────────────────────────────┤
│  Non puoi annullare questa azione.      │  ← Warning message
│  (You cannot undo this action)          │
│                                          │
│  {Food Name}  {Portion}                 │  ← Meal detail for clarity
│                                          │
├─────────────────────────────────────────┤
│ [Cancel] [Delete permanently]           │  ← Buttons
└─────────────────────────────────────────┘
```

### Dialog Actions

**Cancel Button** (`[Annulla]`)
- Material 3 TextButton
- onClick: Dismiss dialog, no changes
- testTag: `"meal_delete_cancel_btn"`

**Delete Button** (`[Elimina definitivamente]`)
- Material 3 Button (filled)
- Color: error (red)
- onClick: 
  1. Delete meal from database
  2. Recalculate daily totals
  3. Refresh UI state
  4. Show success toast: "Pasto eliminato" (Meal deleted)
  5. Close dialog
- Loading state: Show spinner during delete
- testTag: `"meal_delete_confirm_btn"`

---

## 7. Dashboard Macronutrient Card Tap Integration

### 7.1 Card Modification

**Current state**: Non-interactive card showing kcal, carbs, fat, protein percentages

**New state**: 
- Add **Material 3 ripple effect** on tap (implicit via Card click modifier)
- Add **visual feedback**: slight scale/elevation increase on press
- Maintain existing appearance (no style changes, only interactivity)

### 7.2 Interaction Behavior

**On Tap**:
1. Show ripple feedback (Material 3 default)
2. Navigate to History tab
3. Auto-select **today's date** (current date, centered in view)
4. Animate transition (fade or slide)

**Implementation**:
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                // Navigate to History with today's date
                onNavigateToHistory()
            }
        },
    colors = CardDefaults.cardColors(...),
) {
    // Existing card content (unchanged)
}
```

**testTag**: `"dashboard_macro_card_tap"`

### 7.3 Navigation Flow

- **Target State**: HistoryViewModel with selectedIndex = 0 (today)
- **Animation**: 300ms transition (fade + slide-up)
- **Screen**: History tab auto-scrolls if needed (ensure daily totals visible)

---

## 8. Responsive Design

### 8.1 Breakpoints

| Breakpoint | Screen Size | Device Examples | Layout Changes |
|------------|-------------|-----------------|-----------------|
| **Small** | 320–411dp | Pixel 3a, older devices | Single column, collapsed nutrient sections |
| **Medium** | 411–600dp | Pixel 5, small tablets | Slight expansion, some sections auto-expand |
| **Large** | 600dp+ | Tablets, landscape | Multi-column, side-by-side sections, tabs |

### 8.2 Layout Adaptation

#### Small (320–411dp):
- **History Header**: Stacked (date on separate line if space tight)
- **Daily Totals Card**: Compact, 2-column grid for macros
- **Nutrient Sections**: All collapsed by default (except macros)
- **Meal Cards**: 
  - Full width, 16.dp padding
  - Macros only (always visible)
  - Enriched sections collapsed
  - Action buttons stack vertically if needed (≤48dp width for touch)
- **Text sizes**: bodyMedium (14sp) for nutrients, preserved for readability

#### Medium (411–600dp):
- **History Header**: Single-line date
- **Daily Totals Card**: 3-column grid for macros
- **Nutrient Sections**: Saturated Fat & Sugar auto-expanded; Minerals/Vitamins collapsed
- **Meal Cards**: Full width, slightly larger fonts
- **Action buttons**: Horizontal row, no stacking

#### Large (600dp+):
- **History Header**: Luxe spacing, larger date
- **Daily Totals Card**: 4-column macros + side panel for enriched (optional)
- **Nutrient Sections**: Tabs instead of collapsible sections
- **Meal Cards**: Max width 600dp, centered with side padding
- **Action buttons**: Generous spacing (24dp between buttons)
- **Text sizes**: bodyLarge (16sp) for nutrients

### 8.3 Dynamic Spacing

Use `Modifier.padding()` with responsive calculation:
```kotlin
val horizontalPadding = when {
    screenWidth < 411.dp -> 12.dp
    screenWidth < 600.dp -> 16.dp
    else -> 24.dp
}
```

---

## 9. Accessibility (WCAG 2.1 AA)

### 9.1 Color Contrast

**Minimum Ratios**:
- Normal text vs background: **4.5:1** 
- Large text (18sp+) vs background: **3:1**
- UI components (icons, buttons): **3:1**

**Verification**:
- Test in Material 3 light and dark themes
- Use Android Studio Accessibility Color Contrast Checker
- Flag any failures; adjust color tokens if needed

### 9.2 Focus Indicators

**Required for all interactive elements**:
- **Buttons**: Visible focus outline (Material 3 default: 2-3dp border)
- **TextFields**: Focus box outline (Material 3 default)
- **Cards**: Not selectable by default; consider `clickable()` if needed
- **Focus color**: Use `colorScheme.outline` or primary with transparency

**Focus order** (Tab navigation):
1. Previous Day button
2. Next Day button
3. Add Meal button (if empty state shown)
4. For each meal card:
   - Edit button
   - Delete button
   - Override button
5. Bottom FAB (if present)

**Keyboard support**:
- Tab: Move to next element
- Shift+Tab: Move to previous element
- Enter/Space: Activate button
- Escape: Close dialogs

### 9.3 Screen Reader Text (Semantics)

Use `.semantics()` modifier and `contentDescription` parameter:

```kotlin
// Date header
Text(
    text = dateString,
    modifier = Modifier.semantics {
        contentDescription = "Showing: $dateString, tap Previous or Next to navigate"
    }
)

// Previous button
Button(
    onClick = onPrevious,
    modifier = Modifier.semantics {
        contentDescription = "Previous day"
    }
) { Icon(...) }

// Meal card
Column(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "$foodName ($portion). $kcal kcal, $protein g protein, $carbs g carbs, $fat g fat"
    }
) {
    // Card content
}

// Override button
Button(
    onClick = onOverride,
    modifier = Modifier.semantics {
        contentDescription = "Edit nutrient values for $foodName"
    }
) { Text("Override") }
```

### 9.4 Semantic Hierarchy

- Use Material 3 typography styles (headlineSmall, titleMedium, etc.) — screen readers respect this info
- Headings: `Modifier.semantics { this.heading() }`
- Labels + inputs: Form groups with semantic nesting
- Lists: Use `.semantics(mergeDescendants = true)` for meal card groups

### 9.5 Dark Mode Support

- Ensure all text + icons meet 4.5:1 contrast in dark theme
- Test with `darkColorScheme()` via theme toggle in Settings
- Avoid pure black text (#000000) on dark backgrounds; use `onSurface` instead

### 9.6 Alternative Text for Icons

All icons must have meaningful `contentDescription`:
```kotlin
Icon(
    imageVector = Icons.Filled.Delete,
    contentDescription = "Delete",  // Required
    tint = MaterialTheme.colorScheme.error
)
```

---

## 10. Interaction States

### 10.1 Button States

| State | Visual | Behavior | Color |
|-------|--------|----------|-------|
| **Enabled** (normal) | Full opacity, interactive | Tap responds | primary |
| **Disabled** | 50% opacity, non-responsive | Tap ignored | outline (grayed) |
| **Pressed** | Ripple effect, slightly darker | Immediate visual (200ms ripple) | onPrimary |
| **Focused** (keyboard) | Outline box, 2dp border | Shows focus indicator | outline |
| **Loading** | Spinner overlay or pulse animation | Disabled during operation | primary + animation |

### 10.2 Text Field States

| State | Visual | Behavior |
|-------|--------|----------|
| **Empty** | Hint placeholder visible | User can type |
| **Filled** | User text visible | Editable |
| **Error** | Red outline + error message below | User corrects |
| **Focused** | Blue outline + cursor | Text editable |
| **Disabled** | Grayed + read-only | No interaction |

### 10.3 Meal Card States

| State | Visual | Behavior |
|-------|--------|----------|
| **Collapsed section** | `▶` chevron, hidden content | Click to expand |
| **Expanded section** | `▼` chevron, visible content | Click to collapse |
| **Overridden nutrient** | Bold + altered color or badge | Shows "Manual" indicator |
| **Loading (delete/edit)** | Dim overlay + spinner | Disabled until operation complete |

---

## 11. Typography & Text Styles

### 11.1 String Resources (Italian Localization)

Add to `res/values-it/strings.xml`:

```xml
<!-- History Screen -->
<string name="history_title">Storico</string>
<string name="history_previous">← Precedente</string>
<string name="history_next">Prossimo →</string>
<string name="history_date_format">EEEE, d MMMM yyyy</string> <!-- e.g., "Lunedì, 13 aprile 2026" -->
<string name="history_gesture_hint">Scorri per navigare tra i giorni</string> <!-- "Swipe to navigate between days" -->

<!-- Daily Totals -->
<string name="history_daily_totals">Totali Giornalieri</string>
<string name="history_macronutrients">Macronutrienti</string>
<string name="history_enriched_nutrients">Nutrienti Arricchiti</string>
<string name="history_saturated_fat_sugar">Grassi Saturi e Zuccheri</string>
<string name="history_minerals">Minerali</string>
<string name="history_vitamins">Vitamine</string>

<!-- Meal Card -->
<string name="meal_type_breakfast">Colazione</string>
<string name="meal_type_lunch">Pranzo</string>
<string name="meal_type_dinner">Cena</string>
<string name="meal_type_snack">Snack</string>
<string name="meal_edit">Modifica</string>
<string name="meal_delete">Elimina</string>
<string name="meal_override_nutrients">Modifica valori nutrizionali</string>
<string name="meal_portion">Porzione</string>

<!-- Override Dialog -->
<string name="meal_override_dialog_title">Modifica valori nutrizionali</string>
<string name="meal_override_subtitle">Di seguito i valori correnti per porzione</string>
<string name="meal_override_save_as_manual">Salva come valori manuali</string>
<string name="meal_override_cancel">Annulla</string>
<string name="meal_override_save">Salva modifiche</string>

<!-- Deletion Confirmation -->
<string name="meal_delete_confirm_title">Eliminare questo pasto?</string>
<string name="meal_delete_warning">Non puoi annullare questa azione.</string>
<string name="meal_delete_cancel">Annulla</string>
<string name="meal_delete_confirm">Elimina definitivamente</string>
<string name="meal_deleted_toast">Pasto eliminato</string>

<!-- Empty State -->
<string name="history_empty_state_title">Nessun pasto registrato</string>
<string name="history_empty_state_subtitle">Vuoi aggiungere un pasto?</string>
<string name="history_add_meal">+ Aggiungi pasto</string>

<!-- Accessibility -->
<string name="history_nav_previous_label">Vai al giorno precedente</string>
<string name="history_nav_next_label">Vai al prossimo giorno</string>
<string name="meal_edit_description">Modifica questo pasto</string>
<string name="meal_delete_description">Elimina questo pasto</string>
```

### 11.2 Number Formatting

- **Kcal**: No decimals (e.g., "245 kcal")
- **Macronutrients (g)**: 1 decimal (e.g., "42.1 g")
- **Minerals (mg, mcg)**: 1 decimal (e.g., "2.1 mg")
- **Vitamins (mg, mcg)**: 1 decimal (e.g., "8.5 mg")
- **Null values**: "—" (em-dash) in onSurfaceVariant color

---

## 12. Animation & Transitions

### 12.1 Swipe Animation

- **Duration**: 300ms
- **Type**: Easing (ease-in-out)
- **Properties**:
  - Meal cards: translate horizontally (incoming from opposite direction)
  - Date header: cross-fade
  - Daily totals: cross-fade

### 12.2 Dialog Animations

- **Appear**: Scale-in (start at 0.9 scale, animate to 1.0 over 200ms)
- **Dismiss**: Scale-out + fade (reverse)

### 12.3 Expansion Animations

- **Nutrient sections expand/collapse**: Height animation (200ms)
- **Chevron icon**: Rotate animation (180° over 200ms)

### 12.4 List Scroll Behavior

- Smooth scroll when auto-scrolling to see newly added meal
- No snap-to-top on navigation; preserve user's scroll position

---

## 13. Material 3 Components Reference

### 13.1 Component Inventory

| Component | Usage | Material 3 Class |
|-----------|-------|------------------|
| **Card** | Meal entries, daily totals | `androidx.compose.material3.Card` |
| **Button** | Primary actions (Save, Delete) | `androidx.compose.material3.Button` |
| **TextButton** | Secondary actions (Edit, Cancel) | `androidx.compose.material3.TextButton` |
| **OutlinedButton** | Alternative actions | `androidx.compose.material3.OutlinedButton` |
| **TextField** | Nutrient override input | `androidx.compose.material3.TextField` |
| **AlertDialog** | Confirmations, overrides | `androidx.compose.material3.AlertDialog` |
| **Icon** | Meal type, action icons | `androidx.compose.material3.Icon` + Material Icons |
| **Text** | All typography | `androidx.compose.material3.Text` |
| **Surface** | Main container | `androidx.compose.material3.Surface` |
| **NavigationBar** | Existing bottom nav (unchanged) | `androidx.compose.material3.NavigationBar` |

### 13.2 No New Custom Components

- Do NOT create custom card or button variants
- Use standard Material 3 classes with modifier overrides for styling
- Leverage existing design tokens (colors, typography) without new CSS classes or theming overrides

---

## 14. Testing & Validation

### 14.1 Manual Testing Checklist

- [ ] Swipe right: Advances to previous day (or no-op at boundary)
- [ ] Swipe left: Advances to next day (or no-op at today)
- [ ] Daily totals recalculate correctly after meal edit/delete/add
- [ ] Empty state displays when no meals for day
- [ ] Edit button opens MealLoggingScreen with prepopulated values
- [ ] Delete button shows confirmation dialog
- [ ] Override button opens dialog with all 12 nutrient fields
- [ ] Dashboard card tap navigates to History with today selected
- [ ] Input validation in override dialog prevents invalid entries
- [ ] Responsive layout works on 320dp, 411dp, and tablet screens
- [ ] Focus indicators visible on all buttons (keyboard navigation)
- [ ] Screen reader announces meal items, button labels, and section headers
- [ ] Color contrast met (4.5:1) in light and dark themes
- [ ] Swipe gesture feels smooth; no jank on mid-range devices
- [ ] All Italian strings display correctly (no encoding issues)
- [ ] Back button works from override/deletion dialogs (dismisses without saving)
- [ ] Rapid swipes debounced; no state flicker
- [ ] Null/missing nutrients displayed as "—" (not crashes)

### 14.2 Accessibility Testing

- [ ] Tab key navigates all interactive elements in correct order
- [ ] Shift+Tab reverses navigation
- [ ] Enter/Space activates buttons
- [ ] Escape closes dialogs
- [ ] Screen reader reads meal names, portions, and nutrients
- [ ] Color-blind friendly (no red/green only distinctions; use icons or patterns)
- [ ] Text size preference respected (larger text still readable)
- [ ] Dark mode contrast sufficient (test on actual device)

### 14.3 Performance Benchmarks

- [ ] Swipe navigation: < 300ms frame time (smooth 60 fps)
- [ ] 90-day query: < 500ms (measure via Profiler)
- [ ] Meal deletion: < 1s total (including DB + UI refresh)
- [ ] Nutrient override save: < 1s (including validation + DB + recalc)

---

## 15. Known Limitations & Future Enhancements

### 15.1 Out of Scope (v1)

- Bulk operations (select multiple meals, delete all)
- Meal templating or quick-add favorites
- Custom nutrient tracking (user-defined minerals/vitamins)
- Export/PDF generation of history
- Syncing across devices
- Social sharing

### 15.2 Possible Future Work

- **Gesture hints refinement**: User preference to disable or auto-hide faster
- **Tabs vs collapsible SurveyMonkey or A/B test with users** to finalize nutrient layout
- **Offline-first caching**: Pre-compute 365-day totals for faster load
- **Animations**: More sophisticated parallax or carousel swipe patterns
- **Analytics**: Track nutrient override patterns to improve OFF data quality

---

## 16. Design Rationale & Notes

### Why Collapsible Sections (Not Tabs)?

- **Mobile-first**: Easier to tap expand/collapse than switch tabs
- **Progressive disclosure**: Don't overwhelm user with 12 nutrients at once
- **Responsive**: Collapsible scales better from 320dp to 600dp+

### Why Separate Override Button (Not Inline)?

- **Intentionality**: Overrides are deliberate corrections; separate action signals importance
- **Clarity**: Inline editing mixes display + edit modes; button mode is cleaner
- **Accessibility**: Distinct button target easier to hit on touch screens
- **Feature discoverability**: Button label ("Edit nutrient values") more explicit than inline pencil icon

### Why Daily Totals Summary?

- **Key user need**: Users want quick kcal/macro overview before drilling into meals
- **Dashboard consistency**: Mirrors macro card on Dashboard tab
- **Performance**: Pre-computed daily summary (cached) vs computing from meal list

### Why Swipe + Button Navigation?

- **Familiar gesture**: Internet-standard left/right swipe
- **Accessibility**: Buttons required for keyboard + screen reader users
- **Fallback**: If swipe gesture fails or user prefers buttons, always available

### Why 12 Nutrients, Not More?

- **Data availability**: OFF API reliably provides these 12; others sparse or missing
- **Cognitive load**: 12 is threshold before UI becomes cluttered
- **Health relevance**: Core tracking for fitness + dietetic analysis

---

## 17. References & Resources

- Material 3 Design System: https://material.io/design/
- androidx.compose.material3 API: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- WCAG 2.1 AA: https://www.w3.org/WAI/WCAG21/quickref/
- Open Food Facts API: https://static.openfactsfacts.org/data/documents/API-v2.html
- Jetpack Compose Gestures: https://developer.android.com/jetpack/compose/touch-input/pointer-input/pointer-input-detection

---

## Appendix: Color Token Definitions

```kotlin
// Light theme (Material 3 defaults are sufficient; override only if custom palette needed)
lightColorScheme(
    primary = Color(0xFF6750A4),           // Purple
    onPrimary = Color(0xFFFFFFFF),         // White
    primaryContainer = Color(0xFFEADDFF),  // Light purple
    onPrimaryContainer = Color(0xFF21005D), // Dark purple
    secondary = Color(0xFF625B71),         // Gray-blue
    tertiary = Color(0xFF7D5260),          // Mauve (for minerals/vitamins)
    error = Color(0xFFB3261E),             // Red
    errorContainer = Color(0xFFF9DEDC),    // Light red
    background = Color(0xFFFFFBFE),        // Almost white
    surface = Color(0xFFFFFBFE),           // Same as background
    surfaceVariant = Color(0xFFEAE7F0),    // Light gray
    outline = Color(0xFF79747E),           // Medium gray
)

// Dark theme (inverse of light)
darkColorScheme(
    primary = Color(0xFFD0BCFF),           // Light purple
    onPrimary = Color(0xFF371E55),         // Dark purple
    primaryContainer = Color(0xFF4F378B),  // Darker purple
    onPrimaryContainer = Color(0xFFEADDFF), // Light purple
    secondary = Color(0xFFCCC7D0),         // Light gray-blue
    tertiary = Color(0xFFF2B8DD),          // Light mauve
    error = Color(0xFFF2B8B5),             // Light red
    errorContainer = Color(0xFF8C1d18),    // Dark red
    background = Color(0xFF1C1B1F),        // Almost black
    surface = Color(0xFF1C1B1F),           // Same as background
    surfaceVariant = Color(0xFF49454E),    // Dark gray
    outline = Color(0xFF9E9DA0),           // Light gray
)
```

---

**End of Design Specification**
