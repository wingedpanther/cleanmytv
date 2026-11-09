# Technical implementation guide

## 1. Architecture

Separate the implementation into four responsibilities:

Composable UI

LazyColumn · Checkbox · Drag Handle · Focus

Interaction Controller

Normal mode · Reorder mode · D-pad events

ViewModel / StateFlow

Ordered items · Selected IDs · Original order

Repository / Persistence

Save confirmed ordering

The important design decision is that the UI should not independently manipulate several copies of the list. Keep one authoritative ordered collection in the ViewModel.

## 2. Define the item model and UI state

Use stable IDs for both selection and reordering.

```
data class ListItem(
    val id: String,
    val title: String,
    val isChecked: Boolean = false
)

enum class ListInteractionMode {
    NORMAL,
    REORDERING
}

data class ListUiState(
    val items: List<ListItem> = emptyList(),
    val mode: ListInteractionMode =
        ListInteractionMode.NORMAL,
    val movingItemId: String? = null,
    val originalOrder: List<String> = emptyList()
)
```

Checkbox state belongs to the item or a separate selected-ID set. Don't use the item's current index to identify its checkbox state.

## 3. Implement the ViewModel reorder operations

The ViewModel should own the actual movement logic.

```
fun moveItem(itemId: String, offset: Int) {
    val current = _uiState.value
    val items = current.items.toMutableList()

    val fromIndex = items.indexOfFirst {
        it.id == itemId
    }

    if (fromIndex == -1) return

    val toIndex = fromIndex + offset

    if (toIndex !in items.indices) return

    val item = items.removeAt(fromIndex)
    items.add(toIndex, item)

    _uiState.update {
        it.copy(items = items)
    }
}
```

Use `offset = -1` for Up and `offset = 1` for Down.

Other ViewModel operations should include:

- `startReordering(itemId)`
- `moveItem(itemId, offset)`
- `confirmReordering()`
- `cancelReordering()`
- `toggleSelection(itemId)`

On starting a reorder, capture the original ordering. On cancellation, restore it. On confirmation, persist the final order.

## 4. Implement the interaction state machine

NORMAL MODE

D-pad navigates · OK toggles checkbox

OK on drag handle

REORDER MODE

D-pad Up/Down moves the item

OK

Confirm

Back

Cancel

Do not let checkbox click handlers execute while reorder mode is active.

## 5. Build the LazyColumn and item composable

Use stable keys and animate item positions.

```
LazyColumn {
    items(
        items = uiState.items,
        key = { it.id }
    ) { item ->

        ListItemRow(
            item = item,
            isMoving = uiState.movingItemId == item.id,
            onCheckboxClick = {
                viewModel.toggleSelection(item.id)
            },
            onReorderStart = {
                viewModel.startReordering(item.id)
            }
        )
    }
}
```

A conceptual item layout:

```
@Composable
fun ListItemRow(
    item: ListItem,
    isMoving: Boolean,
    onCheckboxClick: () -> Unit,
    onReorderStart: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = {
                onCheckboxClick()
            }
        )

        Text(
            text = item.title,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onReorderStart
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder item"
            )
        }
    }
}
```

For the final implementation, make the drag handle explicitly focusable and ensure the checkbox and handle have independent click and focus behaviour.

## 6. Handle Android TV remote events

This is one of the most important parts.

Use Compose key event handling and distinguish between normal and reorder modes.

Conceptually:

```
Modifier.onPreviewKeyEvent { event ->

    if (uiState.mode !=
        ListInteractionMode.REORDERING
    ) {
        return@onPreviewKeyEvent false
    }

    if (event.type != KeyEventType.KeyUp) {
        return@onPreviewKeyEvent true
    }

    when (event.nativeKeyEvent.keyCode) {

        KeyEvent.KEYCODE_DPAD_UP -> {
            viewModel.moveItem(
                uiState.movingItemId!!, -1
            )
            true
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> {
            viewModel.moveItem(
                uiState.movingItemId!!, 1
            )
            true
        }

        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER -> {
            viewModel.confirmReordering()
            true
        }

        KeyEvent.KEYCODE_BACK -> {
            viewModel.cancelReordering()
            true
        }

        else -> true
    }
}
```

Important implementation details:

- Don't handle both KeyDown and KeyUp for the same action, or each press could move twice.
- Decide whether held-key repeat should move continuously or one position per press.
- Consume directional events during reorder mode so normal focus navigation doesn't interfere.
- Use Compose `BackHandler` as appropriate for system Back navigation, rather than relying exclusively on `KEYCODE_BACK`.
- Handle OK and Enter consistently across supported remotes.

## 7. Focus management

Android TV requires special attention to focus restoration.

Use `FocusRequester` where necessary.

Recommended behaviour:

| Event             | Focus behaviour                         |
| ----------------- | --------------------------------------- |
| Navigate normally | Focus current row                       |
| Focus drag handle | Highlight handle                        |
| Pick up item      | Keep moving item active                 |
| Move Up/Down      | Keep reorder interaction active         |
| Confirm           | Restore focus to moved item's handle    |
| Cancel            | Restore focus to original item's handle |

Don't use list position as the focus identity. When an item moves from index 5 to index 2, its identity should remain the same.

Also, avoid allowing the `LazyColumn` to move focus to an unrelated item while the selected item is being reordered.

## 8. Animation

For modern Compose versions, use `Modifier.animateItem()` on each lazy-list item.

For older versions, use the corresponding `animateItemPlacement()` API if supported.

```
LazyColumn {
    items(
        items = uiState.items,
        key = { it.id }
    ) { item ->

        ListItemRow(
            modifier = Modifier.animateItem(),
            // Other parameters
        )
    }
}
```

You can also apply a subtle visual distinction:

- Moving item: elevated surface and highlighted border.
- Normal items: standard background.
- Drag handle: highlighted while focused.

Avoid excessive scaling or bouncing animations because Android TV interfaces should remain visually stable.

## 9. Persistence strategy

I recommend saving the order only after confirmation.

A typical flow:

```
Start reorder
    |
Capture original item IDs
    |
Move item up/down
    |
Temporary UI state updates
    |
User confirms
    |
Save ordered item IDs
    |
Return to normal mode
```

If the user cancels:

```
Cancel reorder
    |
Restore original item order
    |
Return to normal mode
```

Use the existing repository or DataStore/Room persistence approach, depending on the application's architecture. Avoid performing database writes for every D-pad movement.

## 10. Important implementation considerations

These are the areas I'd specifically ask the AI agent to pay attention to.

| Area           | Recommendation                      |
| -------------- | ----------------------------------- |
| Item identity  | Stable unique IDs                   |
| Checkbox state | Independent of position             |
| List updates   | Immutable state updates             |
| Reorder state  | Explicit state machine              |
| Focus          | FocusRequester and stable identity  |
| Animation      | LazyColumn item placement animation |
| Key events     | Consume only relevant events        |
| Persistence    | Save on confirmation                |
| Cancellation   | Restore original order              |

One additional consideration: If your list is backed by a Flow or database, make sure an incoming data refresh doesn't overwrite the temporary reordered list while the user is moving an item. Handle that transaction carefully.

## 11. Suggested testing scenarios

Implementation test checklist

0/11 passed

Select multiple checkboxes and navigate normally.

Focus a drag handle and pick up an item.

Move an unchecked item several positions down.

Move a checked item several positions up.

Verify checkbox states remain unchanged.

Confirm the new ordering.

Cancel another reorder and verify restoration.

Test the first and last list positions.

Hold D-pad Up/Down and test key repeat.

Navigate normally after confirming or cancelling.

Restart the app and verify persisted ordering.

## 12. Add this technical guidance to your AI coding prompt

You can append this section to the previous prompt:

### Technical Implementation Guidance

Implement the feature using a state-driven architecture with Kotlin and Jetpack Compose.

1. Use LazyColumn with stable item keys for the vertical list.
2. Keep the ordered list in a single authoritative ViewModel state.
3. Use stable item IDs to track checkbox selection, focused items and moving items.
4. Implement a dedicated focusable drag handle for each list item.
5. Use an explicit NORMAL / REORDERING interaction state.
6. Use FocusRequester or an equivalent Compose focus-management approach to restore focus after reordering.
7. Handle Android TV D-pad key events without allowing normal focus navigation to interfere during reorder mode.
8. Process key events consistently, avoiding duplicate movement from KeyDown and KeyUp.
9. Update the ordered collection immutably when moving items.
10. Preserve checkbox selection independently of list ordering.
11. Use animateItem() or the appropriate animation API supported by the project's Compose version.
12. Maintain a snapshot of the original order to support cancellation.
13. Persist the final ordering only after confirmation.
14. Handle asynchronous list updates without overwriting an active reorder transaction.

Keep UI rendering, interaction handling, state management and persistence responsibilities clearly separated.

Before implementing, inspect the project's Compose version, architecture, existing components and state-management conventions. Adapt the implementation to the actual project instead of blindly introducing new dependencies or replacing existing infrastructure.

Provide working Kotlin code, not just pseudocode. Compile the application, resolve errors and test D-pad navigation, focus restoration, checkbox independence, reorder confirmation and cancellation.

My strongest recommendation: Let the coding agent implement the ViewModel/state logic first, then the Compose UI and focus handling. The most challenging part isn't swapping list elements; it's ensuring Android TV focus doesn't fight with the reorder interaction.