package org.fossify.messages.models

enum class SavedViewType {
    MAIN,
    CUSTOM,
}

data class SavedViewConfig(
    val folderId: Long? = null,
    val tags: Set<String> = emptySet(),
    val showArchived: Boolean = false,
    val unreadOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val matchAllTags: Boolean = false,
    val color: Int? = null,
)

data class SavedView(
    val id: String,
    val type: SavedViewType,
    val title: String,
    val position: Int,
    val isEditable: Boolean,
    val config: SavedViewConfig,
    val iconResName: String? = null,
) {
    companion object {
        const val MAIN_VIEW_ID = "main"
        const val MAIN_VIEW_ICON = "ic_home_vector"
        const val DEFAULT_CUSTOM_VIEW_ICON = "ic_filter_list_vector"

        fun mainView() = SavedView(
            id = MAIN_VIEW_ID,
            type = SavedViewType.MAIN,
            title = "All messages",
            position = 0,
            isEditable = false,
            config = SavedViewConfig(),
            iconResName = MAIN_VIEW_ICON,
        )
    }
}

