import os
import re
import glob

base_path = r"p:\One Line A Day\app\src\main\res"
xml_files = glob.glob(os.path.join(base_path, "values-*", "strings.xml"))

def fix_content(content):
    # Android strings.xml requires valid escaping. 
    # Invalid escape sequences like \ followed by something other than n, t, r, ', ", @ etc.
    # A simple fix is to replace unescaped \ with \\ unless it is \n or \'.
    # Actually, the error might be an actual \u that is malformed.
    # Let's just remove all backslashes that are not followed by 'n', ''', '"', '@'
    
    # We replace \\ with something safe first, but there shouldn't be valid \\ anyway.
    fixed = re.sub(r'\\([^n\'"@])', r' \1', content)
    # Also fix potential \ at the end of string
    fixed = re.sub(r'\\$', ' ', fixed)
    return fixed

for f in xml_files:
    if "values-night" in f: continue
    try:
        with open(f, "r", encoding="utf-8") as file:
            content = file.read()
            
        new_content = fix_content(content)
        
        with open(f, "w", encoding="utf-8") as file:
            file.write(new_content)
            
        print(f"Fixed {f}")
    except Exception as e:
        print(f"Error fixing {f}: {e}")
