import json

transcript_path = r'C:\Users\cheth\.gemini\antigravity-ide\brain\81848ef6-7565-4e2e-bd65-d2403168e371\.system_generated\logs\transcript_full.jsonl'

with open(transcript_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

out = open('extracted.txt', 'w', encoding='utf-8')

for line in lines:
    try:
        data = json.loads(line)
        if data.get('source') == 'MODEL' and data.get('type') == 'PLANNER_RESPONSE':
            tool_calls = data.get('tool_calls', [])
            for tc in tool_calls:
                name = tc.get('name')
                args = tc.get('args', {})
                if name in ['multi_replace_file_content', 'replace_file_content']:
                    target = args.get('TargetFile', '')
                    if 'StickerEditorScreen.kt' in target:
                        out.write(f"--- STEP {data.get('step_index')} ---\n")
                        if name == 'multi_replace_file_content':
                            chunks_str = args.get('ReplacementChunks', '[]')
                            if isinstance(chunks_str, str):
                                chunks = json.loads(chunks_str)
                            else:
                                chunks = chunks_str
                            for i, chunk in enumerate(chunks):
                                out.write(f"Chunk {i}:\n")
                                out.write("Target:\n" + chunk.get('TargetContent', '') + "\n")
                                out.write("Replace:\n" + chunk.get('ReplacementContent', '') + "\n")
                        elif name == 'replace_file_content':
                            out.write("Target:\n" + args.get('TargetContent', '') + "\n")
                            out.write("Replace:\n" + args.get('ReplacementContent', '') + "\n")
    except Exception as e:
        out.write("ERROR: " + str(e) + "\n")

out.close()
