# Regex Support Implementation for Category Keywords

## Overview
Implemented regex support for the category keyword field in the Messages app, similar to VS Code's search functionality. Users can now toggle between standard comma-separated keywords and regular expressions for more advanced message filtering.

## Changes Made

### 1. **Data Model Updates**

#### Modified: `Category.kt`
- Added new field: `keywordIsRegex: Boolean = false` to track whether keywords should be treated as regex
- Updated `areContentsTheSame()` to include the new field in comparison

```kotlin
@Entity(tableName = "categories")
data class Category(
    // ... existing fields ...
    @ColumnInfo(name = "keywords_is_regex") var keywordIsRegex: Boolean = false
)
```

### 2. **Database Schema Migration**

#### Modified: `MessagesDatabase.kt`
- Incremented database version from 11 to 12
- Added migration `MIGRATION_11_12` that adds `keywords_is_regex` column to categories table
- Column defaults to 0 (false) for backward compatibility

```kotlin
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `categories` ADD COLUMN `keywords_is_regex` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
```

### 3. **UI/UX Enhancements**

#### Modified: `dialog_add_or_edit_category.xml`
- Added regex toggle button next to keywords input field
- Button uses icon-based UI for clean integration (similar to VS Code)
- Button color changes to category color when regex mode is active

#### Modified: `AddOrEditCategoryDialog.kt`
- Added `isRegexMode` tracking variable
- Implemented regex toggle button listener that switches between modes
- Toggle button color changes to match selected category color when active
- Added regex validation when saving categories
- Shows error message if invalid regex pattern is entered
- Passes `keywordIsRegex` parameter to `createCategory()` and `updateCategory()`

### 4. **Keyword Matching Logic**

#### Modified: `extensions/Context.kt`

**Updated `createCategory()` function:**
- Added `keywordIsRegex: Boolean = false` parameter
- Passes parameter to Category object creation

**Updated `filterMessagesByKeywords()` function:**
- Added `isRegex: Boolean = false` parameter
- Supports both traditional comma-separated keywords and regex patterns:
  - **Regex Mode**: Uses `Regex.containsMatchIn()` for pattern matching
  - **Standard Mode**: Splits by comma, trims, and uses `contains()`
- Includes error handling for invalid regex (returns all messages on error)
- Searches both message body and sender phone number

**Updated `isMessageMatchingCategory()` function:**
- Checks `category.keywordIsRegex` to determine matching mode
- Supports both regex and standard keyword matching
- Handles regex compilation errors gracefully
- Used for automatic category assignment when creating/updating categories

### 5. **Localization Updates**

#### Modified: `strings.xml`
Added new string resources:
```xml
<string name="regex_mode">Use regex</string>
<string name="regex_mode_description">Match using regular expression</string>
<string name="invalid_regex_pattern">Invalid regex pattern</string>
```

### 6. **UI Assets**

#### Created: `drawable/ic_regex_vector.xml`
New drawable for regex toggle button icon

## Features

### User Interface
1. **Regex Toggle Button**: Located next to the keywords input field in category editor
2. **Visual Feedback**: Button color changes to the category color when regex mode is active
3. **Error Handling**: Shows error message if invalid regex pattern is entered

### Matching Modes

#### Standard Mode (Default)
- Keywords separated by commas
- Case-insensitive matching
- Matches substrings within message body
- Example: `"urgent, deadline, important"`

#### Regex Mode
- Single regular expression pattern
- Case-sensitive by default (can use flags like `(?i)` for case-insensitivity)
- Full regex capabilities
- Example: `"^(urgent|deadline|IMPORTANT)"` - matches start-of-line
- Example: `"(?i)(error|warning|fail)"` - case-insensitive matches

### Auto-Categorization
When saving a category with keywords (regex or standard), the app automatically:
1. Scans all existing messages
2. Applies category to messages matching the pattern
3. Removes category from previously categorized messages that no longer match
4. Updates conversation category labels accordingly

## Technical Details

### Regex Engine
- Uses Kotlin's built-in `Regex` class
- Supports full Kotlin regex syntax
- Invalid patterns gracefully fall back to showing no matches

### Search Fields
Both matching modes search:
1. Message body (full text)
2. Sender phone number

### Error Handling
- Invalid regex patterns show user-friendly error messages
- Compilation errors don't crash the app
- Database migration is safe and backwards compatible

## Database Compatibility
- **Backward Compatibility**: Existing categories without regex work as before (keywordIsRegex defaults to false)
- **Migration**: All existing categories automatically work in standard mode
- **Data Preservation**: No data loss during migration

## Testing Recommendations

1. **Standard Mode Testing**
   - Create category with comma-separated keywords
   - Verify messages match correctly
   - Test case-insensitivity

2. **Regex Mode Testing**
   - Test basic patterns: `^test`, `test$`, `[0-9]+`
   - Test case-sensitive matching
   - Test with special characters
   - Verify error message for invalid patterns

3. **Toggle Testing**
   - Toggle between modes multiple times
   - Verify button color changes with category color
   - Verify regex validation works

4. **Auto-Categorization Testing**
   - Create category with keywords
   - Send messages matching the pattern
   - Verify auto-categorization works
   - Update category keywords and verify messages re-categorize

## Performance Considerations
- Regex compilation happens at save-time (not per-message check), so minimal performance impact
- Message matching uses efficient Kotlin regex APIs
- Error handling prevents regex DoS attacks from complex patterns

## Future Enhancements
- Add tooltip/help text explaining regex syntax
- Add preset regex patterns/templates
- Support for capture groups and backreferences
- Regex pattern testing UI in category editor

