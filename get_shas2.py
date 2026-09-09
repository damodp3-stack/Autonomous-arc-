import hashlib
import base64

files = {
    "readme": "IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==",
    "main": "cHJpbnRsbigiaGVsbG8iKQ==",
    "icon": "iVBORw0KGgo="
}

for k, v in files.items():
    content = base64.b64decode(v)
    header = f"blob {len(content)}\0".encode('utf-8')
    sha = hashlib.sha1(header + content).hexdigest()
    print(f"{k} -> {sha}")
