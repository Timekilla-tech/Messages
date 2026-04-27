package org.fossify.messages.helpers

import com.google.gson.reflect.TypeToken
import org.fossify.messages.extensions.gson.gson
import org.fossify.messages.models.SavedView
import org.fossify.messages.models.SavedViewConfig
import org.fossify.messages.models.SavedViewType
import java.util.UUID

class SavedViewsStore(private val config: Config) {
    fun getViews(): List<SavedView> {
        val parsed = parseViews(config.savedViewsJson)
        val sanitized = sanitizeViews(parsed)
        persistIfChanged(parsed, sanitized)
        return sanitized
    }

    fun getActiveViewOrMain(): SavedView {
        val views = getViews()
        val lastId = config.lastSavedViewId
        return views.firstOrNull { it.id == lastId } ?: SavedView.mainView().also {
            config.lastSavedViewId = SavedView.MAIN_VIEW_ID
        }
    }

    fun setActiveView(viewId: String) {
        val safeId = if (getViews().any { it.id == viewId }) viewId else SavedView.MAIN_VIEW_ID
        config.lastSavedViewId = safeId
    }

    fun updateActiveViewConfig(configUpdate: SavedViewConfig): SavedView {
        val current = getActiveViewOrMain()
        val updated = current.copy(config = configUpdate)
        upsertView(updated)
        return updated
    }

    fun createView(
        title: String,
        config: SavedViewConfig = SavedViewConfig(),
        iconResName: String = SavedView.DEFAULT_CUSTOM_VIEW_ICON,
    ): SavedView {
        val view = SavedView(
            id = "view_${UUID.randomUUID()}",
            type = SavedViewType.CUSTOM,
            title = title,
            position = getViews().size,
            isEditable = true,
            config = config,
            iconResName = iconResName,
        )
        upsertView(view)
        return getViews().firstOrNull { it.id == view.id } ?: view
    }

    fun renameView(viewId: String, newTitle: String): SavedView? {
        if (viewId == SavedView.MAIN_VIEW_ID) {
            return null
        }

        val existing = getViews().firstOrNull { it.id == viewId } ?: return null
        val updated = existing.copy(title = newTitle)
        upsertView(updated)
        return getViews().firstOrNull { it.id == viewId }
    }

    fun deleteView(viewId: String): Boolean {
        if (viewId == SavedView.MAIN_VIEW_ID) {
            return false
        }

        val remaining = getViews().filterNot { it.id == viewId }
        if (remaining.size == getViews().size) {
            return false
        }

        saveViews(remaining)
        if (config.lastSavedViewId == viewId) {
            config.lastSavedViewId = SavedView.MAIN_VIEW_ID
        }
        return true
    }

    fun upsertView(view: SavedView) {
        val mutableViews = getViews().toMutableList()
        val index = mutableViews.indexOfFirst { it.id == view.id }
        val sanitizedView = sanitizeView(view)
        if (index >= 0) {
            mutableViews[index] = sanitizedView
        } else {
            mutableViews.add(sanitizedView)
        }

        saveViews(mutableViews)
    }

    private fun saveViews(views: List<SavedView>) {
        val sanitized = sanitizeViews(views)
        config.savedViewsJson = gson.toJson(sanitized)
        if (sanitized.none { it.id == config.lastSavedViewId }) {
            config.lastSavedViewId = SavedView.MAIN_VIEW_ID
        }
    }

    private fun parseViews(raw: String): List<SavedView> {
        if (raw.isBlank()) {
            return listOf(SavedView.mainView())
        }

        return try {
            val type = object : TypeToken<List<SavedView>>() {}.type
            gson.fromJson<List<SavedView>>(raw, type) ?: listOf(SavedView.mainView())
        } catch (_: Exception) {
            listOf(SavedView.mainView())
        }
    }

    private fun sanitizeViews(views: List<SavedView>): List<SavedView> {
        val nonMain = views
            .filter { it.id != SavedView.MAIN_VIEW_ID }
            .map { sanitizeView(it).copy(type = SavedViewType.CUSTOM, isEditable = true) }
            .distinctBy { it.id }
            .sortedBy { it.position }

        val main = SavedView.mainView().copy(position = 0)
        return listOf(main) + nonMain.mapIndexed { index, view ->
            view.copy(position = index + 1)
        }
    }

    private fun sanitizeView(view: SavedView): SavedView {
        if (view.id == SavedView.MAIN_VIEW_ID || view.type == SavedViewType.MAIN) {
            return SavedView.mainView()
        }

        return view.copy(
            id = view.id.ifBlank { "view_${System.currentTimeMillis()}" },
            type = SavedViewType.CUSTOM,
            title = view.title.ifBlank { "View" },
            isEditable = true,
            iconResName = view.iconResName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: SavedView.DEFAULT_CUSTOM_VIEW_ICON,
            config = view.config.copy(
                tags = view.config.tags
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        )
    }

    private fun persistIfChanged(parsed: List<SavedView>, sanitized: List<SavedView>) {
        if (parsed != sanitized) {
            config.savedViewsJson = gson.toJson(sanitized)
        }
    }
}

