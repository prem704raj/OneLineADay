import os

files = [
    r"p:\One Line A Day\app\src\main\java\com\onelineaday\dailydiary\ui\components\AudioAttachment.kt",
    r"p:\One Line A Day\app\src\main\java\com\onelineaday\dailydiary\ui\components\PhotoAttachment.kt",
    r"p:\One Line A Day\app\src\main\java\com\onelineaday\dailydiary\ui\screens\EntryDetailScreen.kt",
    r"p:\One Line A Day\app\src\main\java\com\onelineaday\dailydiary\ui\screens\MainNavigation.kt"
]

imports = """
import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
"""

for fpath in files:
    if not os.path.exists(fpath):
        continue
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    
    if "import androidx.compose.ui.res.stringResource" not in content:
        # insert after the first import or package declaration
        if "import " in content:
            idx = content.find("import ")
            content = content[:idx] + imports.strip() + "\n" + content[idx:]
            with open(fpath, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Fixed imports in {fpath}")
