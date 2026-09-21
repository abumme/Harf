## ADDED Requirements

### Requirement: Board and keyboard adapt to the viewport
The game screen SHALL keep the board and the on-screen keyboard fully visible on any viewport, adapting its layout rather than clipping or scrolling the play area. When the viewport is tall it SHALL stack them vertically and scale the board to fit the available height; when the viewport is wide it SHALL place the board and keyboard side by side.

#### Scenario: Tall viewport fits both by scaling
- **WHEN** the game is shown on a tall/portrait viewport where the default tile size would overflow (small phone, large font scale)
- **THEN** the board tiles SHALL scale down so the board and the full keyboard are both visible without clipping

#### Scenario: Tall viewport with ample height keeps the standard size
- **WHEN** the game is shown on a tall viewport with enough height for the standard tile size
- **THEN** the board SHALL render at its standard size (unchanged from the non-adaptive layout)

#### Scenario: Wide viewport uses a side-by-side layout
- **WHEN** the game is shown on a wide viewport (landscape phone, desktop, web, or tablet in landscape)
- **THEN** the board and the on-screen keyboard SHALL be laid out side by side, each fully visible, using the available width

#### Scenario: Play area is never clipped or scrolled
- **WHEN** the game is shown on any supported viewport
- **THEN** neither the board nor the keyboard SHALL be cut off, and the play area SHALL NOT require scrolling to reach a row or a key
