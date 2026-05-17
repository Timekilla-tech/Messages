package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "color") var color: Int,
    @ColumnInfo(name = "icon") var icon: String = "",
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "is_default") var isDefault: Boolean = false,
    @ColumnInfo(name = "keywords") var keywords: String = "",
    @ColumnInfo(name = "keywords_is_regex") var keywordIsRegex: Boolean = false
) {
    companion object {
        fun areItemsTheSame(old: Category, new: Category): Boolean {
            return old.id == new.id
        }

        fun areContentsTheSame(old: Category, new: Category): Boolean {
            return old.name == new.name &&
                old.color == new.color &&
                old.icon == new.icon &&
                old.description == new.description &&
                old.isDefault == new.isDefault &&
                old.keywords == new.keywords &&
                old.keywordIsRegex == new.keywordIsRegex
        }
    }
}
