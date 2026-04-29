package org.fossify.messages.interfaces
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.fossify.messages.models.Category


@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): List<Category>
    @Query("SELECT * FROM categories WHERE name LIKE :text OR description LIKE :text")
    fun getCategoryWithText(text: String): List<Category>
    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryById(id: Long): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(category: Category): Long

    @Update
    fun update(category: Category)

    @Delete
    fun deleteCategory(category: Category)
}
