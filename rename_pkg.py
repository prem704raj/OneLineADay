import os

d = 'app'
for r, dirs, fs in os.walk(d):
    for f in fs:
        if f.endswith('.kt') or f.endswith('.xml') or f.endswith('.kts') or f.endswith('.pro'):
            path = os.path.join(r, f)
            try:
                with open(path, 'r', encoding='utf-8') as file:
                    content = file.read()
                if 'com.onelineaday.diary' in content:
                    content = content.replace('com.onelineaday.diary', 'com.onelineaday.dailydiary')
                    with open(path, 'w', encoding='utf-8') as file:
                        file.write(content)
            except Exception as e:
                print(f"Error processing {path}: {e}")
