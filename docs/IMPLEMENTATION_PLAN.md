# Photo Import Wizard - Implementation Plan

**Created**: 2026-04-08  
**Last Updated**: 2026-05-17  
**Status**: Planning
**Version**: 1.3

---

## Overview

This document outlines the implementation plan for a comprehensive photo import wizard that provides users with fine-grained control over photo detection, refinement, and transformation.

> **Note on ML Detection:** For detection mode selection (Computer Vision / Bounding Box / Pose / Hybrid), ONNX model integration, per-photo detection settings, crop modes, and training data export, see [ML_DETECTION_INTEGRATION_PLAN.md](./ML_DETECTION_INTEGRATION_PLAN.md). The CV auto-detection toggle described in Section 0.2 below remains valid as the COMPUTER_VISION mode within the broader ML detection settings system.
>
> **Note on Camera Device Import:** For importing from PTP/MTP camera devices that don't mount as USB mass storage (e.g., Fujifilm, Canon in PTP mode), see [CAMERA_DEVICE_IMPORT_PLAN.md](./CAMERA_DEVICE_IMPORT_PLAN.md). The folder-based source selection described in this document remains the default; camera device import adds an "Import from Camera" button alongside the existing "Import from Folder" button. When a camera is plugged in at startup, the app auto-selects the camera source.

---

## 0. Import Screen - Mode Selection & CV Toggle

**Purpose**: Configure import mode and CV detection settings before entering the refinement workflow.

### 0.1 Import Mode Selection

**Modes**:
| Mode | Description |
|------|-------------|
| **Photo Scan** | Scans a flatbed/camera image for multiple photo bounding boxes |
| **Single Photo** | Imports a single photo (skip multi-box detection) |

**Photo Scan Mode**:
- Detects multiple photo boundaries using CV
- Allows manual refinement of detected boxes
- Supports adding/removing boxes
- Includes 4-point manual selection feature

### 0.2 CV Auto-Detection Toggle

> **See also:** [ML_DETECTION_INTEGRATION_PLAN.md](./ML_DETECTION_INTEGRATION_PLAN.md) — The CV toggle described here is superseded by the DetectionMode settings system (Section 3), which offers Computer Vision, Bounding Box, Pose, and Hybrid modes with full parameter configuration.

**Setting**: Enable/disable automatic CV-based bounding box detection.

**UI**: Toggle switch or checkbox labeled **"Auto-detect bounding boxes"**

| State | Behavior |
|-------|----------|
| **Enabled** (default) | Run CV detection on image load; show detected bounding boxes |
| **Disabled** | Skip CV detection; start with empty box list; user adds boxes manually |

**Location**: In Photo Scan mode settings area on the Import screen.

**UI Layout**:
```
┌─────────────────────────────────────────────────────────────┐
│                        Import Screen                         │
│                                                              │
│  Import Mode:                                                 │
│  ┌─────────────────────┐  ┌─────────────────────┐          │
│  │   ○ Photo Scan       │  │   ○ Single Photo    │          │
│  │   (multi-box)        │  │   (single image)    │          │
│  └─────────────────────┘  └─────────────────────┘          │
│                                                              │
│  Photo Scan Options:                                          │
│  ┌──────────────────────────────────────────────────┐      │
│  │  ☑ Auto-detect bounding boxes                     │      │
│  │     When enabled, CV will find photo boundaries   │      │
│  │     automatically. Disable to start with empty.   │      │
│  └──────────────────────────────────────────────────┘      │
│                                                              │
│  [Import Image]                                              │
└─────────────────────────────────────────────────────────────┘
```

### 0.3 4-Point Manual Bounding Box Selection

**Purpose**: Allow users to manually define a bounding box by clicking 4 corner points instead of using auto-detection.

**Trigger**: Click "Add 4-Point Box" button (available after entering Photo Scan mode)

**Button Location**: In the overview refinement view, alongside other box manipulation controls.

**Workflow**:
1. User clicks **"4-Point" button** (icon: ◇◇◇◇ or "4pt")
2. Canvas enters **4-point selection mode**:
   - Cursor changes to crosshair (+)
   - Status message: "Click to set point 1 of 4"
3. User clicks **Point 1** (e.g., top-left corner of photo)
4. Status updates: "Click to set point 2 of 4"
5. User clicks **Point 2** (e.g., top-right corner)
6. Status updates: "Click to set point 3 of 4"
7. User clicks **Point 3** (e.g., bottom-right corner)
8. Status updates: "Click to set point 4 of 4"
9. User clicks **Point 4** (e.g., bottom-left corner)
10. System creates a quadrilateral from the 4 points
11. System adds the new bounding box to the list
12. Canvas exits 4-point selection mode
13. New box is selected and highlighted

**Cancel Behavior**:
- Press `Escape` at any time to cancel 4-point selection
- Press `Backspace` to remove the last placed point
- Click "Cancel" button to exit without adding box
- Returns to normal mode without adding a box

**4-Point Button UI**:
```
┌─────────────────────────────────────────────────────────────┐
│ [← Back]  [4-Point]  [Add Box]      [🔍-][⛶][🔍+]  [ⓘ]     │
│           ◇◇◇◇                                  [🗑️ Delete] │
└─────────────────────────────────────────────────────────────┘
```

**Visual Feedback During Selection**:
- Each placed point shows a numbered marker (① ② ③ ④)
- Lines connecting placed points update in real-time
- Current point being placed has pulsing indicator
- Preview quadrilateral shown after 3rd point is placed

**State Indicator**:
```
Point 1 of 4   [○────────○]
                 ╱          ╲
               ╱            ╲
             ○              ○
             
Point 4 of 4   ○────────○────○ (complete)
```

**Keyboard Shortcuts**:
| Key | Action |
|-----|--------|
| Escape | Cancel 4-point selection |
| Backspace | Remove last placed point |
| Enter | Confirm selection (when 4 points placed) |

**Combined with CV Detection**:
- Users can mix auto-detected and manually drawn boxes
- Auto-detected boxes can be refined or deleted
- 4-point boxes can be refined via normal corner editing
- Works with CV toggle: if CV disabled, only 4-point/manual boxes available

### 0.4 Add Box Button (Click-to-Add)

**Existing Feature** (from Section 2.2):
- Click on empty image area to add a box (30% width, 3:2 aspect ratio)
- Available in both CV-enabled and CV-disabled modes

**4-Point Button vs Add Box Button**:
| Button | Use Case |
|--------|----------|
| **Add Box** | Quick rectangular box; auto-calculates size from click position |
| **4-Point** | Manual corners for perspective-distorted photos or irregular shapes |

---

## 1. Loading Animation

**Requirement**: Use `loading.webp` as the loading animation.

**Implementation**:
- Locate the existing loading animation resource
- Replace or configure the application to use `loading.webp`
- Ensure proper loading states during:
  - Initial app startup
  - Image loading/cropping
  - Transformation processing
  - File saving

**Files to modify**:
- `src/main/resources/icons/loading.webp` (verify exists)
- Application initialization code

---

## 2. Full-Screen Bounding Box Refinement

**Requirement**: Full-screen UI for refining detected photo bounding boxes with zoom-to-fit functionality.

### 2.1 Initial View - Detection Overview

**Behavior**:
- Display the full scanned image with all detected bounding boxes overlaid
- Bounding boxes are initially drawn as semi-transparent rectangles with corner handles
- User can interact with boxes to refine or add/remove them
- **No bounding box selected by default**

**Selection Behavior**:
- **No selection on load**: When overview opens, no bounding box is selected
- **Click to select**: Click on a bounding box to select it (shows selection highlight)
- **Click outside to deselect**: Clicking on image area not containing a box deselects current selection
- **Select for actions**: Must select a box before pressing Delete to remove it
- **Selection indicator**: Selected box has highlighted border (e.g., brighter color, thicker line)

**Zoom Controls**:
Zoom in/out buttons positioned in top-right corner of view:

| Button | Icon | Action |
|--------|------|--------|
| Zoom In | 🔍+ | Increase zoom level (1.25x multiplier) |
| Zoom Out | 🔍- | Decrease zoom level (0.8x multiplier) |
| Fit to View | ⛶ | Reset zoom to fit entire image in viewport |

**Scroll-to-Zoom Behavior**:
- **When NOT hovering over a bounding box**: Scroll wheel zooms in/out centered on cursor position
- **When hovering over a bounding box**: Scroll wheel expands/contracts that box (as designed)
- **Zoom clamped**: Minimum 0.1x, Maximum 10x
- **Pan while zoomed**: Click and drag empty space to pan when zoomed in beyond fit

**Zoom-Fit Constraint**:
- When zooming, the **image stays within the viewport** - it does not overflow
- Content is scaled down as needed to fit
- Pan controls allow exploring the image when zoomed in

**Delete Behavior**:
- Must **select a bounding box first** before it can be deleted
- **Select**: Click on any bounding box to select it (highlighted border)
- **Delete**: Press `Delete` or `Backspace` key to remove selected box
- **No selection = no delete**: If no box is selected, delete key does nothing
- **Deselect**: Press `Escape` or click outside any bounding box
- **All boxes deleted**: Show empty state with "Click to add a box" prompt

**Zoom UI Layout**:
```
┌─────────────────────────────────────────────────────────────┐
│ [← Back]  [4-Point]  [Add Box]      [🔍-] [⛶] [🔍+]  [ⓘ]  │
│           ◇◇◇◇           □□□□                               │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │         Full Scanned Image                          │   │
│  │         with Bounding Boxes                         │   │
│  │                                                     │   │
│  │    ┌────────┐           ┌────────┐                │   │
│  │    │ Photo 1│           │ Photo 2 │                │   │
│  │    └────────┘           └────────┘                │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Zoom: 100%    [🗑️ Delete]                    [To Summary]  │
│             [NORMAL]                                        │
└─────────────────────────────────────────────────────────────┘
```

**Button Functions**:
| Button | Purpose |
|--------|---------|
| ← Back | Return to import screen |
| 4-Point (◇◇◇◇) | Enter 4-point manual bounding box mode |
| Add Box | Click once to place rectangular box (30% width, 3:2 ratio) |
| 🔍- / 🔍+ | Zoom out / zoom in |
| ⛶ Fit | Reset zoom to fit entire image |
| 🗑️ Delete | Delete selected bounding box |
| ⓘ | Show keyboard/mouse controls help |
| [To Summary] | Proceed to summary screen with correction options |

**4-Point Mode vs Add Box Mode**:
| Mode | Trigger | Behavior |
|------|---------|----------|
| 4-Point | Click "4-Point" button | Click 4 corners to define any quadrilateral |
| Add Box | Click "Add Box" button | Click once to place rectangular box |
| Refinement | Click on existing box | Enter zoomed refinement mode for that box |

**Keyboard Shortcuts** (Overview):
| Key | Action |
|-----|--------|
| D | Enter 4-point mode (4-Point button) |
| A | Enter add-box mode (Add Box button) |
| Delete | Delete selected bounding box (if selected) |
| Escape | Cancel 4-point/add-box mode, or deselect |
| Ctrl+Plus | Zoom in |
| Ctrl+Minus | Zoom out |
| Ctrl+0 | Fit to view |
| Ctrl+A | Select all bounding boxes |

### 2.2 Add Bounding Box

**Trigger**: Click on image area not intersecting any existing bounding box.

**Behavior**:
1. Detect click position (x, y)
2. Verify click does NOT intersect any existing bounding box
3. Create new bounding box:
   - Width: 30% of image width
   - Height: Based on **default aspect ratio (3:2)**
   - Center: Clicked position
   - Add to list of bounding boxes

**Default Aspect Ratio**:
- Default: **3:2** (standard 4x6 photo ratio)
- Common photo ratios available: 1:1, 3:2, 2:3, 4:3, 3:4, 5:4, 16:9
- User can change via aspect ratio dropdown in summary screen

**Minimum Bounding Box Size**:
- Minimum dimension: **10% of image width/height** (whichever axis is larger)
- If clicked position would create box smaller than minimum, reject creation
- Visual feedback: Show "Too small" message if box would be below minimum

### 2.3 Remove Bounding Box

**Trigger**: Click on an existing bounding box, then press Delete/Backspace or click trash icon.

**Behavior**:
1. Remove bounding box from list
2. Update UI to reflect removal

### 2.4 Zoom to Bounding Box for Refinement

**Trigger**: Click on a bounding box (not on handles).

**Behavior**:
1. Calculate bounding box + 20% margin in all directions
2. Zoom/pan canvas to show this region full-screen
3. Enable corner refinement mode

**Return to Overview**:
- **"← Back" button** (top-left corner): Return to full image overview showing all bounding boxes
- **Keyboard**: Press `Escape` to return to overview (when not selecting a corner or moving box)
- **Back navigation preserves changes** made in refinement mode

**Zoom Controls** (in refinement mode):
| Button | Icon | Action |
|--------|------|--------|
| Zoom In | 🔍+ | Increase zoom level (1.25x) |
| Zoom Out | 🔍- | Decrease zoom level (0.8x) |
| Fit to Box | ⛶ | Reset to fit bounding box + 20% margin |

**Scroll-to-Zoom Behavior** (refinement mode):
- **Scroll wheel**: Zooms in/out centered on cursor position
- **Zoom clamped**: Minimum to show entire box, maximum 10x
- **Image stays within viewport** - does not overflow bounds
- **Zoom does not affect bounding box size** - only the view magnification

**Delete Bounding Box** (refinement mode):
- **Delete/Backspace key**: Remove current bounding box, return to overview
- **Trash icon** (toolbar): Delete selected box
- **Right-click context menu**: "Delete" option
- **Behavior**: After deletion, return to overview with remaining boxes
- **Confirmation**: No confirmation dialog (undo available via Ctrl+Z)

### 2.5 Corner Refinement Mode

**UI Elements**:
- Zoomed view of selected bounding box + 20% margin
- Four draggable corner handles (top-left, top-right, bottom-right, bottom-left)
- Visual feedback showing current corner positions
- **Info icon (ⓘ)**: Displays keyboard/mouse interaction tooltip

**Interactions**:

| Action | Input | Result |
|--------|-------|--------|
| Move entire box | Click and drag anywhere on bounding box | Translates all four corners by same amount |
| Resize box | Click and drag a corner handle | Moves only that corner |
| Move selected corner | Arrow keys (when corner selected) | Moves selected corner by 1px per press |
| Expand box outward | Mouse scroll wheel | Scales box outward from center |
| Rotate box | Shift + mouse scroll | Rotates box around center |
| Select corner | Click within buffer zone of corner | Highlights corner for arrow key movement |
| Navigate boxes | Left/Right arrow keys (when no corner selected) | Navigate to previous/next bounding box |
| Zoom view | 🔍+ / 🔍- buttons or Ctrl+Plus/Ctrl+Minus | Change zoom level (image stays within view) |
| Fit to box | ⛶ button or Ctrl+0 | Reset zoom to fit box + 20% margin |

**Corner Selection & Arrow Key Movement**:
- Corners have a **clickable buffer zone** (default: 20px radius from corner point)
- Clicking within buffer selects that corner
- When a corner is selected:
  - Arrow keys move that corner in the pressed direction
  - Shift + arrow key moves by 10px (larger increment)
  - Corner selection is visually indicated (highlighted handle)
  - Press Escape or click outside box to deselect corner

**Arrow Key Movement Increment**:
| Key | Normal | + Shift |
|-----|--------|---------|
| ↑ | 1px up | 10px up |
| ↓ | 1px down | 10px down |
| ← | 1px left | 10px left |
| → | 1px right | 10px right |

**Scroll Interactions**:
- **Mouse scroll**: Expand/contract bounding box
  - Scroll up = expand outward (larger)
  - Scroll down = contract inward (smaller)
  - Expansion is centered on bounding box center
- **Shift + scroll**: Rotate bounding box
  - Scroll up = rotate clockwise
  - Scroll down = rotate counter-clockwise
  - Rotation is around bounding box center point

**Interaction Tooltip** (ⓘ icon):
Display on hover/click of info icon, or shown when entering refinement mode:

```
┌─────────────────────────────────────────────────────────────┐
│  🖱️ Mouse Controls                                         │
│  • Click + drag box: Move entire bounding box                │
│  • Click + drag corner: Resize at that corner               │
│  • Scroll: Expand/contract box                              │
│  • Shift + scroll: Rotate box                               │
│  • Click near corner: Select corner for arrow keys          │
│                                                             │
│  ⌨️ Keyboard Controls                                       │
│  • Arrow keys: Move selected corner (1px)                   │
│  • Shift + arrows: Move selected corner (10px)             │
│  • Ctrl + ←/→: Navigate between boxes                       │
│  • Ctrl + Z: Undo    |   Ctrl + Shift + Z: Redo            │
│  • Enter: Done (return to overview)                         │
│  • Escape: Deselect corner                                  │
└─────────────────────────────────────────────────────────────┘
```

**Visual Feedback**:
- Selected corner: Highlighted handle (e.g., larger, different color)
- Hover state: Cursor changes (move, resize, pointer)
- During drag: Real-time preview of new position
- Rotation: Show angle indicator (e.g., "-3.2°") during rotation

**Navigation**:
- **"< Previous" button**: Navigate to previous bounding box
- **"> Next" button**: Navigate to next bounding box
- Current box index display (e.g., "Box 2 of 5")

**Refinement Mode - No 4-Point/Add Box**:
- **4-Point** button: NOT shown in Refinement mode (use Overview instead)
- **Add Box** button: NOT shown in Refinement mode (use Overview instead)
- Refinement is for editing an EXISTING box's corners, not creating new boxes
- To add a new box, return to Overview mode

**Exit**:
- "Done" button returns to overview with all bounding boxes visible
- Changes are preserved

### 2.6 Navigation Controls

**Buttons**:
| Button | Action |
|--------|--------|
| ← Back | Return to overview (full image with all boxes) |
| < Previous | Navigate to previous bounding box |
| Box X of Y | Current position indicator |
| > Next | Navigate to next bounding box |
| Done | Return to overview (same as ← Back) |

**Overview Mode Buttons**:
| Button | Action |
|--------|--------|
| 🔍- | Zoom out |
| ⛶ Fit | Fit image to view |
| 🔍+ | Zoom in |
| ⓘ | Show keyboard/mouse controls help |
| Delete | Delete selected bounding box (if selected) |

**Keyboard Shortcuts**:
| Key | Action |
|-----|--------|
| Arrow keys | Move selected corner (1px) |
| Shift + Arrow | Move selected corner (10px) |
| Ctrl + Left | Previous box |
| Ctrl + Right | Next box |
| Ctrl + Plus | Zoom in |
| Ctrl + Minus | Zoom out |
| Ctrl + 0 | Fit to box |
| Enter | Done (return to overview) |
| Escape | Deselect corner / Return to overview |
| Delete | Delete current bounding box |
| Ctrl+Z | Undo last corner move |
| Ctrl+Shift+Z | Redo |

**Selection Modes**:
- **No selection**: 
  - Plain arrow keys navigate between boxes
  - Escape returns to overview
  - Scroll zooms the view
- **Corner selected**: 
  - Arrow keys move that corner
  - Scroll expands/contracts the box
- **Click on box interior** → Enter "move mode" (box follows cursor)
- **Click on corner** → Select that corner
- **Click outside box** → Deselect and return to overview (from refinement mode)

**Undo/Redo Behavior**:
- Maintain stack of corner positions for each box
- Undo reverts to previous position
- Redo restores undone position
- Stack limit: 50 operations per box
- Clear redo stack on new corner move after undo

**UI Layout**:
```
┌─────────────────────────────────────────────────────────────┐
│  [← Back] [← Previous]  Box 2 of 5  [Next →]   [🔍-][⛶][🔍+] │
│                                         [🗑️ Delete]   [ⓘ]   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │                                                     │   │
│  │              Zoomed Bounding Box View                │   │
│  │              with draggable corners                  │   │
│  │                                                     │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Undo: Ctrl+Z              Rotate: Shift+Scroll           │
│  Expand: Scroll              Redo: Ctrl+Shift+Z           │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Summary Screen - Post-Refinement

**Requirement**: Display list of all bounding boxes with per-photo correction options and bulk operations.

### 3.1 Summary List View

**UI Layout**:
- Scrollable list of photo cards (one per bounding box)
- **Bulk action buttons** at the top of the list
- Each card shows:
  - **Cropped image preview (clickable thumbnail)**
  - Photo index/name (e.g., "Photo 1", "Photo 2")
  - **Aspect Ratio dropdown** (for perspective correction)
  - Correction options

**Bulk Change Buttons**:
Located at the top of the summary list, these buttons allow applying settings to all photos at once:

| Button | Action |
|--------|--------|
| Apply Perspective to All | Enable perspective correction for all photos |
| Apply Rotation to All | Enable rotation correction for all photos |
| Clear All Corrections | Disable all corrections (perspective + rotation) |
| Reset All to Default | Reset all photos to default settings (perspective, 3:2 ratio) |

**UI Layout**:
```
┌─────────────────────────────────────────────────────────────┐
│  [Apply Perspective to All]  [Apply Rotation to All]       │
│  [Clear All Corrections]    [Reset All to Default]         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  ┌──────────┐                                           ┌──┐│
│  │ [Preview]│  Photo 1 (index _1)                      │  ││
│  │    📷    │  ────────────────────────────────────────│  ││
│  │  Click   │  Aspect Ratio: [3:2 ▼]                   │  ││
│  │  to edit │                                          │  ││
│  └──────────┘  ☑ Perspective Correction                 │  ││
│                ☐ Rotation Correction                     │  ││
│                [↺ L]  [R ↻]                             │  ││
│                                                     └──┘──││
│  ┌──────────┐                                           ┌──┐│
│  │ [Preview]│  Photo 2 (index _2)                      │  ││
│  └──────────┘  ...                                      └──┘│
└─────────────────────────────────────────────────────────────┘
```

**Button Behavior**:
- **Apply Perspective to All**: Sets all photos to use perspective correction, disables rotation on all
- **Apply Rotation to All**: Sets all photos to use rotation correction, disables perspective on all
- **Clear All Corrections**: Removes all correction settings (photos will be cropped but not corrected)
- **Reset All to Default**: Returns all photos to default state (perspective enabled, 3:2 aspect ratio)

**Photo Card Structure**:
```
┌─────────────────────────────────────────────────────────────────┐
│  ┌──────────┐  Photo 1 (index _1)                               │
│  │ [Preview]│  ─────────────────────────────────────────────────  │
│  │   📷    │  Aspect Ratio: [3:2 ▼]    [↺ L]  [R ↻]            │
│  │  Click   │                                                     │
│  │  to edit │  ☐ Perspective Correction   ☐ Rotation Correction │
│  └──────────┘                                                    │
└─────────────────────────────────────────────────────────────────┘
```

**Photo Card Layout - Row-Based Design**:
The photo card uses a two-column layout:
- **Left column**: Image preview thumbnail (fixed width, clickable)
- **Right column**: Photo metadata and correction controls

The controls are organized in rows:
1. **Row 1**: Photo name + rotation buttons
2. **Row 2**: Aspect ratio dropdown + correction checkboxes

**Interaction Details**:
- Image preview click → Navigate to refinement view for that photo
- Aspect ratio dropdown → Adjust output dimensions for perspective correction
- Rotation buttons → Rotate photo 90° left/right
- Correction checkboxes → Toggle perspective/rotation correction (mutex)

**Aspect Ratio Dropdown**:
- Only affects **Perspective Correction** output dimensions
- Disabled/hidden when **Rotation Correction** is selected
- Options: Current, 1:1, 4:3, 3:2, 5:4, 3:4, 2:3, 16:9, Custom
- Auto-selects closest ratio to detected corners on enable

**Image Preview Click - Return to Refinement**:
- **Trigger**: Click on the image preview thumbnail within a photo card
- **Behavior**:
  1. Navigate back to the full-screen bounding box refinement view
  2. Auto-select and zoom to the corresponding bounding box
  3. Enable corner refinement mode for fine-tuning
  4. User can adjust corners as needed
  5. Click "Done" to return to summary screen with updated corners
- **Use Case**: User wants to make additional corner adjustments after reviewing correction options

**Navigation Flow**:
```
┌──────────────────────────────────────┐
│         Summary Screen                │
│  ┌─────────┐ ┌─────────┐            │
│  │ Preview │ │ Preview │  ...       │
│  │ Photo 1│ │ Photo 2 │            │
│  │ [📷]   │ │ [📷]   │            │
│  │ ☑ Persp│ │ ☐ Persp│            │
│  │ ☐ Rot  │ │ ☑ Rot  │            │
│  └─────────┘ └─────────┘            │
│                                      │
│         [Click preview →              │
│          goes to refinement]         │
└──────────────────────────────────────┘
              │
              ▼ (click preview)
┌──────────────────────────────────────┐
│  < Previous    Box 2 of 5    > Next  │
│                                         │
│  ┌─────────────────────────────────┐  │
│  │                                 │  │
│  │   Zoomed View - Edit Corners    │  │
│  │                                 │  │
│  │        [Done]                   │  │
│  └─────────────────────────────────┘  │
└──────────────────────────────────────┘
              │
              ▼ (click Done)
┌──────────────────────────────────────┐
│         Summary Screen                │
│    (with updated corner positions)     │
└──────────────────────────────────────┘
```

### 3.2 Per-Photo Correction Options

Each card includes:

| Option | Type | Description |
|--------|------|-------------|
| Perspective Correction | Checkbox | Apply full perspective transform |
| Rotation Correction | Checkbox | Rotate based on longest-side axis alignment |
| Rotate Left | Button | Rotate 90° counter-clockwise |
| Rotate Right | Button | Rotate 90° clockwise |
| Aspect Ratio | Dropdown | Target ratio for perspective correction |
| Preview | Small preview | Shows result of current settings |

### 3.3 Aspect Ratio Selection (Perspective Correction Only)

**Purpose**: Define the target output dimensions for perspective correction.

**Available Ratios**:
| Label | Ratio | Common Use |
|-------|-------|------------|
| Current | Dynamic | Uses detected corner approximation (no snapping) |
| 1:1 | 1.0 | Square photos |
| 4:3 | 1.333 | Standard monitor/TV |
| 3:2 | 1.5 | 4x6 print, DSLR photos |
| 5:4 | 1.25 | 8x10 print |
| 3:4 | 0.75 | Portrait 4x6 |
| 2:3 | 0.667 | Portrait 4x6 (vertical) |
| 16:9 | 1.778 | Wide screen |
| Custom | User-defined | Manual aspect ratio input |

**Auto-Selection Logic**:
When perspective correction is enabled:
1. Calculate current aspect ratio from detected corners
2. Find the closest standard ratio using minimum distance:
   ```
   closestRatio = min(standardRatios, key={r => abs(r - currentRatio)})
   ```
3. Pre-select the closest ratio in dropdown
4. User can override by selecting different ratio

**Behavior**:
- Aspect ratio dropdown only affects perspective correction
- When rotation correction is selected, dropdown is disabled/hidden
- "Current" option outputs image with no ratio snapping
- Custom option shows numeric input field for width:height

### 3.4 Bounding Box Expansion for Perspective

**Purpose**: Ensure all corners of the detected quadrilateral fit within the output frame.

**Algorithm**:
1. Given detected corner coordinates and target aspect ratio:
2. Calculate the center point of the detected region
3. Determine the maximum dimension needed to fit all corners at target ratio:
   ```
   width = max_corner_distance_on_width_axis
   height = width / target_ratio
   // OR
   height = max_corner_distance_on_height_axis
   width = height * target_ratio
   
   // Take the larger of the two to ensure all corners fit
   ```
4. Expand output canvas to fit all corners
5. Apply perspective transform to fill the expanded frame

### 3.5 Correction Mutex

**Rule**: Cannot apply both perspective AND rotation correction simultaneously.

**Behavior**:
- When "Perspective Correction" is checked, disable "Rotation Correction"
- When "Rotation Correction" is checked, disable "Perspective Correction"
- Clear visual indication of which mode is active
- Aspect ratio dropdown is only visible/enabled when perspective correction is checked

### 3.6 Rotation Correction Details

**Purpose**: Straighten photos that are slightly off-axis.

**Algorithm**:
1. Detect longest sides of the photo
2. Calculate average rotation angle off horizontal/vertical
3. Rotate image to align with nearest axis
4. **Bounding Box Expansion**: Increase bounding box so rotated corners remain within frame

**Bounding Box Expansion Formula**:
```
// For rotation angle θ (in radians)
newWidth = width * |cos(θ)| + height * |sin(θ)|
newHeight = width * |sin(θ)| + height * |cos(θ)|
// Expand box by this amount to ensure no corners are clipped
```

### 3.7 Perspective Correction Details

**Purpose**: Correct for trapezoidal distortion from camera angle.

**Algorithm**:
1. Apply perspective transform using detected corner coordinates
2. Output to rectangular frame with selected aspect ratio
3. **Expand output canvas** to fit all detected corners within the target aspect ratio

**Implementation with Aspect Ratio**:
```
1. Get detected corner coordinates (quadrilateral)
2. Calculate center point of detected region
3. Determine target dimensions based on selected aspect ratio
4. Expand output canvas to ensure all corners fit
5. Apply perspective transform to map quadrilateral to rectangular output
```

---

## 4. Processing & Export

**Requirement**: Apply selected transformations and export with naming convention.

### 4.1 Processing Pipeline

For each photo in bounding box list:

1. **Crop**: Extract photo region from source image
2. **Apply Corrections** (based on checkboxes):
   - If Perspective: Apply perspective transform
   - If Rotation: Rotate + expand bounding box
3. **Output**: Save processed image

### 4.2 Naming Convention

**Format**: `{importName}_#{index}.{extension}`

**Examples**:
- `Vacation_1.jpg`
- `Vacation_2.jpg`
- `ScannedPhotos_3.png`

**Indexing**: Based on bounding box order (1-indexed).

### 4.3 Export Flow

1. User clicks "Process & Export" (or similar)
2. Loading animation displays
3. Each photo is processed sequentially
4. Progress indicator shows current/total
5. On completion, show success message or navigate to next screen

---

## 5. Future Enhancements

### 5.1 Custom Naming (Phase 2)

**Requirement**: Override naming per-photo using metadata.

**UI Addition**:
- "Custom Name" text field in each photo card
- Metadata editor as new window in import wizard

### 5.2 Metadata Editor Window

**Fields to support**:
- Title
- Date Taken
- Location
- Description
- Tags/Labels
- Custom fields

**Integration**:
- Opens as modal dialog or separate window
- Pre-populated with extracted metadata if available
- User can edit before final export
- Metadata saved to EXIF or sidecar file

### 5.3 Batch Operations

**Potential additions**:
- Apply same correction to all photos
- Auto-detect rotation (optional)
- Auto-detect perspective (optional)

---

## 6. Screen Flow Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│                         Import Screen                              │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  Import Mode:                                               │  │
│  │  ○ Photo Scan (multi-box)    ○ Single Photo (single)        │  │
│  │                                                             │  │
│  │  Photo Scan Options:                                        │  │
│  │  ☑ Auto-detect bounding boxes (CV)                        │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                              │                                     │
│              ┌───────────────┴───────────────┐                  │
│              │                               │                    │
│         [Load Image]                    [Load Image]              │
│              │                               │                    │
└──────────────│───────────────────────────────│────────────────────┘
               │ CV Enabled                     │ CV Disabled
               ▼                               ▼
┌──────────────────────┐             ┌──────────────────────┐
│   Loading (CV)      │             │   Loading (No CV)    │
│   Auto-detecting... │             │   Skip detection     │
└──────────┬───────────┘             └──────────┬───────────┘
           │                                   │
           ▼                                   ▼
┌──────────────────────────────────────────────────────────────┐
│  Full-Screen Bounding Box Refinement (Overview)             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ Photo 1  │  │ Photo 2  │  │ Photo 3  │   [+ Add] [4-Pt] │
│  └──────────┘  └──────────┘  └──────────┘                  │
│  [🔍-] [⛶] [🔍+]  [ⓘ]                                    │
└─────────────────────────────────────────────────────────────┘
         │ Click box or [4-Point] mode
         ▼
┌──────────────────────────────────────────────────────────────┐
│  4-Point Selection Mode (if [4-Pt] clicked)                  │
│  "Click to set point 1 of 4"                                 │
│  ① ─ ─ ─ ─ ─ ─ ─ ○                                         │
│                    ╲                                         │
│                     ○ (waiting for click)                   │
└─────────────────────────────────────────────────────────────┘
         │ 4 points placed
         ▼
┌──────────────────────────────────────────────────────────────┐
│  Zoomed View - Corner Refinement                            │
│  [← Back] [← Prev] Box 2 of 5 [Next →] [🔍-][⛶][🔍+]      │
│                                           [🗑️ Delete] [ⓘ]  │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  Summary List (Corrections)                                │
│  [Apply Perspective to All]  [Apply Rotation to All]       │
│  [Clear All]  [Reset All to Default]                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                       │
│  │ Preview │ │ Preview │ │ Preview │   ...                 │
│  │ Photo 1 │ │ Photo 2 │ │ Photo 3 │                       │
│  └─────────┘ └─────────┘ └─────────┘                       │
└──────────────────────────────────────────────────────────────┘
         │ Click "Process"
         ▼
┌──────────────────────────────────────────────────────────────┐
│  Processing & Export                                        │
│  [loading.webp animation]                                   │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. Implementation Tasks

### Phase 0: Import Screen Setup

| # | Task | Status |
|---|------|--------|
| 0.1 | Add Photo Scan / Single Photo mode selection UI | ✅ |
| 0.2 | Add CV auto-detection toggle (checkbox/switch) | ✅ |
| 0.3 | Add "4-Point" button to enter manual corner selection mode | ✅ |
| 0.4 | Implement 4-point selection workflow (click 1→4 points) | ✅ |
| 0.5 | Add visual feedback for point placement (numbered markers, lines) | ✅ |
| 0.6 | Handle cancel behavior (Escape, Backspace, cancel button) | ✅ |
| 0.7 | Add status indicator showing current point (e.g., "Point 2 of 4") | ✅ |
| 0.8 | Create quadrilateral from 4 points and add as bounding box | ✅ |
| 0.9 | Integrate 4-point with CV toggle (work together) | ✅ |
| 0.10 | Implement state/mode system (Normal, 4-Point, Add Box, Refinement) | ✅ |
| 0.11 | Add mode indicator in status bar | ✅ |
| 0.12 | Handle no-CV-results scenario (empty state, retry option) | ✅ |
| 0.13 | Handle image loading errors (corrupted, unsupported, memory) | ✅ |
| 0.14 | Implement 4-point quadrilateral validation (convex, minimum size) | ✅ |
| 0.15 | Implement help overlay (ⓘ icon, F1, ? key) | ✅ |
| 0.16 | Add Single Photo mode flow (simplified, no multi-box) | ✅ |
| 0.17 | Add F1 and ? keyboard shortcuts for help | ✅ |

### Phase 1: Core UI Infrastructure

| # | Task | Status |
|---|------|--------|
| 1.1 | Verify/create loading.webp resource | ✅ |
| 1.2 | Create bounding box overlay component | ✅ |
| 1.3 | Implement full-screen refinement view | ✅ |
| 1.4 | Add click-to-add bounding box logic (30% width, 3:2 ratio) | ✅ |
| 1.5 | Add minimum size validation (10% of image dimension) | ✅ |
| 1.6 | Add corner drag handles | ✅ |
| 1.7 | Implement zoom-to-box functionality (+20% margin) | ✅ |
| 1.8 | Add Previous/Next navigation | ✅ |
| 1.9 | Add keyboard shortcuts (arrows, Enter, Ctrl+Z) | ✅ |
| 1.10 | Implement undo/redo for corner movements | ✅ |
| 1.11 | Add drag-to-move bounding box (click interior) | ✅ |
| 1.12 | Add corner selection via click buffer (20px radius) | ✅ |
| 1.13 | Add arrow key movement for selected corner (1px/10px with shift) | ✅ |
| 1.14 | Add scroll wheel to expand/contract bounding box | ✅ |
| 1.15 | Add shift+scroll to rotate bounding box around center | ✅ |
| 1.16 | Add info icon (ⓘ) with interaction tooltip | ✅ |
| 1.17 | Add "← Back" button to return to overview from refinement | ✅ |
| 1.18 | Add delete functionality (Delete key, trash icon, context menu) | ✅ |
| 1.19 | Add zoom buttons (🔍+, ⛶ fit, 🔍-) in overview view | ✅ |
| 1.20 | Add zoom buttons in refinement view | ✅ |
| 1.21 | Implement scroll-to-zoom when not hovering over bounding box | ✅ |
| 1.22 | Ensure zoomed content stays within viewport (no overflow) | ✅ |
| 1.23 | Implement no selection by default on overview load | ✅ |
| 1.24 | Add click-outside-to-deselect behavior | ✅ |

### Phase 2: Summary & Corrections

| # | Task | Status |
|---|------|--------|
| 2.1 | Create summary list view | ✅ |
| 2.2 | Add aspect ratio dropdown (Current, 1:1, 3:2, etc.) | ✅ |
| 2.3 | Auto-select closest aspect ratio to detected corners | ✅ |
| 2.4 | Add portrait/landscape orientation handling for aspect ratio | ✅ |
| 2.5 | Add perspective correction checkbox | ✅ |
| 2.6 | Add rotation correction checkbox | ✅ |
| 2.7 | Implement correction mutex logic | ✅ |
| 2.8 | Add rotate left/right buttons | ✅ |
| 2.9 | Implement rotation preview | ✅ |
| 2.10 | Implement bounding box expansion for perspective correction | ✅ |
| 2.11 | Add bulk change buttons (Apply Perspective to All, etc.) | ✅ |

### Phase 3: Processing & Export

| # | Task | Status |
|---|------|--------|
| 3.1 | Implement perspective transform with aspect ratio output | ✅ |
| 3.2 | Implement rotation transform with bounding box expansion | ✅ |
| 3.3 | Add export with naming convention | ✅ |
| 3.4 | Add loading animation during processing | ✅ |

### Phase 4: Future (Not in Scope)

| # | Task | Status |
|---|------|--------|
| 4.1 | Custom naming per-photo | Future |
| 4.2 | Metadata editor window | Future |
| 4.3 | EXIF reading/writing | Future |
| 4.4 | Batch auto-correction | Future |

---

## 8. File Locations

### Source Files

```
src/main/kotlin/org/kryspetrie/fileimport/
├── infrastructure/
│   └── photoscan/
│       ├── PhotoScanState.kt              # State management, mode transitions
│       ├── BoundingBox.kt                # Bounding box model (rect + quad)
│       ├── BoundingBoxList.kt            # List management, intersection detection
│       ├── FourPointState.kt             # 4-point selection mode state
│       ├── UndoRedoManager.kt             # Undo/redo stack management
│       ├── ZoomController.kt             # Zoom level and pan calculations
│       ├── AspectRatioHandler.kt          # Ratio selection and flipping logic
│       ├── RefinementState.kt            # Corner selection, drag handling
│       ├── CornerRefinementView.kt       # Full-screen refinement UI (zoomed)
│       ├── OverviewView.kt               # Overview with all boxes
│       ├── SummaryScreen.kt              # Correction options UI
│       ├── SummaryPhotoCard.kt           # Individual photo card component
│       ├── BulkActionBar.kt              # Bulk change buttons
│       ├── HelpOverlay.kt                # ⓘ info overlay component
│       ├── PerspectiveTransformer.kt     # Perspective transform algorithm
│       ├── RotationTransformer.kt       # Rotation with box expansion
│       ├── ImageLoader.kt                # Image loading with error handling
│       ├── CVDetector.kt                 # Auto-detection wrapper
│       └── ExportManager.kt              # Export pipeline with progress
├── ui/
│   └── screens/
│       ├── ImportScreen.kt               # Import screen (mode selection)
│       ├── RefinementWizard.kt           # Wizard container, navigation
│       ├── LoadingOverlay.kt             # loading.webp animation
│       └── ErrorDialog.kt                # Error display component
└── resources/
    ├── icons/
    │   ├── loading.webp                  # Loading animation
    │   ├── point-marker-1.png            # ① point marker
    │   ├── point-marker-2.png            # ② point marker
    │   ├── point-marker-3.png            # ③ point marker
    │   └── point-marker-4.png            # ④ point marker
    └── test-data/
        └── ground_truth/                 # Test images with expected values
            ├── scan_01/
            │   ├── image.jpg
            │   └── expected_boxes.json
            └── scan_02/
                └── ...
```

### Documentation

```
docs/
├── IMPLEMENTATION_PLAN.md                # This file
├── SUGGESTED_IMPL_PLAN.md                # Previous plan (if exists)
└── TEST_CHECKLIST.md                     # Manual testing checklist
```

### Test Files

```
src/test/kotlin/org/kryspetrie/fileimport/
├── unit/
│   ├── BoundingBoxTest.kt               # BB-01 through BB-14
│   ├── FourPointTest.kt                  # 4P-01 through 4P-06
│   ├── UndoRedoTest.kt                   # UR-01 through UR-06
│   ├── ZoomControllerTest.kt             # ZC-01 through ZC-08
│   ├── CornerRefinementTest.kt           # CR-01 through CR-07
│   ├── AspectRatioTest.kt                # AR-01 through AR-06
│   ├── PerspectiveTransformTest.kt       # PT-01 through PT-05
│   └── RotationTransformTest.kt          # RT-01 through RT-04
├── integration/
│   ├── FullImportFlowTest.kt             # INT-01 through INT-10
│   ├── SummaryFlowTest.kt                # SUM-01 through SUM-08
│   └── ExportFlowTest.kt                 # EXP-01 through EXP-05
└── ui/
    ├── OverviewViewTest.kt               # UI-01 through UI-08
    ├── RefinementViewTest.kt             # UI-09 through UI-15
    ├── SummaryScreenTest.kt              # UI-16 through UI-20
    └── ErrorStateTest.kt                 # UI-21 through UI-24
```

### Key Classes and Responsibilities

| Class | Responsibility | Public API |
|-------|---------------|------------|
| `PhotoScanState` | Central state container | `mode`, `boundingBoxes`, `currentBox`, `undoStack` |
| `BoundingBox` | Single box with corners | `corners`, `move()`, `moveCorner()`, `rotate()`, `expand()` |
| `BoundingBoxList` | Collection management | `add()`, `remove()`, `canAdd()`, `findAtPoint()`, `getIntersections()` |
| `FourPointState` | 4-point mode state | `points`, `mode`, `addPoint()`, `removeLast()`, `clear()`, `toBoundingBox()` |
| `UndoRedoManager` | History management | `push()`, `undo()`, `redo()`, `canUndo()`, `canRedo()` |
| `ZoomController` | Zoom/pan calculations | `zoomIn()`, `zoomOut()`, `fitToView()`, `screenToImage()`, `imageToScreen()` |
| `AspectRatioHandler` | Ratio logic | `getOutputRatio()`, `autoSelectClosest()`, `isPortrait()` |
| `RefinementState` | Refinement interaction | `selectCorner()`, `moveSelected()`, `expand()`, `rotate()` |
| `PerspectiveTransformer` | 4-point transform | `apply(source, targetW, targetH)` |
| `RotationTransformer` | Rotation with expansion | `rotate(image, degrees)`, `calculateExpandedBounds()` |

---

## 9. Dependencies

### Existing
- Kotlin + Java AWT (for image processing)
- JetBrains Compose (for UI)

### New Requirements
- WebP decoding support (if not already available)
- AffineTransform (for rotation)
- PerspectiveTransform / manual implementation

---

## 10. Testing Plan

### 10.1 Testing Strategy Overview

The wizard requires testing across multiple layers:
- **Unit Tests**: Individual components and algorithms
- **Integration Tests**: Multi-component workflows
- **Visual/UI Tests**: User interface behavior and appearance
- **Performance Tests**: Large images, many boxes, undo stacks
- **Manual Acceptance Tests**: User-facing workflows

### 10.2 Unit Tests

#### 10.2.1 BoundingBox Model Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| BB-01 | Create rectangular box | center (100,100), width 60, height 40 | Corners at (70,80), (130,80), (130,120), (70,120) |
| BB-02 | Create quadrilateral box | 4 points: (10,10), (90,10), (90,90), (10,90) | Valid quadrilateral, all 4 corners stored |
| BB-03 | Minimum size validation | center (50,50), dimensions 5% of image | Rejected (below 10% minimum) |
| BB-04 | Add box to empty list | New box | List contains 1 box, selected |
| BB-05 | Add box intersecting existing | Box overlapping existing | Rejected, show "overlaps existing" message |
| BB-06 | Remove box | Box selected, press Delete | Box removed from list |
| BB-07 | Move box (drag) | Drag box center by (10, -5) | All 4 corners moved by (10, -5) |
| BB-08 | Move single corner | Drag TL corner by (5, 5) | Only TL corner moved |
| BB-09 | Click detection (no box) | Click at (0,0) with no boxes | No box selected |
| BB-10 | Click detection (with box) | Click at center of box | Box selected |
| BB-11 | Click detection (near corner) | Click within 20px of corner | Corner selected |
| BB-12 | Deselect on outside click | Box selected, click empty area | No box selected |
| BB-13 | Convex hull validation | 4 points in wrong order: TL, BL, TR, BR | Reorder to TL, TR, BR, BL |
| BB-14 | Invalid quadrilateral | 4 collinear points | Rejected, show "invalid shape" |

**Example Test Code (Kotlin)**:
```kotlin
@Test
fun `BB-01 create rectangular box at center`() {
    val box = BoundingBox.rectangular(center = Point(100, 100), width = 60, height = 40)
    assertEquals(Point(70, 80), box.topLeft)
    assertEquals(Point(130, 80), box.topRight)
    assertEquals(Point(130, 120), box.bottomRight)
    assertEquals(Point(70, 120), box.bottomLeft)
}

@Test
fun `BB-05 reject overlapping box`() {
    val existing = BoundingBox.rectangular(center = Point(100, 100), width = 60, height = 40)
    val overlapping = BoundingBox.rectangular(center = Point(100, 100), width = 40, height = 30)
    assertFalse(boundingBoxList.canAdd(overlapping)) // Overlaps existing
}
```

#### 10.2.2 FourPointSelection Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| 4P-01 | Complete 4 points | Points: (10,10), (90,10), (90,90), (10,90) | Box created, exits mode |
| 4P-02 | Cancel with Escape | After point 2 placed, press Escape | Mode exited, no box created |
| 4P-03 | Remove last point (Backspace) | After point 3 placed, press Backspace | Point 3 removed, back to "Point 2 of 4" |
| 4P-04 | Points too close | Points within 2% of each other | Rejected with "Points too close" |
| 4P-05 | Self-intersecting quad | Points: (0,0), (50,50), (100,0), (50,25) | Rejected with "invalid shape" |
| 4P-06 | Confirm with Enter | 4 points placed, press Enter | Box created, exits mode |

**Example Test**:
```kotlin
@Test
fun `4P-01 complete 4 points creates box`() {
    val state = FourPointState()
    state.addPoint(Point(10, 10))
    state.addPoint(Point(90, 10))
    state.addPoint(Point(90, 90))
    state.addPoint(Point(10, 90))
    
    assertTrue(state.isComplete)
    val box = state.toBoundingBox()
    assertNotNull(box)
    assertEquals(FourPointState.Mode.COMPLETE, state.mode)
}
```

#### 10.2.3 UndoRedoManager Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| UR-01 | Undo single action | Move corner, press Ctrl+Z | Corner returned to original position |
| UR-02 | Redo after undo | After UR-01, press Ctrl+Shift+Z | Corner moved again |
| UR-03 | Clear redo on new action | Undo, then make new move | Redo stack cleared |
| UR-04 | Stack limit (50) | Make 55 moves | Only 50 actions in stack, oldest 5 discarded |
| UR-05 | Per-box stack isolation | Box A has 20 moves, Box B has 30 moves | Each box maintains separate history |
| UR-06 | Undo 4-point mode | Start 4-point, place 2 points, undo | Back to "Point 1 of 4" |

#### 10.2.4 ZoomController Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| ZC-01 | Zoom in | Current zoom 1.0x, click + button | New zoom 1.25x |
| ZC-02 | Zoom out | Current zoom 1.25x, click - button | New zoom 1.0x |
| ZC-03 | Fit to view | Any zoom, click fit | Zoom calculated to fit image in viewport |
| ZC-04 | Zoom clamp (minimum) | Zoom 0.1x, press - | No change (at minimum) |
| ZC-05 | Zoom clamp (maximum) | Zoom 10x, press + | No change (at maximum) |
| ZC-06 | Zoom around cursor | Cursor at (50, 50), zoom in | Point (50,50) stays under cursor |
| ZC-07 | Pan while zoomed | Zoom 2x, drag empty area | View offset adjusted |
| ZC-08 | Zoom within bounds | Zoomed in, image fits in viewport | No scroll bars needed |

**Example Test**:
```kotlin
@Test
fun `ZC-01 zoom in increases zoom level`() {
    val controller = ZoomController(initialZoom = 1.0)
    controller.zoomIn()
    assertEquals(1.25, controller.currentZoom, 0.01)
}

@Test
fun `ZC-06 zoom around cursor position`() {
    val controller = ZoomController(initialZoom = 1.0)
    val imagePoint = controller.screenToImage(Point(50, 50), cursorPosition = Point(50, 50))
    controller.zoomIn(cursorPosition = Point(50, 50))
    // The same image point should still be under cursor
    val newImagePoint = controller.screenToImage(Point(50, 50), cursorPosition = Point(50, 50))
    assertEquals(imagePoint.x, newImagePoint.x, 1.0) // Within 1px
    assertEquals(imagePoint.y, newImagePoint.y, 1.0)
}
```

#### 10.2.5 CornerRefinementController Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| CR-01 | Arrow key move (1px) | Corner selected, press ↑ | Corner moved 1px up |
| CR-02 | Shift+arrow move (10px) | Corner selected, press Shift+→ | Corner moved 10px right |
| CR-03 | Deselect corner | Corner selected, press Escape | No corner selected |
| CR-04 | Scroll expand | Hover over box, scroll up | Box expanded by 5% |
| CR-05 | Shift+scroll rotate | Hover over box, Shift+scroll up | Box rotated 5° clockwise |
| CR-06 | Corner buffer selection | Click 15px from corner | Corner selected |
| CR-07 | Outside buffer (no select) | Click 25px from any corner | Corner not selected |

**Example Test**:
```kotlin
@Test
fun `CR-01 arrow key moves selected corner 1px`() {
    val box = BoundingBox.rectangular(center = Point(100, 100), width = 80, height = 60)
    val controller = CornerRefinementController(box)
    controller.selectCorner(Corner.TOP_LEFT)
    
    val initialY = controller.selectedCorner!!.y
    controller.handleKey(KeyEvent.VK_UP)
    
    assertEquals(initialY - 1, controller.selectedCorner!!.y)
}
```

#### 10.2.6 AspectRatioHandler Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| AR-01 | Landscape photo + landscape ratio | Box 200x100, ratio 1.5 | Output 200x133 (as-is) |
| AR-02 | Landscape photo + portrait ratio | Box 200x100, ratio 0.75 | Output 200x267 (flipped to 1.33) |
| AR-03 | Portrait photo + portrait ratio | Box 100x200, ratio 0.75 | Output 100x133 (as-is) |
| AR-04 | Portrait photo + landscape ratio | Box 100x200, ratio 1.5 | Output 100x67 (flipped to 0.667) |
| AR-05 | Square box + any ratio | Box 150x150, ratio 1.5 | Output 150x150 (1:1) |
| AR-06 | Auto-select closest | Box 195x130 (ratio 1.5) | "3:2" pre-selected |

**Example Test**:
```kotlin
@Test
fun `AR-02 landscape photo flips portrait ratio`() {
    val handler = AspectRatioHandler()
    val outputRatio = handler.getOutputAspectRatio(
        detectedWidth = 200.0,
        detectedHeight = 100.0, // Landscape
        selectedRatio = 0.75   // Portrait ratio (3:4)
    )
    // Should flip to landscape: 4:3 = 1.333
    assertEquals(1.333, outputRatio, 0.01)
}
```

#### 10.2.7 PerspectiveTransformer Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| PT-01 | Basic 4-point transform | Trapezoid → rectangle | Correct pixel mapping |
| PT-02 | Output at custom ratio | Trapezoid, target 3:2 | Output dimensions match 3:2 |
| PT-03 | Expand for corners | Trapezoid with corner outside target | Canvas expanded, no clipping |
| PT-04 | Identity transform | Rectangle already aligned | Output equals input |
| PT-05 | 90° rotated source | Photo at 90° angle | Corrected to horizontal |

**Example Test**:
```kotlin
@Test
fun `PT-01 basic perspective transform`() {
    val transform = PerspectiveTransformer()
    val source = Quadrilateral(
        topLeft = Point(0, 0),
        topRight = Point(100, 0),
        bottomRight = Point(100, 100),
        bottomLeft = Point(0, 100)
    )
    val result = transform.apply(source, targetWidth = 100, targetHeight = 100)
    
    // For rectangle, output should be pixel-perfect copy
    assertEquals(0.0, result.pixelDifferences(source), 0.01)
}
```

#### 10.2.8 RotationTransformer Tests

| Test ID | Test Case | Input | Expected Output |
|---------|-----------|-------|------------------|
| RT-01 | Rotate 15° clockwise | Box, angle +15° | All corners moved, box rotated |
| RT-02 | Bounding box expansion | Box 100x80, rotate 45° | New dimensions ~127x127 |
| RT-03 | Rotate +90° | Box, angle +90° | TL→TR, TR→BR, BR→BL, BL→TL |
| RT-04 | No rotation (0°) | Box, angle 0° | Unchanged |

**Example Test**:
```kotlin
@Test
fun `RT-02 rotation expands bounding box`() {
    val transformer = RotationTransformer()
    val box = RectangularBox(width = 100.0, height = 80.0)
    
    val expanded = transformer.calculateExpandedBounds(box, rotationDegrees = 45.0)
    
    // After 45° rotation, bounding box should be roughly 127x127
    assertTrue(expanded.width > 100)
    assertTrue(expanded.height > 80)
    // Both should be similar (square-ish after 45° rotation of rect)
}
```

### 10.3 Integration Tests

#### 10.3.1 Full Import Flow Tests

| Test ID | Test Case | Steps | Expected Result |
|---------|-----------|-------|------------------|
| INT-01 | Photo Scan with CV | Import image → CV detection → Overview | Detected boxes displayed |
| INT-02 | Photo Scan without CV | Toggle off → Import → Overview | Empty box list |
| INT-03 | Manual 4-Point entry | Add box → 4-Point → click 4 corners | Box added to list |
| INT-04 | Add rectangular box | Add box → click location | New box created (30% width) |
| INT-05 | Refine box | Click box → Refinement → drag corner → Done | Corners updated |
| INT-06 | Delete box | Select box → Delete | Box removed, overview shown |
| INT-07 | Navigate boxes | In Refinement, press Ctrl+→ | Next box shown |
| INT-08 | Undo/Redo cycle | Move corner → Undo → Redo | Original position, then moved again |
| INT-09 | Zoom in refinement | In Refinement, click zoom+ | View magnified |
| INT-10 | Multi-box workflow | Add 3 boxes → Refine each → Delete middle → Summary | Correct state |

**Integration Test Example**:
```kotlin
@Test
fun `INT-03 manual 4-point entry creates box`() {
    // Setup
    val viewModel = ImportViewModel()
    viewModel.setMode(Mode.PHOTO_SCAN)
    viewModel.loadImage(testImagePath)
    
    // Enter 4-point mode
    viewModel.enterFourPointMode()
    assertEquals(Mode.FOUR_POINT, viewModel.currentMode)
    
    // Place 4 points
    viewModel.placePoint(Point(10, 10))
    viewModel.placePoint(Point(190, 10))
    viewModel.placePoint(Point(190, 150))
    viewModel.placePoint(Point(10, 150))
    
    // Verify box created
    assertEquals(1, viewModel.boundingBoxes.size)
    assertEquals(Mode.NORMAL, viewModel.currentMode)
    assertTrue(viewModel.boundingBoxes[0].isSelected)
}
```

#### 10.3.2 Summary → Refinement Flow

| Test ID | Test Case | Steps | Expected Result |
|---------|-----------|-------|------------------|
| SUM-01 | View summary | All boxes refined → Click "To Summary" | Summary screen with all boxes |
| SUM-02 | Click preview to edit | Summary → Click preview thumbnail | Refinement mode for that box |
| SUM-03 | Update corners from summary | Refine from summary → Done → Summary | Updated corners reflected |
| SUM-04 | Perspective correction | Enable perspective → Select ratio → Process | Correct output dimensions |
| SUM-05 | Rotation correction | Enable rotation → Apply → Process | Correctly rotated output |
| SUM-06 | Correction mutex | Enable perspective → Enable rotation | Perspective auto-unchecked |
| SUM-07 | Bulk apply perspective | 5 photos → Bulk → Perspective to All | All 5 enabled, rotation disabled |
| SUM-08 | Bulk reset | Mixed settings → Reset All → Default | All at defaults |

#### 10.3.3 Export Flow Tests

| Test ID | Test Case | Steps | Expected Result |
|---------|-----------|-------|------------------|
| EXP-01 | Basic export | Process & Export → Choose folder | Files saved with naming convention |
| EXP-02 | Custom naming | Summary → Rename photo → Process | File uses custom name |
| EXP-03 | Partial failure | 5 photos, one corrupt output | 4 saved, error message for 1 |
| EXP-04 | Progress indication | Process 10 photos | Progress bar updates correctly |
| EXP-05 | Loading animation | Process → Animation shows | Animation visible during processing |

### 10.4 UI/Visual Tests

#### 10.4.1 Overview Screen Tests

| Test ID | Test Case | Input | Expected UI Behavior |
|---------|-----------|-------|---------------------|
| UI-01 | Box selection highlight | Click on box | Selected box has bright border |
| UI-02 | No selection on load | Open overview | No box highlighted |
| UI-03 | Mode indicator | Enter 4-Point mode | Status shows "[4-POINT]" |
| UI-04 | Zoom buttons visible | View overview | Zoom controls in top-right |
| UI-05 | 4-Point markers | Place point 2 | ① ② shown with line |
| UI-06 | Preview quadrilateral | Place point 3 | Dashed line preview to point 4 |
| UI-07 | Delete disabled (no selection) | Open overview, press Delete | No box deleted, no error |
| UI-08 | Cursor change (4-Point) | Enter 4-Point mode | Cursor changes to crosshair |

#### 10.4.2 Refinement Screen Tests

| Test ID | Test Case | Input | Expected UI Behavior |
|---------|-----------|-------|---------------------|
| UI-09 | Corner handle hover | Hover over corner | Cursor changes to resize |
| UI-10 | Corner selected state | Click on corner | Corner highlighted (larger, different color) |
| UI-11 | Back button visible | View refinement | ← Back button in top-left |
| UI-12 | Zoom controls | View refinement | 🔍- ⛶ 🔍+ buttons visible |
| UI-13 | Delete button | View refinement | 🗑️ Delete button visible |
| UI-14 | Help overlay | Click ⓘ | Full help overlay appears |
| UI-15 | Status message | In refinement | "Drag corners or box to refine" shown |

#### 10.4.3 Summary Screen Tests

| Test ID | Test Case | Input | Expected UI Behavior |
|---------|-----------|-------|---------------------|
| UI-16 | Photo preview clickable | View summary | Preview shows "Click to edit" |
| UI-17 | Correction mutex | Check Perspective | Rotation checkbox disabled |
| UI-18 | Aspect ratio dropdown | Perspective checked | Ratio dropdown enabled |
| UI-19 | Bulk buttons | View summary | All 4 bulk buttons visible |
| UI-20 | Rotation buttons | View summary card | ↺ L and R ↻ visible |

#### 10.4.4 Error State UI Tests

| Test ID | Test Case | Input | Expected UI Behavior |
|---------|-----------|-------|---------------------|
| UI-21 | No CV results | CV enabled, returns empty | "No photos detected" message + retry |
| UI-22 | Image load failure | Corrupt image file | Error dialog with options |
| UI-23 | Empty state | All boxes deleted | "Click to add" prompt shown |
| UI-24 | Export failure | Disk full during export | Error message + retry option |

### 10.5 Performance Tests

| Test ID | Test Case | Input | Expected Performance |
|---------|-----------|-------|---------------------|
| PERF-01 | Large image (8000x8000) | Load 8000px image | Load under 5 seconds (with downsampling) |
| PERF-02 | Many boxes (50) | 50 bounding boxes | UI responsive, no lag |
| PERF-03 | Undo stack stress | 50 moves per box, 10 boxes | Undo/redo responsive |
| PERF-04 | Zoom stress test | Rapid zoom in/out 50 times | No UI freeze |
| PERF-05 | 4-Point rapid entry | Quick 4-point clicks | All points registered accurately |
| PERF-06 | Memory usage | 20 large images in session | Under 500MB |
| PERF-07 | Perspective transform | 20 photos with perspective | Process under 30 seconds total |

### 10.6 Test Data Requirements

#### 10.6.1 Image Test Set

| Type | Purpose | Count |
|------|---------|-------|
| Simple flatbed scan | Basic functionality | 5 |
| Multiple photos (2-5) | Multi-box detection | 5 |
| Perspective-distorted | 4-point and perspective correction | 5 |
| Rotated photos | Rotation correction | 5 |
| Low contrast photos | Edge detection robustness | 3 |
| Large images (4000px+) | Performance testing | 3 |
| Corrupt/unsupported | Error handling | 2 |

#### 10.6.2 Ground Truth Data

For each test image, store expected values:
```
ground_truth/
├── scan_01.jpg/
│   ├── expected_boxes.json
│   └── corners.json
├── scan_02.jpg/
│   └── ...
```

**Expected boxes format**:
```json
{
  "image": "scan_01.jpg",
  "boxes": [
    {
      "id": 1,
      "corners": {
        "topLeft": {"x": 256, "y": 1560},
        "topRight": {"x": 2104, "y": 1560},
        "bottomRight": {"x": 2104, "y": 3814},
        "bottomLeft": {"x": 256, "y": 3814}
      }
    }
  ],
  "aspectRatio": 1.5,
  "portrait": false
}
```

### 10.7 Manual Testing Checklist

#### Phase 0: Import Screen
- [ ] Photo Scan radio button selectable
- [ ] Single Photo radio button selectable
- [ ] CV toggle visible when Photo Scan selected
- [ ] CV toggle hidden when Single Photo selected
- [ ] "Import Image" button loads file picker
- [ ] Invalid image shows error dialog

#### Phase 1: Overview Mode
- [ ] All detected boxes visible
- [ ] No box selected on load
- [ ] Click selects box (highlight visible)
- [ ] Click empty deselects
- [ ] 4-Point button enters mode
- [ ] 4-Point markers numbered correctly
- [ ] Escape cancels 4-Point mode
- [ ] Add Box button works
- [ ] Zoom buttons work
- [ ] Scroll zooms when not on box
- [ ] Delete removes selected box

#### Phase 2: Refinement Mode
- [ ] Click on box enters refinement
- [ ] ← Back returns to overview
- [ ] Corner dragging works
- [ ] Arrow keys move selected corner
- [ ] Shift+arrow moves 10px
- [ ] Scroll expands/contracts box
- [ ] Shift+scroll rotates box
- [ ] Zoom controls work
- [ ] Delete removes current box
- [ ] Ctrl+arrows navigate boxes

#### Phase 3: Summary Mode
- [ ] All photos listed
- [ ] Preview thumbnails clickable
- [ ] Click preview enters refinement
- [ ] Correction mutex works
- [ ] Aspect ratio dropdown enabled/disabled correctly
- [ ] Bulk buttons work
- [ ] Processing shows progress

#### Phase 4: Export
- [ ] Files saved with correct naming
- [ ] Naming convention applied
- [ ] Progress indicator works
- [ ] Error handling for failures

### 10.8 Regression Testing

After any changes, verify these core flows still work:

| Flow | Steps |
|------|-------|
| Quick import | Photo Scan → Import → Refine → Summary → Export |
| Manual entry | Single Photo → 4-Point → Summary → Export |
| Delete flow | Add box → Select → Delete → Undo |
| Mixed workflow | 2 CV boxes + 1 manual box → Refine 1 → Delete 1 → Export 2 |

### 10.9 Test Coverage Goals

| Category | Target Coverage |
|----------|-----------------|
| Unit tests | 90%+ of business logic |
| Integration tests | All user-facing workflows |
| UI tests | Key interactions automated |
| Manual tests | All checklist items verified |

---

## 10.10 Visual Testing Notes

---

## 11. Navigation Paths Summary

| From | To | Trigger |
|------|-----|---------|
| Overview | Zoomed (specific box) | Click on any bounding box |
| Zoomed | Overview | Click "← Back" or "Done" |
| Overview | Summary | Click "Done Refinement" |
| **Summary** | **Zoomed (specific box)** | **Click image preview thumbnail** |
| Zoomed | Summary | Click "Done Refinement" |

**Key Features**:
- **"← Back" button**: Always visible in zoomed refinement view; returns to overview with all boxes
- Clicking the image preview in the Summary screen navigates back to the full-screen corner refinement view, automatically selecting and zooming to that photo's bounding box for additional corner adjustments.
- **Delete key**: Requires box to be selected first (no selection = no delete)

---

## 12. Open Questions

### Answered Questions
| # | Question | Answer |
|---|----------|--------|
| 1 | Aspect ratio for new bounding boxes | **3:2** (standard 4x6 photo ratio) as default |
| 2 | Corner snap behavior | **Free movement** (no grid snap) |
| 3 | Minimum bounding box size | **10% of image dimension** |
| 4 | Keyboard shortcuts for navigation | **Arrow keys** for nav, **Enter** for Done, **Ctrl+Z/Shift+Z** for Undo/Redo |
| 5 | Undo/Redo for refinement changes | **Yes**, implement undo/redo stack (**50 operations** per box) |
| 6 | Delete key behavior | **Must select box first**, then Delete to remove |
| 7 | Default selection on load | **No selection** on load; user must click to select |
| 8 | Zoom-to-fit overflow | **Image stays within bounds**; use pan to explore |
| 9 | 4-Point quadrilateral order | **Any order accepted**; system computes convex hull |
| 10 | Help trigger | **ⓘ icon click**, **F1**, or **?** key |

### Remaining Open Questions
| # | Question | Status |
|---|----------|--------|
| A | Should Single Photo mode allow cropping without corrections? | Open |
| B | Maximum bounding box count (performance limit)? | Open |
| C | Default zoom level on overview load (fit, 100%, or last used)? | Open |

---

## 14. Delete and Selection Behavior

### Selection States

| State | Behavior |
|-------|----------|
| **No box selected** (default on load) | Overview shows all boxes, no selection highlight; Delete key does nothing |
| **Box selected** | Highlighted border; Delete key removes box; can move/resize |
| **Corner selected** | Corner handle highlighted; arrow keys move corner |
| **Deselected** | Click outside any box or press Escape |

### Delete Flow

1. User clicks on a bounding box → Box becomes selected (highlighted)
2. User presses `Delete` or `Backspace` → Box is removed
3. If in overview: Box removed, remaining boxes stay selected
4. If in refinement: Box removed, return to overview
5. Undo available via `Ctrl+Z`

### Delete Triggers

| Method | Context | Behavior |
|--------|---------|----------|
| Delete key | Box selected | Remove box |
| Backspace key | Box selected | Remove box |
| 🗑️ Trash icon | Overview: delete selected; Refinement: delete current | Remove box |
| Right-click → Delete | On any box | Remove box |

### Empty State

When all bounding boxes are deleted:
- Show empty canvas with "Click anywhere to add a bounding box" message
- No boxes to select or delete
- User can click to create new boxes

---

## 15. Aspect Ratio Dropdown Feature

### Summary
Each photo card in the summary screen includes an **Aspect Ratio dropdown** that controls the output dimensions for perspective correction.

### Available Ratios
| Option | Ratio (W:H) | Description |
|--------|-------------|-------------|
| Current | Dynamic | No snapping, uses detected corner approximation |
| 1:1 | 1.0 | Square |
| 4:3 | 1.333 | Standard monitor/TV |
| 3:2 | 1.5 | 4x6 print, DSLR (default) |
| 5:4 | 1.25 | 8x10 print |
| 3:4 | 0.75 | Portrait 4x6 |
| 2:3 | 0.667 | Portrait 4x6 (vertical) |
| 16:9 | 1.778 | Wide screen |
| Custom | User-defined | Manual width:height input |

### Auto-Selection Logic
When perspective correction is enabled:
1. Calculate aspect ratio from detected corner positions
2. Find closest standard ratio:
   ```
   closestRatio = min(standardRatios, key={r => abs(r - currentRatio)})
   ```
3. Pre-select the closest ratio in dropdown

### Portrait/Landscape Orientation Handling

**Purpose**: Ensure aspect ratio is applied in the correct direction based on detected photo orientation.

**Detection Logic**:
1. Calculate detected bounding box width and height from corner coordinates
2. Determine if detected photo is **portrait** (height > width) or **landscape** (width >= height)
3. When user selects an aspect ratio, determine the "natural orientation" of that ratio:
   - Ratio >= 1.0: Natural landscape orientation
   - Ratio < 1.0: Natural portrait orientation

**Application Rules**:

| Detected Orientation | Selected Ratio | Action |
|---------------------|----------------|--------|
| Landscape | Ratio >= 1.0 (landscape) | Use ratio as-is (e.g., 3:2 → 3:2) |
| Landscape | Ratio < 1.0 (portrait) | **Flip** ratio for output (e.g., 3:4 → 4:3 output) |
| Portrait | Ratio < 1.0 (portrait) | Use ratio as-is (e.g., 3:4 → 3:4) |
| Portrait | Ratio >= 1.0 (landscape) | **Flip** ratio for output (e.g., 3:2 → 2:3 output) |
| Square (1:1) | Any | Use 1:1 regardless of selection |

**Implementation**:
```
fun getOutputAspectRatio(detectedWidth: Double, detectedHeight: Double, selectedRatio: Double): Double {
    val detectedIsPortrait = detectedHeight > detectedWidth
    val ratioIsPortrait = selectedRatio < 1.0
    
    return when {
        // Square detection - always use 1:1
        abs(detectedWidth - detectedHeight) < threshold -> 1.0
        
        // Same orientation - use as-is
        detectedIsPortrait == ratioIsPortrait -> selectedRatio
        
        // Different orientations - flip the ratio
        else -> 1.0 / selectedRatio
    }
}
```

**User-Facing Behavior**:
- User selects "3:4" for a landscape photo → Output becomes "4:3" (landscape format)
- User selects "3:2" for a portrait photo → Output becomes "2:3" (portrait format)
- User selects "Current" → Output matches detected orientation
- User selects "1:1" → Output is always square regardless of detected orientation

**UI Indication**:
- Show small icon (📐 landscape or 📱 portrait) next to selected ratio
- Tooltip explains: "Will be adjusted to match detected photo orientation"

### Behavior

- **Perspective mode**: Dropdown visible and enabled; controls output dimensions
- **Rotation mode**: Dropdown disabled/hidden (rotation uses detected dimensions)
- **"Current"**: Outputs image with no ratio snapping, matching detected corners
- **Custom**: Shows numeric input for custom width:height (e.g., "7:5")

### Bounding Box Expansion
When applying perspective correction, expand the output canvas to fit all detected corners within the selected aspect ratio:
1. Calculate center of detected region
2. Determine dimensions that fit all corners at target ratio
3. Expand canvas as needed to ensure no corners are clipped
4. Apply perspective transform to fill the expanded frame

---

## 16. Bulk Change Buttons Feature

### Summary
The summary screen includes bulk action buttons at the top of the photo list, allowing users to apply settings to all photos at once.

### Available Bulk Actions

| Button | Icon | Action | Result |
|--------|------|--------|--------|
| Apply Perspective to All | ☐→☑ | Enable perspective correction for all photos | Disables rotation on all |
| Apply Rotation to All | ↻ | Enable rotation correction for all photos | Disables perspective on all |
| Clear All Corrections | ✕ | Disable all corrections | Photos will be cropped but not corrected |
| Reset All to Default | ↺ | Reset to default settings | Perspective enabled, 3:2 ratio |

### UI Placement
Bulk buttons are positioned at the top of the summary list, above the scrollable photo cards:

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  [☑ Perspective to All]  [↻ Rotation to All]                │
│  [✕ Clear All]           [↺ Reset All]                      │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  ┌─────────────┐  Photo 1                                    │
│  │             │  ...                                        │
│  └─────────────┘                                             │
│  ┌─────────────┐  Photo 2                                    │
│  └─────────────┘                                             │
│  ...                                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Button Behavior

**Apply Perspective to All**:
1. Set all photos to use perspective correction
2. Disable rotation correction on all photos
3. Show confirmation tooltip: "Perspective correction applied to X photos"

**Apply Rotation to All**:
1. Set all photos to use rotation correction
2. Disable perspective correction on all photos
3. Show confirmation tooltip: "Rotation correction applied to X photos"

**Clear All Corrections**:
1. Disable both perspective and rotation on all photos
2. Photos will be cropped to detected bounding boxes without transformation
3. Show confirmation tooltip: "All corrections cleared"

**Reset All to Default**:
1. Set perspective correction = enabled
2. Set rotation correction = disabled
3. Set aspect ratio = 3:2 (default)
4. Reset any rotation (left/right) applied
5. Show confirmation tooltip: "All photos reset to defaults"

### Keyboard Shortcuts for Bulk Actions

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+P | Apply perspective to all |
| Ctrl+Shift+R | Apply rotation to all |
| Ctrl+Shift+C | Clear all corrections |
| Ctrl+Shift+D | Reset all to default |

### Confirmation Behavior
- Bulk actions take effect immediately (no confirmation dialog)
- Each photo card updates in real-time to reflect the bulk change
- Users can still make individual adjustments after bulk application

### Edge Cases
- **No photos in list**: Bulk buttons are disabled/hidden
- **All photos already have setting**: Button still works (no-op with visual feedback)
- **Mixed settings**: Bulk action overrides all to new state

---

## 17. State/Mode Definitions

The wizard operates in distinct modes with clear transitions. Understanding these modes is essential for implementation.

### 17.1 Mode Hierarchy

```
┌─────────────────────────────────────────────────────────────────┐
│                        APPLICATION STATES                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐     ┌──────────────────┐                │
│  │   Import Screen   │────▶│   Photo Scan     │                │
│  │   (Mode Select)   │     │   or Single      │                │
│  └──────────────────┘     └────────┬─────────┘                │
│                                     │                           │
│                                     ▼                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    OVERVIEW MODE                          │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │ Sub-modes (mutually exclusive):                       │ │  │
│  │  │                                                      │ │  │
│  │  │  [NORMAL]      - Default: select, delete, zoom       │ │  │
│  │  │       │                                                │ │  │
│  │  │       ├─▶ [4-POINT MODE] - Click 4 corners            │ │  │
│  │  │       │                                              │ │  │
│  │  │       └─▶ [ADD BOX MODE] - Click once for rect box    │ │  │
│  │  │                                                      │ │  │
│  │  │  Click on existing box ──▶ [REFINEMENT MODE]          │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                     │                           │
│                                     ▼ (Click "To Summary")      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   SUMMARY MODE                             │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │  View/edit correction options per photo               │ │  │
│  │  │  Click preview ──▶ [REFINEMENT MODE] (for that box)  │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                     │                           │
│                                     ▼ (Click "Process")         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │               PROCESSING & EXPORT                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 17.2 Overview Mode - Sub-Modes

| Mode | Entry Action | Exit Action | Visual Indicator |
|------|-------------|-------------|------------------|
| **Normal** | Default state | Click 4-Point or Add Box button | Normal cursor |
| **4-Point** | Click "4-Point" button | Complete 4 clicks, Escape, or Backspace | Crosshair cursor + "Point X of 4" status |
| **Add Box** | Click "Add Box" button | Click to place, Escape | Crosshair cursor + "Click to add box" status |

**Mode Rules**:
- Only ONE sub-mode active at a time
- Clicking a sub-mode button toggles that mode (click again to cancel)
- Pressing Escape exits current mode and returns to Normal
- Mode is shown in status bar: `[NORMAL]`, `[4-POINT]`, `[ADD BOX]`

### 17.3 Refinement Mode

**Entry**: Click on an existing bounding box in Overview.

**Available Actions** (Refinement does NOT have 4-Point mode):
- Drag entire box (click interior)
- Drag corners (click corner handles)
- Select corner for arrow key movement
- Scroll to expand/contract box
- Shift+scroll to rotate box
- Zoom in/out
- Delete current box
- Navigate Previous/Next

**Exit**: Click "← Back" or "Done" to return to Overview.

### 17.4 Mode Transition Rules

| Current Mode | Action | New Mode |
|--------------|--------|----------|
| Import Screen | Click "Import" | Overview (Normal) |
| Overview (Normal) | Click "4-Point" | Overview (4-Point) |
| Overview (Normal) | Click "Add Box" | Overview (Add Box) |
| Overview (Normal) | Click on box | Refinement |
| Overview (4-Point) | Complete 4 clicks | Overview (Normal) + new box selected |
| Overview (Add Box) | Click in valid area | Overview (Normal) + new box selected |
| Any | Click "← Back" | Previous view |
| Refinement | Click "Done" | Overview (Normal) |
| Summary | Click preview | Refinement (specific box) |

### 17.5 State Indicator UI

The current mode/state should always be visible:

```
┌─────────────────────────────────────────────────────────────┐
│ [← Back]  [4-Point]  [Add Box]      [🔍-] [⛶] [🔍+]  [ⓘ]  │
│           ◇◇◇◇           □□□□                    [NORMAL]  │
│                                                             │
│  Status: Click a box to edit, or add a new box             │
└─────────────────────────────────────────────────────────────┘
```

**Status Messages by Mode**:
| Mode | Status Message |
|------|----------------|
| Normal | "Click a box to edit, or add a new box" |
| 4-Point | "Click to set point 1 of 4" / "2 of 4" / etc. |
| Add Box | "Click to place bounding box" |
| Refinement | "Drag corners or box to refine" |

---

## 18. Error Handling & Edge Cases

### 18.1 CV Detection Results

**No boxes detected** (CV enabled but returns empty):
1. Show informational message: "No photos detected in this image"
2. Offer options:
   - "Start with empty list" (use Add Box or 4-Point manually)
   - "Retry detection" (re-run CV)
   - "Import different image" (return to import screen)
3. Empty state shows: "No bounding boxes. Click 'Add Box' or '4-Point' to add photos manually"

**Fewer boxes than expected**:
- Show detected count: "Found 2 photos. Add more if needed."
- Allow user to add missing boxes
- No automatic retry

### 18.2 Image Loading Errors

| Error | Handling |
|-------|----------|
| File not found | Show error dialog, offer to browse for file |
| Unsupported format | Show "Unsupported format. Use JPEG, PNG, or WebP." |
| Corrupted file | Show "Could not load image. File may be corrupted." |
| Memory error | Show "Image too large. Try a smaller resolution." |
| Timeout | Show "Loading took too long. Try again or use a smaller image." |

**Recovery Options**:
- "Try Again" - Retry the failed operation
- "Choose Different Image" - Return to file picker
- "Continue Anyway" - May be disabled for critical errors

### 18.3 4-Point Mode Edge Cases

**Points too close together**:
- Minimum distance between any two points: 5% of image dimension
- If too close, show warning: "Points too close together. Please spread them out."
- Do not create box if validation fails

**Points in invalid order**:
- System accepts ANY 4 points (no order required)
- Computes convex hull to determine quadrilateral
- Validates resulting shape is convex and has reasonable area

**Invalid quadrilateral** (self-intersecting):
- Detect self-intersecting edges
- If invalid, show: "Invalid shape. Points must form a simple quadrilateral."
- Allow user to reposition points

### 18.4 Export Errors

**Partial export failure**:
1. If export fails for some photos:
   - Show: "Exported 3 of 5 photos. 2 failed."
   - List failed photos with error reason
   - Offer: "Retry Failed" or "Skip Failed"
2. Completed photos are saved; user can retry failed ones

**Export errors by type**:
| Error | Message |
|-------|---------|
| Disk full | "Not enough disk space. Free up space and try again." |
| Write permission | "Cannot write to destination folder. Check permissions." |
| File exists | "File already exists: {name}. Overwrite or rename?" |
| Invalid path | "Destination folder not found. Choose a different location." |

### 18.5 Undo/Redo Limits

**Stack overflow prevention**:
- Maximum 50 undo operations per bounding box
- Maximum 200 total operations across all boxes
- If limit reached, oldest operations are discarded
- No warning shown; oldest simply removed from stack

**Memory protection**:
- If undo stack exceeds 10MB, truncate oldest 25%
- Document this limitation in help text

---

## 19. Single Photo Mode Details

### 19.1 Mode Behavior

Single Photo mode is designed for importing individual photos without multi-box detection.

**Entry**: Select "Single Photo" mode on Import screen, then click "Import Image".

**Flow**:
```
┌──────────────────┐
│   Import Screen   │ ──▶ Select "Single Photo"
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│   File Picker     │ ──▶ Choose image
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Single Photo    │
│  Crop/Refinement │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Summary         │
│  (No multi-box)  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Processing      │
└──────────────────┘
```

### 19.2 Single Photo Refinement

**No bounding box detection** (CV toggle disabled):
- Single box created automatically covering entire image
- User refines the box to define crop area
- All refinement features available (drag, corners, zoom)

**Refinement UI**:
```
┌─────────────────────────────────────────────────────────────┐
│ [← Back]                   [🔍-] [⛶] [🔍+]  [ⓘ]           │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │                                                     │   │
│  │              Full Image with                        │   │
│  │              One Bounding Box                       │   │
│  │              (User adjusts to crop)                  │   │
│  │                                                     │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [Done Refinement]                           Zoom: 100%    │
└─────────────────────────────────────────────────────────────┘
```

### 19.3 No "Add Box" or "4-Point" in Single Photo Mode

Since only one photo is expected:
- **"4-Point" button**: Hidden or disabled (not applicable)
- **"Add Box" button**: Hidden or disabled
- Only one box exists; click on box enters refinement

### 19.4 Summary Screen (Single Photo)

**Simplified UI**:
```
┌─────────────────────────────────────────────────────────────┐
│                    Photo 1                                    │
│  ┌────────────────┐                                          │
│  │                │  Aspect Ratio: [Current ▼]              │
│  │   Preview      │                                          │
│  │                │  ☐ Perspective Correction                 │
│  │                │  ☐ Rotation Correction                    │
│  │                │                                          │
│  └────────────────┘                                          │
│                                                              │
│  [Process & Export]                                          │
└─────────────────────────────────────────────────────────────┘
```

**Differences from Photo Scan Summary**:
- No bulk buttons (only one photo)
- No photo index (just "Photo 1")
- "Process & Export" instead of list

---

## 20. Keyboard Shortcut Summary

### 20.1 Complete Shortcut Reference

#### Import Screen
| Shortcut | Action |
|----------|--------|
| Enter | Confirm import with current settings |
| Escape | Cancel and return to previous screen |

#### Overview Mode
| Shortcut | Action |
|----------|--------|
| D | Toggle 4-Point mode |
| A | Toggle Add Box mode |
| Escape | Cancel 4-Point/Add Box mode, or deselect |
| Delete | Delete selected box |
| Backspace | Delete selected box |
| Ctrl+A | Select all boxes |
| Ctrl+Plus | Zoom in |
| Ctrl+Minus | Zoom out |
| Ctrl+0 | Fit to view |

#### 4-Point Mode (Overview sub-mode)
| Shortcut | Action |
|----------|--------|
| Escape | Cancel and return to Normal |
| Backspace | Remove last placed point |
| Enter | Confirm (when 4 points placed) |

#### Refinement Mode
| Shortcut | Action |
|----------|--------|
| Arrow keys | Move selected corner (1px) |
| Shift+Arrow | Move selected corner (10px) |
| Escape | Deselect corner, or return to Overview |
| Delete | Delete current box, return to Overview |
| Ctrl+Left | Previous box |
| Ctrl+Right | Next box |
| Ctrl+Plus | Zoom in |
| Ctrl+Minus | Zoom out |
| Ctrl+0 | Fit box to view |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |

#### Summary Mode
| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+P | Apply perspective to all |
| Ctrl+Shift+R | Apply rotation to all |
| Ctrl+Shift+C | Clear all corrections |
| Ctrl+Shift+D | Reset all to default |
| Enter | Process & Export |
| Escape | Back to Overview |

### 20.2 Conflict Resolution

**Priority rules when shortcuts conflict**:
1. **Escape** always exits current mode first, then navigates back
2. **Delete/Backspace** requires selection; if none, key does nothing
3. **Ctrl+arrows** in Refinement mode navigate boxes; in Overview (no selection) also navigate
4. **Enter** confirms action (4-Point complete, process export) but never acts as delete

---

## 21. Help/Info Overlay

### 21.1 Info Icon (ⓘ) Behavior

**Trigger**: Click on ⓘ icon in toolbar.

**Display**: Modal overlay covering the screen with keyboard/mouse reference.

**Contents**:
```
┌─────────────────────────────────────────────────────────────┐
│                     Keyboard & Mouse Reference              │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  🖱️ Mouse Controls                                     ││
│  │  ─────────────────────────────────────────────────────  ││
│  │  • Click + drag box: Move entire bounding box           ││
│  │  • Click + drag corner: Resize at that corner           ││
│  │  • Click near corner: Select corner for arrow keys      ││
│  │  • Scroll: Expand/contract box (hover on box)           ││
│  │  • Scroll: Zoom view (not hovering on box)             ││
│  │  • Shift + scroll: Rotate box                          ││
│  │  • Right-click: Context menu (delete, etc.)            ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  ⌨️ Keyboard Shortcuts                                  ││
│  │  ─────────────────────────────────────────────────────  ││
│  │  • D: 4-Point mode      A: Add Box mode                ││
│  │  • Arrow keys: Move selected corner                    ││
│  │  • Shift + arrows: Move by 10px                        ││
│  │  • Delete: Remove selected box                          ││
│  │  • Ctrl+Z: Undo       Ctrl+Shift+Z: Redo              ││
│  │  • Escape: Cancel / Deselect / Go back                ││
│  │  • Ctrl+←/→: Navigate between boxes                    ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│                                          [Close] (or press Esc)│
└─────────────────────────────────────────────────────────────┘
```

### 21.2 Help Trigger Options

| Trigger | Action |
|---------|--------|
| Click ⓘ icon | Open help overlay |
| Press F1 | Open help overlay |
| Hover ⓘ for 1s | Show quick tooltip (abbreviated shortcuts) |
| Press ? | Open help overlay |

### 21.3 Content by Mode

**Overview Help** (Normal mode):
- All Overview shortcuts
- 4-Point mode instructions
- Add Box mode instructions

**Refinement Help**:
- Corner dragging
- Arrow key movement
- Scroll behaviors
- Zoom controls

**Summary Help**:
- Bulk action shortcuts
- Correction options explanation

---

*Document maintained by: Development Team*  
*Last Updated: 2026-04-08*  
*Version: 1.2*
