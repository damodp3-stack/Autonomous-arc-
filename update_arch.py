with open('docs/ARCHITECTURE.md', 'r') as f:
    content = f.read()

content = content.replace(
    '- Room Database (Local Persistence) with `ProjectEntity`, `MessageEntity`, and `ProjectFileEntity`.',
    '- Room Database (Local Persistence) with `ProjectEntity`, `MessageEntity`, and `ProjectFileEntity`.\n- Android App-Private Filesystem for real, on-disk file storage (`ProjectFileSystem`).'
)

content = content.replace(
    'ProjectFileRepository (Room DB)',
    'ProjectFileRepository (Room DB + ProjectFileSystem)'
)

content = content.replace(
    'Project\n├── Chats (Implemented)\n├── Metadata (Implemented)\n├── Files (Implemented)',
    'Project\n├── Chats (Implemented)\n├── Metadata (Implemented)\n├── Files (Implemented in Room + Real Filesystem)'
)

with open('docs/ARCHITECTURE.md', 'w') as f:
    f.write(content)
