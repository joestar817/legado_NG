package io.legado.app.ui.main.home

/** A single target and bulk layout editing share one pending layout, but different tool visibility. */
internal fun beginHomeWidgetEditing(state: HomeUiState, singleEditId: String? = null): HomeUiState? {
    if (state.layoutReadFailed) return null
    if (singleEditId != null) {
        if (state.displayedWidgets.none { it.id == singleEditId }) return null
        if (state.editing) return state
    }
    return state.copy(draft = state.draft ?: state.widgets, singleEditId = singleEditId)
}

internal fun cancelHomeWidgetEditing(state: HomeUiState): HomeUiState =
    state.copy(draft = null, singleEditId = null)

/** The drawer's Save commits the whole pending edit, including earlier moves and removals. */
internal fun saveHomeWidgetVariant(state: HomeUiState, id: String, variantId: String): HomeUiState? {
    val updated = editHomeWidget(state, id, variantId) ?: return null
    return HomeUiState(widgets = updated.displayedWidgets)
}

/** A suite is in use only when every placed instance has the same valid style. */
internal fun homeWidgetCommonStyle(widgets: List<HomeWidgetInstance>): String? {
    if (widgets.isEmpty()) return null
    var commonStyle: String? = null
    val ids = hashSetOf<String>()
    widgets.forEach { widget ->
        if (widget.id.isBlank() || !ids.add(widget.id)) return null
        val styleId = HomeWidgetCatalog.variant(widget)?.styleId ?: return null
        if (commonStyle != null && commonStyle != styleId) return null
        commonStyle = styleId
    }
    return commonStyle
}

/** Validate the whole suite before returning a layout; instances and their sizes stay intact. */
internal fun changeHomeWidgetSuite(
    widgets: List<HomeWidgetInstance>,
    styleId: String,
): List<HomeWidgetInstance>? {
    if (widgets.isEmpty()) return null
    if (HomeWidgetCatalog.types.none { type -> type.variants.any { it.styleId == styleId } }) return null
    val ids = hashSetOf<String>()
    return widgets.map { widget ->
        if (widget.id.isBlank() || !ids.add(widget.id)) return null
        val type = HomeWidgetCatalog.type(widget.typeId) ?: return null
        val current = type.variants.firstOrNull { it.id == widget.variantId } ?: return null
        val target = type.variants.firstOrNull { it.styleId == styleId && it.size == current.size }
            ?: return null
        widget.copy(variantId = target.id)
    }
}

/** Applying a suite also commits all earlier changes in the single or bulk parent draft. */
internal fun saveHomeWidgetSuite(state: HomeUiState, styleId: String): HomeUiState? {
    if (state.layoutReadFailed) return null
    val widgets = changeHomeWidgetSuite(state.displayedWidgets, styleId) ?: return null
    return HomeUiState(widgets = widgets)
}

/** Each type is added once; later choices replace it without committing an existing bulk draft. */
internal fun addOrReplaceHomeWidget(
    state: HomeUiState,
    typeId: String,
    variantId: String,
    newId: String,
): HomeUiState? {
    if (state.layoutReadFailed) return null
    if (HomeWidgetCatalog.type(typeId)?.variants?.none { it.id == variantId } != false) return null
    val widgets = state.displayedWidgets
    if (widgets.none { it.typeId == typeId } &&
        (newId.isBlank() || widgets.any { it.id == newId })) return null
    return replaceHomeWidgetLayout(state, homeWidgetChoiceLayout(widgets, HomeWidgetInstance(newId, typeId, variantId)))
}

/** Used for the hypothetical preview and explicit confirmation, never while loading stored layouts. */
internal fun homeWidgetChoiceLayout(
    widgets: List<HomeWidgetInstance>,
    choice: HomeWidgetInstance,
): List<HomeWidgetInstance> {
    val existing = widgets.firstOrNull { it.typeId == choice.typeId } ?: return widgets + choice
    return widgets.mapNotNull { widget ->
        when {
            widget.id == existing.id -> widget.copy(variantId = choice.variantId)
            widget.typeId == choice.typeId -> null
            else -> widget
        }
    }
}

internal fun editHomeWidget(state: HomeUiState, id: String, variantId: String): HomeUiState? {
    if (state.layoutReadFailed) return null
    val updated = changeHomeWidgetVariant(state.displayedWidgets, id, variantId) ?: return null
    return replaceHomeWidgetLayout(state, updated)
}

internal fun removeHomeWidget(state: HomeUiState, id: String): HomeUiState? {
    if (state.layoutReadFailed || state.displayedWidgets.none { it.id == id }) return null
    return replaceHomeWidgetLayout(state, state.displayedWidgets.filterNot { it.id == id })
}

internal fun moveHomeWidgetState(state: HomeUiState, fromId: String, toId: String): HomeUiState? {
    if (state.layoutReadFailed) return null
    val widgets = state.displayedWidgets
    val moved = moveHomeWidget(widgets, fromId, toId)
    if (moved === widgets) return null
    return replaceHomeWidgetLayout(state, moved)
}

private fun replaceHomeWidgetLayout(state: HomeUiState, widgets: List<HomeWidgetInstance>): HomeUiState =
    if (state.pendingEdit) state.copy(draft = widgets) else state.copy(widgets = widgets)
