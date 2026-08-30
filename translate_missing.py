import os
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator
import concurrent.futures
import re

languages = {
    "es": "es", "pt": "pt", "fr": "fr", "de": "de", "ar": "ar",
    "id": "id", "tr": "tr", "ru": "ru", "ja": "ja", "ko": "ko",
    "zh": "zh-CN", "hi": "hi", "bn": "bn", "ur": "ur"
}

missing_strings = {
    "settings_language": "Language",
    "settings_select_language": "Select Language",
    "settings_app_lock": "App Lock",
    "settings_app_lock_subtitle": "Require biometric authentication to open app",
    "settings_export_backup": "Export Backup",
    "settings_export_backup_subtitle": "Save a .zip of your diary to Google Drive",
    "settings_restore_backup": "Restore Backup",
    "settings_restore_backup_subtitle": "Import a .zip backup to restore your data",
    "settings_restore_purchases": "Restore Purchases",
    "settings_restore_purchases_subtitle": "Restore your Premium purchase on this device",
    "gallery": "Gallery",
    "premium_monthly": "Monthly",
    "premium_lifetime": "Lifetime",
    "filter_all": "All",
    "delete_voice_memory_title": "Delete Voice Memory?",
    "delete_voice_memory_message": "Are you sure you want to permanently delete this voice memory from today\\'s entry?",
    "remove": "Remove"
}

base_path = r"p:\One Line A Day\app\src\main\res"

# Update English strings.xml first
en_file = os.path.join(base_path, "values", "strings.xml")
tree = ET.parse(en_file)
root = tree.getroot()

existing_keys = {child.attrib.get('name') for child in root if child.tag == 'string'}
for k, v in missing_strings.items():
    if k not in existing_keys:
        new_child = ET.SubElement(root, "string", name=k)
        new_child.text = v.replace("\\'", "'")

xml_str = ET.tostring(root, encoding='utf-8').decode('utf-8')
xml_str = xml_str.replace("&amp;amp;", "&amp;").replace("'", "\\'")
with open(en_file, "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n')
    f.write(xml_str)

def translate_string(translator, text):
    try:
        if "https://" in text: return text
        translated = translator.translate(text.replace("\\'", "'"))
        return translated.replace("'", "\\'").replace("&", "&amp;")
    except:
        return text

def process_language(lang_code, google_lang):
    print(f"Translating missing for {lang_code}...")
    translator = GoogleTranslator(source='en', target=google_lang)
    
    val_dir = os.path.join(base_path, f"values-{lang_code}")
    target_file = os.path.join(val_dir, "strings.xml")
    if not os.path.exists(target_file):
        return
    
    tree = ET.parse(target_file)
    root = tree.getroot()
    
    existing_keys = {child.attrib.get('name') for child in root if child.tag == 'string'}
    
    for k, v in missing_strings.items():
        if k not in existing_keys:
            translated = translate_string(translator, v)
            new_child = ET.SubElement(root, "string", name=k)
            new_child.text = translated
            
    xml_str = ET.tostring(root, encoding='utf-8').decode('utf-8')
    xml_str = xml_str.replace("&amp;amp;", "&amp;").replace("\\'", "\\'")
    
    with open(target_file, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write(xml_str)
    print(f"Finished {lang_code}")

with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
    futures = []
    for lang_code, google_lang in languages.items():
        futures.append(executor.submit(process_language, lang_code, google_lang))
    concurrent.futures.wait(futures)

print("Translation of missing strings complete!")
