import os
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator
import time
import re

languages = {
    "hi": "hi",
    "bn": "bn",
    "ur": "ur",
    "es": "es",
    "pt": "pt",
    "fr": "fr",
    "de": "de",
    "ar": "ar",
    "id": "id",
    "tr": "tr",
    "ru": "ru",
    "ja": "ja",
    "ko": "ko",
    "zh": "zh-CN"
}

base_path = r"p:\One Line A Day\app\src\main\res"
en_file = os.path.join(base_path, "values", "strings.xml")

tree = ET.parse(en_file)
root = tree.getroot()

def preserve_placeholders(text):
    # Find all %1$s, %1$d, etc. and \n
    placeholders = re.findall(r'(%\d+\$[sd]|\n)', text)
    temp_text = text
    for i, p in enumerate(placeholders):
        temp_text = temp_text.replace(p, f" PLACEHOLDER{i} ")
    return temp_text, placeholders

def restore_placeholders(text, placeholders):
    res = text
    for i, p in enumerate(placeholders):
        # translator might change spacing
        res = re.sub(r'\s*PLACEHOLDER' + str(i) + r'\s*', p, res, flags=re.IGNORECASE)
    return res

for lang_code, google_lang in languages.items():
    print(f"Translating to {lang_code}...")
    translator = GoogleTranslator(source='en', target=google_lang)
    
    # Create new XML
    new_root = ET.Element("resources")
    
    for child in root:
        if child.tag == "string":
            name = child.attrib.get('name')
            text = child.text
            if not text:
                text = "".join(child.itertext())
            
            if child.attrib.get('translatable') == 'false':
                new_child = ET.SubElement(new_root, "string", name=name)
                new_child.text = text
                continue
            
            try:
                # Handle placeholders
                temp_text, placeholders = preserve_placeholders(text)
                
                # App name shouldn't usually be translated but user asked for full translation. Let's skip translating URLs or App Name if preferred, 
                # but "One Line A Day" can be translated.
                if "https://" in temp_text:
                    translated_temp = temp_text
                else:
                    translated_temp = translator.translate(temp_text)
                    time.sleep(0.1) # rate limit prevention
                
                final_text = restore_placeholders(translated_temp, placeholders)
                
                # Fix single quotes escaping
                final_text = final_text.replace("'", "\\'")
                # Fix amp
                final_text = final_text.replace("&", "&amp;")
                
                new_child = ET.SubElement(new_root, "string", name=name)
                new_child.text = final_text
            except Exception as e:
                print(f"Error translating {name}: {e}")
                new_child = ET.SubElement(new_root, "string", name=name)
                new_child.text = text # fallback
    
    # Create directory
    val_dir = os.path.join(base_path, f"values-{lang_code}")
    os.makedirs(val_dir, exist_ok=True)
    
    # Save file
    out_tree = ET.ElementTree(new_root)
    # Unescape XML correctly, ET escapes things, but we need standard android format
    xml_str = ET.tostring(new_root, encoding='utf-8').decode('utf-8')
    # ET might escape our already escaped quotes or &amp;
    xml_str = xml_str.replace("&amp;amp;", "&amp;").replace("\\'", "\\'")
    
    with open(os.path.join(val_dir, "strings.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write(xml_str)
        
print("Translation complete!")
