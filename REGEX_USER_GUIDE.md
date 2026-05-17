# How to Use Regex Keywords in Categories

## Overview
When creating or editing a category, you can now use regular expressions (regex) for more powerful keyword matching. This is useful when you want to match complex patterns in messages.

## User Guide

### Creating a Category with Standard Keywords (Default)

1. Open **Settings** → **Manage Categories**
2. Click **Add Category** or edit an existing one
3. Enter category name (e.g., "Work")
4. Enter keywords separated by commas: `"deadline, meeting, project"`
5. The **regex toggle button** (icon to the right of keywords field) should **NOT be highlighted**
6. Click **OK**

Messages containing any of these keywords (case-insensitive) will be automatically assigned to this category.

### Creating a Category with Regex Keywords

1. Open **Settings** → **Manage Categories**
2. Click **Add Category** or edit an existing one
3. Enter category name (e.g., "Urgent Messages")
4. Enter a regex pattern: `"^(URGENT|CRITICAL|!{2,})"`
5. Click the **regex toggle button** (icon to the right) - it should now be **highlighted/colored**
6. Click **OK**

If the regex pattern is invalid, you'll see an error message. Fix it and try again.

## Examples

### Standard Keywords
```
urgent, deadline, asap
```
Matches messages containing: "urgent", "deadline", or "asap" (case-insensitive)

### Regex Examples

#### Match phone numbers:
```
\+?1?\d{3}[-.]?\d{3}[-.]?\d{4}
```

#### Match email addresses:
```
[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}
```

#### Match messages starting with priority markers:
```
^(\[P[0-9]\]|!!!|URGENT|CRITICAL)
```

#### Match error/warning messages (case-insensitive):
```
(?i)(error|warning|fail|exception)
```

#### Match times:
```
\b([0-9]{1,2}:[0-9]{2})\s?(am|pm|AM|PM)?\b
```

#### Match money amounts:
```
\$\d+(\.\d{2})?|\d+\s(dollars|bucks)
```

## Tips & Tricks

### Case-Insensitive Regex
Add `(?i)` at the start of your pattern to make it case-insensitive:
```
(?i)urgent|critical|asap
```

### Special Characters in Regex
If your pattern contains special regex characters (`.`, `*`, `+`, etc.), escape them with backslash:
- `.` becomes `\.`
- `*` becomes `\*`
- `+` becomes `\+`

### Test Your Pattern
Before saving, test your regex pattern with sample messages to ensure it works as expected.

### Switching Between Modes
You can easily toggle between standard keywords and regex by clicking the regex button. The button will:
- **Be highlighted/colored**: Regex mode is ON
- **Be normal/gray**: Standard mode is ON (default)

## What Gets Searched?
Both standard and regex keywords search in:
1. **Message body** - The full text of the message
2. **Sender phone number** - The phone number that sent the message

## Auto-Categorization
When you save a category with keywords:
- All existing messages are scanned
- Messages matching your keywords are automatically categorized
- Messages previously in this category that no longer match are uncategorized
- Conversation labels are updated accordingly

## Troubleshooting

### "Invalid regex pattern" Error
This means your regex syntax is incorrect. Common issues:
- Missing closing parenthesis: fix `(urgent|critical` → `(urgent|critical)`
- Unescaped special characters: fix `price$100` → `price\$100`
- Invalid quantifiers: fix `a{1,}` → `a+` or `a{1,}`

### Messages Not Being Categorized
- Verify the keyword/pattern actually matches your message text
- For regex, remember patterns are **case-sensitive** unless you use `(?i)`
- Check that the pattern searches both message body AND sender phone number

### Why Did My Message Category Change?
If you edited a category's keywords, all messages are re-evaluated:
- Messages now matching the new keywords get categorized
- Messages no longer matching the new keywords get uncategorized

## Advanced Regex Features

### Lookahead/Lookbehind
```
(?=.*urgent)(?=.*deadline)  # Must contain BOTH urgent AND deadline
(?<!read) urgent            # urgent NOT after "read"
```

### Word Boundaries
```
\bdeadline\b                # "deadline" as complete word only
```

### Alternation with Grouping
```
(urgent|critical|important).*meeting  # Priority word followed by meeting
```

Need help with regex? Check resources like:
- https://regex101.com (interactive regex tester)
- https://www.regular-expressions.info/ (regex reference)

