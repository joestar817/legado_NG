package io.legado.app.ui.main.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetMutationTest {
    @Test
    fun singleEditingCreatesAPendingLayoutWithoutActivatingAllWidgets() {
        val saved = threeWidgetLayout()
        val state = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        assertTrue(state.pendingEdit)
        assertFalse(state.editing)
        assertEquals(saved[0].id, state.singleEditId)
        assertSame(saved, state.widgets)
        assertSame(saved, state.draft)
    }

    @Test
    fun cancellingSingleMoveAndDeletionRestoresTheOriginalLayout() {
        val saved = threeWidgetLayout()
        val started = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
        val removed = removeHomeWidget(moved, saved[0].id)!!
        assertTrue(removed.pendingEdit)
        assertFalse(removed.editing)
        assertEquals(saved[0].id, removed.singleEditId)
        assertSame(saved, removed.widgets)
        val cancelled = cancelHomeWidgetEditing(removed)
        assertEquals(HomeUiState(saved), cancelled)
        assertFalse(cancelled.pendingEdit)
        assertSame(saved, cancelled.widgets)
        val onlyWidget = beginHomeWidgetEditing(HomeUiState(listOf(saved[0])), saved[0].id)!!
        val emptyDraft = removeHomeWidget(onlyWidget, saved[0].id)!!
        assertTrue(emptyDraft.pendingEdit)
        assertFalse(emptyDraft.editing)
        assertTrue(emptyDraft.displayedWidgets.isEmpty())
        assertEquals(saved[0].id, emptyDraft.singleEditId)
        assertEquals(listOf(saved[0]), cancelHomeWidgetEditing(emptyDraft).widgets)
    }

    @Test
    fun drawerSaveCommitsPriorSingleSessionChangesAndExitsEditing() {
        val saved = threeWidgetLayout()
        val started = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
        val focused = beginHomeWidgetEditing(moved, saved[1].id)!!
        val removed = removeHomeWidget(focused, saved[1].id)!!
        val refocused = beginHomeWidgetEditing(removed, saved[0].id)!!
        val confirmed = saveHomeWidgetVariant(refocused, saved[0].id, "night-large")!!
        assertEquals(listOf(saved[2], saved[0].copy(variantId = "night-large")), confirmed.widgets)
        assertFalse(confirmed.pendingEdit)
        assertFalse(confirmed.editing)
        assertNull(confirmed.singleEditId)
        assertEquals(threeWidgetLayout(), saved)
    }

    @Test
    fun unchangedDrawerSelectionStillCommitsTheWholeBulkDraft() {
        val saved = threeWidgetLayout()
        val draft = saved.reversed().dropLast(1)
        val state = HomeUiState(saved, draft)
        val confirmed = saveHomeWidgetVariant(state, draft[0].id, draft[0].variantId)!!
        assertEquals(HomeUiState(draft), confirmed)
        assertFalse(confirmed.pendingEdit)
    }

    @Test
    fun invalidDrawerSaveCannotCommitOrDiscardPendingChanges() {
        val saved = threeWidgetLayout()
        val started = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
        assertNull(saveHomeWidgetVariant(moved, "missing", "large"))
        assertNull(saveHomeWidgetVariant(moved, saved[0].id, "missing"))
        assertTrue(moved.pendingEdit)
        assertEquals(saved[0].id, moved.singleEditId)
        assertSame(saved, moved.widgets)
    }

    @Test
    fun changingFocusOrPromotingToBulkKeepsOneOriginalSnapshot() {
        val saved = threeWidgetLayout()
        val started = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
        val focused = beginHomeWidgetEditing(moved, saved[1].id)!!
        assertFalse(focused.editing)
        assertSame(moved.draft, focused.draft)
        val bulk = beginHomeWidgetEditing(focused)!!
        assertTrue(bulk.editing)
        assertNull(bulk.singleEditId)
        assertSame(moved.draft, bulk.draft)
        assertSame(bulk, beginHomeWidgetEditing(bulk, saved[0].id))
        assertEquals(HomeUiState(saved), cancelHomeWidgetEditing(bulk))
    }

    @Test
    fun aMissingTargetOrFailedLayoutCannotStartAnEditSession() {
        val saved = threeWidgetLayout()
        assertNull(beginHomeWidgetEditing(HomeUiState(saved), "missing"))
        assertNull(beginHomeWidgetEditing(HomeUiState(saved, layoutReadFailed = true), saved[0].id))
        assertNull(beginHomeWidgetEditing(HomeUiState(saved, layoutReadFailed = true)))
    }

    @Test
    fun localMoveChangesOrderWithoutEnteringBulkEditingOrReplacingInstances() {
        val saved = threeWidgetLayout()
        val moved = moveHomeWidgetState(HomeUiState(saved), saved[0].id, saved[2].id)!!
        assertFalse(moved.editing)
        assertEquals(listOf(saved[1], saved[2], saved[0]), moved.widgets)
        assertSame(saved[0], moved.widgets.last())
        assertEquals(threeWidgetLayout(), saved)
    }

    @Test
    fun bulkMovePreservesSavedLayoutAndOtherPendingChanges() {
        val saved = threeWidgetLayout()
        val changed = saved[0].copy(variantId = "night-large")
        val draft = listOf(saved[2], changed)
        val moved = moveHomeWidgetState(HomeUiState(saved, draft), changed.id, saved[2].id)!!
        assertTrue(moved.editing)
        assertSame(saved, moved.widgets)
        assertEquals(listOf(changed, saved[2]), moved.draft)
        assertSame(changed, moved.displayedWidgets.first())
    }

    @Test
    fun staleNoOpAndFailedLayoutMovesDoNotTriggerSaving() {
        val saved = threeWidgetLayout()
        val state = HomeUiState(saved)
        assertNull(moveHomeWidgetState(state, saved[0].id, saved[0].id))
        assertNull(moveHomeWidgetState(state, "missing", saved[0].id))
        assertNull(moveHomeWidgetState(state, saved[0].id, "missing"))
        assertNull(moveHomeWidgetState(state.copy(layoutReadFailed = true), saved[0].id, saved[1].id))
    }

    @Test
    fun normalRemoveDeletesOnlyTheRequestedInstanceWithoutCreatingADraft() {
        val first = HomeWidgetInstance("one", "reading", "small")
        val second = HomeWidgetInstance("two", "reading", "night-large")
        val saved = listOf(first, second)
        val updated = removeHomeWidget(HomeUiState(saved), first.id)!!
        assertFalse(updated.editing)
        assertEquals(listOf(second), updated.widgets)
        assertSame(second, updated.widgets.single())
        assertEquals(2, saved.size)
    }

    @Test
    fun bulkRemovePreservesSavedLayoutAndOtherPendingChanges() {
        val saved = threeWidgetLayout()
        val draft = listOf(saved[2], saved[0].copy(variantId = "night-large"))
        val updated = removeHomeWidget(HomeUiState(saved, draft), saved[2].id)!!
        assertTrue(updated.editing)
        assertSame(saved, updated.widgets)
        assertEquals(listOf(draft[1]), updated.draft)
        assertSame(draft[1], updated.displayedWidgets.single())
    }

    @Test
    fun staleOrFailedLayoutRemovalCannotPersistOtherChanges() {
        val saved = threeWidgetLayout()
        assertNull(removeHomeWidget(HomeUiState(saved), "missing"))
        assertNull(removeHomeWidget(HomeUiState(saved, emptyList()), saved.first().id))
        assertNull(removeHomeWidget(HomeUiState(saved, layoutReadFailed = true), saved.first().id))
        assertEquals(emptyList<HomeWidgetInstance>(), removeHomeWidget(HomeUiState(listOf(saved.first())), saved.first().id)!!.widgets)
    }

    @Test
    fun normalAddSupportsEveryCatalogChoiceWithoutCreatingABulkDraft() {
        HomeWidgetCatalog.types.forEach { type ->
            val saved = threeWidgetLayout().filterNot { it.typeId == type.id }
            type.variants.forEach { variant ->
                val updated = addOrReplaceHomeWidget(HomeUiState(saved), type.id, variant.id, "new")!!
                assertFalse(updated.editing)
                assertNull(updated.draft)
                assertEquals(saved.map { it.id } + "new", updated.widgets.map { it.id })
                assertEquals(HomeWidgetInstance("new", type.id, variant.id), updated.widgets.last())
                saved.forEachIndexed { index, widget -> assertSame(widget, updated.widgets[index]) }
            }
        }
    }

    @Test
    fun normalEditChangesOnlyTheRequestedInstanceAmongRepeatedTypes() {
        val saved = listOf(
            HomeWidgetInstance("one", "reading", "small"),
            HomeWidgetInstance("two", "reading", "small"),
            HomeWidgetInstance("three", "calendar", "large"),
        )
        val updated = editHomeWidget(HomeUiState(saved), "two", "night-large")!!
        assertFalse(updated.editing)
        assertEquals(saved.map { it.id }, updated.widgets.map { it.id })
        assertSame(saved[0], updated.widgets[0])
        assertSame(saved[2], updated.widgets[2])
        assertEquals("night-large", updated.widgets[1].variantId)
        assertEquals("small", saved[1].variantId)
    }

    @Test
    fun addThenEditInsideBulkPreservesSavedLayoutAndAllPendingChanges() {
        val saved = threeWidgetLayout()
        val draft = saved.reversed().dropLast(1)
        val added = addOrReplaceHomeWidget(HomeUiState(saved, draft), "reading", "story-small", "new")!!
        val edited = editHomeWidget(added, "new", "night-large")!!
        assertTrue(edited.editing)
        assertSame(saved, added.widgets)
        assertSame(saved, edited.widgets)
        assertEquals(draft.map { it.id } + "new", edited.displayedWidgets.map { it.id })
        assertFalse(edited.displayedWidgets.any { it.id == saved.first().id })
        draft.forEachIndexed { index, widget -> assertSame(widget, edited.displayedWidgets[index]) }
        assertEquals("night-large", edited.displayedWidgets.last().variantId)
        assertEquals("story-small", added.displayedWidgets.last().variantId)
    }

    @Test
    fun addingToAnExplicitEmptyDraftDoesNotResurrectRemovedWidgets() {
        val saved = threeWidgetLayout()
        val updated = addOrReplaceHomeWidget(HomeUiState(saved, emptyList()), "calendar", "large", "calendar")!!
        assertSame(saved, updated.widgets)
        assertEquals(listOf(HomeWidgetInstance("calendar", "calendar", "large")), updated.draft)
    }

    @Test
    fun invalidOrStaleRequestsCannotChangeEitherSavedLayoutOrDraft() {
        val saved = threeWidgetLayout()
        val state = HomeUiState(saved, saved.drop(1))
        assertNull(editHomeWidget(state, saved.first().id, "large"))
        assertNull(editHomeWidget(state, saved[1].id, "unknown"))
        assertNull(addOrReplaceHomeWidget(state, "unknown", "small", "new"))
        assertNull(addOrReplaceHomeWidget(state, "calendar", "small", "new"))
        assertNull(addOrReplaceHomeWidget(state, "reading", "small", saved[1].id))
        assertNull(addOrReplaceHomeWidget(state, "reading", "small", " "))
        assertSame(saved, state.widgets)
        assertEquals(saved.drop(1), state.draft)
    }

    @Test
    fun savingAnUnchangedSelectionIsValidInBothModes() {
        val saved = threeWidgetLayout()
        val normal = HomeUiState(saved)
        assertEquals(normal, editHomeWidget(normal, saved.first().id, saved.first().variantId))
        assertEquals(normal, addOrReplaceHomeWidget(normal, saved.first().typeId, saved.first().variantId, "new"))
        val draft = saved.reversed().dropLast(1)
        val bulk = HomeUiState(saved, draft)
        assertEquals(bulk, editHomeWidget(bulk, draft.first().id, draft.first().variantId))
        assertEquals(bulk, addOrReplaceHomeWidget(bulk, draft.first().typeId, draft.first().variantId, "new"))
    }

    @Test
    fun aFailedLayoutReadRejectsAllMutationsWithoutResettingTheLayout() {
        val saved = threeWidgetLayout()
        listOf(HomeUiState(saved, layoutReadFailed = true), HomeUiState(saved, saved, true)).forEach { state ->
            assertNull(addOrReplaceHomeWidget(state, "reading", "small", "new"))
            assertNull(editHomeWidget(state, saved.first().id, "large"))
            assertSame(saved, state.widgets)
        }
    }

    @Test
    fun normalReplaceKeepsTheExistingIdAndPositionForEveryCatalogChoice() {
        val saved = threeWidgetLayout() + HomeWidgetInstance("calendar", "calendar", "large")
        HomeWidgetCatalog.types.forEach { type ->
            val target = saved.first { it.typeId == type.id }
            type.variants.forEach { variant ->
                val updated = addOrReplaceHomeWidget(HomeUiState(saved), type.id, variant.id, "unused")!!
                assertFalse(updated.editing)
                assertEquals(saved.map { it.id }, updated.widgets.map { it.id })
                assertEquals(variant.id, updated.widgets.first { it.id == target.id }.variantId)
                saved.filter { it.id != target.id }.forEach { other ->
                    assertSame(other, updated.widgets.first { it.id == other.id })
                }
            }
        }
    }

    @Test
    fun repeatedlyChoosingASizeOrSkinCannotCreateASecondInstanceOfTheType() {
        var state = HomeUiState()
        listOf("small", "night-small", "night-large", "story-large", "story-large").forEachIndexed { index, variant ->
            state = addOrReplaceHomeWidget(state, "reading", variant, "new-$index")!!
            assertEquals(listOf(HomeWidgetInstance("new-0", "reading", variant)), state.widgets)
        }
    }

    @Test
    fun explicitReplaceMergesOnlyTheChosenTypeAndPreservesTheFirstInstance() {
        val saved = listOf(
            HomeWidgetInstance("calendar-1", "calendar", "large"),
            HomeWidgetInstance("reading-1", "reading", "small"),
            HomeWidgetInstance("updates", "updates", "large"),
            HomeWidgetInstance("reading-2", "reading", "story-small"),
            HomeWidgetInstance("calendar-2", "calendar", "night-large"),
        )
        listOf(HomeUiState(saved), HomeUiState(saved, saved)).forEach { state ->
            val updated = addOrReplaceHomeWidget(state, "reading", "night-large", "unused")!!
            assertEquals(listOf("calendar-1", "reading-1", "updates", "calendar-2"), updated.displayedWidgets.map { it.id })
            assertEquals("night-large", updated.displayedWidgets[1].variantId)
            assertSame(saved[0], updated.displayedWidgets[0])
            assertSame(saved[2], updated.displayedWidgets[2])
            assertSame(saved[4], updated.displayedWidgets[3])
            if (state.editing) assertSame(saved, updated.widgets)
            assertEquals(5, state.displayedWidgets.size)
        }
    }

    @Test
    fun bulkReplaceUsesTheCurrentDraftAndLeavesSavedAndPendingOtherChangesAlone() {
        val saved = threeWidgetLayout()
        val draft = listOf(saved[2], saved[0].copy(variantId = "night-large"),
            HomeWidgetInstance("new-calendar", "calendar", "large"))
        val updated = addOrReplaceHomeWidget(HomeUiState(saved, draft), "reading", "story-small", "unused")!!
        assertTrue(updated.editing)
        assertSame(saved, updated.widgets)
        assertEquals(listOf("default_updates", "default_reading", "new-calendar"), updated.displayedWidgets.map { it.id })
        assertEquals("story-small", updated.displayedWidgets[1].variantId)
        assertSame(draft[0], updated.displayedWidgets[0])
        assertSame(draft[2], updated.displayedWidgets[2])
        val replacedNewItem = addOrReplaceHomeWidget(updated, "calendar", "night-large", "unused-2")!!
        assertSame(saved, replacedNewItem.widgets)
        assertEquals("new-calendar", replacedNewItem.displayedWidgets.last().id)
        assertEquals(3, replacedNewItem.displayedWidgets.size)
    }

    @Test
    fun replacementPreviewKeepsCalendarIdentityAndDoesNotMutateTheRealLayout() {
        val reading = HomeWidgetInstance("reading", "reading", "small")
        val calendar = HomeWidgetInstance("calendar", "calendar", "large")
        val saved = listOf(calendar, reading)
        val preview = homeWidgetChoiceLayout(saved, HomeWidgetInstance("temporary", "calendar", "night-large"))
        assertEquals(listOf("calendar", "reading"), preview.map { it.id })
        assertEquals("night-large", preview.first().variantId)
        assertSame(reading, preview[1])
        assertSame(calendar, saved.first())
        assertEquals("large", calendar.variantId)
    }

    @Test
    fun commonSuiteIncludesEveryInstanceRegardlessOfItsTypeOrSize() {
        val widgets = listOf(
            HomeWidgetInstance("calendar", "calendar", "story-large"),
            HomeWidgetInstance("reading-small", "reading", "story-small"),
            HomeWidgetInstance("reading-large", "reading", "story-large"),
            HomeWidgetInstance("listening", "listening", "story-small"),
        )
        assertEquals("storybook", homeWidgetCommonStyle(widgets))
        assertEquals("storybook", homeWidgetCommonStyle(HomeWidgetCatalog.defaults()))
        assertEquals("night", homeWidgetCommonStyle(listOf(widgets[0].copy(variantId = "night-large"))))
    }

    @Test
    fun anEmptyOrMixedLayoutHasNoSuiteInUse() {
        val widgets = listOf(
            HomeWidgetInstance("reading", "reading", "night-small"),
            HomeWidgetInstance("listening", "listening", "night-large"),
            HomeWidgetInstance("calendar", "calendar", "large"),
        )
        assertNull(homeWidgetCommonStyle(emptyList()))
        assertNull(homeWidgetCommonStyle(widgets))
        assertNull(homeWidgetCommonStyle(widgets.reversed()))
    }

    @Test
    fun anInvalidInstanceCannotBeIgnoredWhenDeterminingTheCommonSuite() {
        val valid = HomeWidgetInstance("reading", "reading", "small")
        listOf(
            HomeWidgetInstance("unknown", "missing", "small"),
            HomeWidgetInstance("unknown", "listening", "missing"),
            HomeWidgetInstance("calendar", "calendar", "small"),
            HomeWidgetInstance(" ", "updates", "large"),
            HomeWidgetInstance(valid.id, "updates", "large"),
        ).forEach { invalid ->
            assertNull(homeWidgetCommonStyle(listOf(valid, invalid)))
            assertNull(homeWidgetCommonStyle(listOf(invalid, valid)))
        }
    }

    @Test
    fun switchingSuitesPreservesSizesIdentitiesOrderAndTheLargeOnlyCalendar() {
        val widgets = listOf(
            HomeWidgetInstance("calendar", "calendar", "story-large"),
            HomeWidgetInstance("updates", "updates", "small"),
            HomeWidgetInstance("reading", "reading", "story-large"),
            HomeWidgetInstance("listening", "listening", "night-small"),
        )
        val night = changeHomeWidgetSuite(widgets, "night")!!
        assertEquals(listOf(
            widgets[0].copy(variantId = "night-large"),
            widgets[1].copy(variantId = "night-small"),
            widgets[2].copy(variantId = "night-large"),
            widgets[3].copy(variantId = "night-small"),
        ), night)
        listOf("basic", "storybook", "night").forEach { style ->
            val changed = changeHomeWidgetSuite(night, style)!!
            assertEquals(widgets.map { it.id to it.typeId }, changed.map { it.id to it.typeId })
            assertEquals(widgets.map { HomeWidgetCatalog.variant(it)!!.size },
                changed.map { HomeWidgetCatalog.variant(it)!!.size })
            assertEquals(style, homeWidgetCommonStyle(changed))
            assertEquals(HomeWidgetSize.LARGE, HomeWidgetCatalog.variant(changed.first())!!.size)
        }
        assertEquals(listOf("story-large", "small", "story-large", "night-small"), widgets.map { it.variantId })
    }

    @Test
    fun switchingSuitesKeepsEveryRepeatedTypeInsteadOfUsingReplacementSemantics() {
        val widgets = listOf(
            HomeWidgetInstance("reading-small", "reading", "small"),
            HomeWidgetInstance("calendar-one", "calendar", "night-large"),
            HomeWidgetInstance("reading-large", "reading", "night-large"),
            HomeWidgetInstance("calendar-two", "calendar", "large"),
        )
        val original = widgets.toList()
        val changed = changeHomeWidgetSuite(widgets, "storybook")!!
        assertEquals(listOf(
            widgets[0].copy(variantId = "story-small"),
            widgets[1].copy(variantId = "story-large"),
            widgets[2].copy(variantId = "story-large"),
            widgets[3].copy(variantId = "story-large"),
        ), changed)
        assertEquals(2, changed.count { it.typeId == "reading" })
        assertEquals(2, changed.count { it.typeId == "calendar" })
        assertEquals(changed, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(changed)))
        assertEquals(original, widgets)
    }

    @Test
    fun anInvalidSuiteOrInstanceRejectsTheWholeMappingWithoutChangingTheInput() {
        val widgets = threeWidgetLayout()
        assertNull(changeHomeWidgetSuite(emptyList(), "night"))
        assertNull(changeHomeWidgetSuite(widgets, "missing"))
        assertNull(changeHomeWidgetSuite(widgets, ""))
        listOf(
            HomeWidgetInstance("bad", "missing", "small"),
            HomeWidgetInstance("bad", "reading", "missing"),
            HomeWidgetInstance("bad", "calendar", "small"),
            HomeWidgetInstance(" ", "calendar", "large"),
            HomeWidgetInstance(widgets.first().id, "calendar", "large"),
        ).forEach { invalid ->
            val input = listOf(widgets[0], invalid, widgets[2])
            val original = input.toList()
            assertNull(changeHomeWidgetSuite(input, "night"))
            assertEquals(original, input)
            assertSame(widgets[0], input.first())
            assertSame(widgets[2], input.last())
        }
        assertEquals(threeWidgetLayout(), widgets)
    }

    @Test
    fun normalSuiteSaveChangesOnlyPlacedWidgetsAndDoesNotCreateAnEditSession() {
        val widgets = listOf(
            HomeWidgetInstance("calendar", "calendar", "large"),
            HomeWidgetInstance("updates", "updates", "small"),
        )
        val confirmed = saveHomeWidgetSuite(HomeUiState(widgets), "storybook")!!
        assertEquals(listOf(
            widgets[0].copy(variantId = "story-large"),
            widgets[1].copy(variantId = "story-small"),
        ), confirmed.widgets)
        assertFalse(confirmed.pendingEdit)
        assertFalse(confirmed.editing)
        assertNull(confirmed.singleEditId)
        assertEquals(listOf("large", "small"), widgets.map { it.variantId })
    }

    @Test
    fun suiteSaveCommitsEarlierMovesAndDeletionsFromBothSingleAndBulkSessions() {
        val saved = threeWidgetLayout()
        listOf(null, saved[0].id).forEach { singleEditId ->
            val started = beginHomeWidgetEditing(HomeUiState(saved), singleEditId)!!
            val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
            val removed = removeHomeWidget(moved, saved[1].id)!!
            val confirmed = saveHomeWidgetSuite(removed, "night")!!
            assertEquals(listOf(
                saved[2].copy(variantId = "night-large"),
                saved[0].copy(variantId = "night-small"),
            ), confirmed.widgets)
            assertFalse(confirmed.pendingEdit)
            assertFalse(confirmed.editing)
            assertNull(confirmed.singleEditId)
            assertSame(saved, removed.widgets)
            assertEquals(listOf(saved[2], saved[0]), removed.draft)
        }
        assertEquals(threeWidgetLayout(), saved)
    }

    @Test
    fun applyingTheSuiteAlreadyInUseStillCommitsTheWholeParentDraft() {
        val saved = threeWidgetLayout()
        listOf(null, saved[0].id).forEach { singleEditId ->
            val started = beginHomeWidgetEditing(HomeUiState(saved), singleEditId)!!
            val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
            val removed = removeHomeWidget(moved, saved[1].id)!!
            val added = addOrReplaceHomeWidget(removed, "calendar", "large", "new-calendar")!!
            assertEquals("basic", homeWidgetCommonStyle(added.displayedWidgets))
            val confirmed = saveHomeWidgetSuite(added, "basic")!!
            assertEquals(HomeUiState(listOf(saved[2], saved[0],
                HomeWidgetInstance("new-calendar", "calendar", "large"))), confirmed)
            assertFalse(confirmed.pendingEdit)
            assertNull(confirmed.singleEditId)
            assertSame(saved, added.widgets)
        }
    }

    @Test
    fun suiteSaveCanCommitAfterTheSingleSessionTargetHasBeenDeleted() {
        val saved = threeWidgetLayout()
        val started = beginHomeWidgetEditing(HomeUiState(saved), saved[0].id)!!
        val removed = removeHomeWidget(started, saved[0].id)!!
        assertEquals(saved[0].id, removed.singleEditId)
        val confirmed = saveHomeWidgetSuite(removed, "night")!!
        assertEquals(listOf(
            saved[1].copy(variantId = "night-small"),
            saved[2].copy(variantId = "night-large"),
        ), confirmed.widgets)
        assertFalse(confirmed.pendingEdit)
        assertNull(confirmed.singleEditId)
        assertSame(saved, removed.widgets)
    }

    @Test
    fun aRejectedSuiteSaveCannotCommitOrDiscardEitherParentSession() {
        val saved = threeWidgetLayout()
        listOf(null, saved[0].id).forEach { singleEditId ->
            val started = beginHomeWidgetEditing(HomeUiState(saved), singleEditId)!!
            val moved = moveHomeWidgetState(started, saved[0].id, saved[2].id)!!
            assertNull(saveHomeWidgetSuite(moved, "missing"))
            assertNull(saveHomeWidgetSuite(moved.copy(layoutReadFailed = true), "night"))
            val invalidDraft = moved.copy(draft = moved.displayedWidgets +
                HomeWidgetInstance("bad", "calendar", "small"))
            assertNull(saveHomeWidgetSuite(invalidDraft, "night"))
            assertTrue(moved.pendingEdit)
            assertEquals(singleEditId, moved.singleEditId)
            assertSame(saved, moved.widgets)
            assertEquals(listOf(saved[1], saved[2], saved[0]), moved.draft)
            assertSame(invalidDraft.draft, invalidDraft.displayedWidgets)
            assertSame(saved, invalidDraft.widgets)
        }
    }

    @Test
    fun anEmptyParentDraftCannotApplyASuiteOrResurrectTheSavedLayout() {
        val saved = threeWidgetLayout()
        listOf(null, saved.first().id).forEach { singleEditId ->
            val empty = HomeUiState(saved, emptyList(), singleEditId = singleEditId)
            assertNull(saveHomeWidgetSuite(empty, "night"))
            assertNull(homeWidgetCommonStyle(empty.displayedWidgets))
            assertTrue(empty.pendingEdit)
            assertEquals(singleEditId, empty.singleEditId)
            assertTrue(empty.displayedWidgets.isEmpty())
            assertSame(saved, empty.widgets)
        }
    }

    private fun threeWidgetLayout(): List<HomeWidgetInstance> = listOf(
        HomeWidgetInstance("default_reading", "reading", "small"),
        HomeWidgetInstance("default_listening", "listening", "small"),
        HomeWidgetInstance("default_updates", "updates", "large"),
    )
}
