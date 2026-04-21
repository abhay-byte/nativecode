
import urllib.request
import zipfile
import io
import os

URLS = {
    "theme": "https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/theme.zip",
    "icons": "https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/icons.zip",
    "cursor": "https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/cursor.zip",
    "font": "https://github.com/abhay-byte/fluxlinux/releases/download/debian-v1/font.zip"
}

def check_zip(name, url):
    print(f"Checking {name} from {url}...")
    try:
        with urllib.request.urlopen(url) as r:
            data = r.read()
            z = zipfile.ZipFile(io.BytesIO(data))
            files = z.namelist()
            if not files:
                print(f"  [!] Empty zip: {name}")
                return
            
            # Get first item
            first = files[0]
            # Get root dir
            root = first.split('/')[0]
            print(f"  [OK] First file: {first}")
            print(f"  [OK] Detected Root: {root}")
            
    except Exception as e:
        print(f"  [ERROR] {e}")

for k, v in URLS.items():
    check_zip(k, v)
