$py = Get-Content graphify-out\.graphify_python
& $py -c "
import json
from graphify.detect import detect
from pathlib import Path
result = detect(Path('.'))
print(json.dumps(result, ensure_ascii=False))
" | Out-File -FilePath graphify-out\.graphify_detect.json -Encoding utf8
