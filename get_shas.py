import hashlib
import base64

files = {
    "sha1": "IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==",
    "sha3": "ZnVuIG1haW4oKSB7IHByaW50bG4oIkhlbGxvIikgfQ==",
    "sha4": "iVBORw0KGgo="
}

for k, v in files.items():
    content = base64.b64decode(v)
    header = f"blob {len(content)}\0".encode('utf-8')
    sha = hashlib.sha1(header + content).hexdigest()
    print(f"{k} -> {sha}")
