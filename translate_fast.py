import os
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator
import concurrent.futures
import re

languages = {
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
    placeholders = re.findall(r'(%\d+\$[sd]|\n)', text)
    temp_text = text
    for i, p in enumerate(placeholders):
        temp_text = temp_text.replace(p, f" PLACEHOLDER{i} ")
    return temp_text, placeholders

def restore_placeholders(text, placeholders):
    res = text
    for i, p in enumerate(placeholders):
        res = re.sub(r'\s*PLACEHOLDER' + str(i) + r'\s*', p, res, flags=re.IGNORECASE)
    return res

def translate_string(translator, text):
    try:
        temp_text, placeholders = preserve_placeholders(text)
        if "https://" in temp_text:
            return text
        translated_temp = translator.translate(temp_text)
        final_text = restore_placeholders(translated_temp, placeholders)
        final_text = final_text.replace("'", "\\'").replace("&", "&amp;")
        return final_text
    except Exception as e:
        return text

def process_language(lang_code, google_lang):
    print(f"Translating to {lang_code}...")
    translator = GoogleTranslator(source='en', target=google_lang)
    new_root = ET.Element("resources")
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        future_to_element = {}
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
                
                future = executor.submit(translate_string, translator, text)
                future_to_element[future] = (name, text)
        
        for future in concurrent.futures.as_completed(future_to_element):
            name, text = future_to_element[future]
            final_text = future.result()
            new_child = ET.SubElement(new_root, "string", name=name)
            new_child.text = final_text

    val_dir = os.path.join(base_path, f"values-{lang_code}")
    os.makedirs(val_dir, exist_ok=True)
    xml_str = ET.tostring(new_root, encoding='utf-8').decode('utf-8')
    xml_str = xml_str.replace("&amp;amp;", "&amp;").replace("\\'", "\\'")
    
    with open(os.path.join(val_dir, "strings.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write(xml_str)
    print(f"Finished {lang_code}")

# Translate languages in parallel
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
    futures = []
    for lang_code, google_lang in languages.items():
        futures.append(executor.submit(process_language, lang_code, google_lang))
    concurrent.futures.wait(futures)

print("Translation complete!")
