"""Git clean filter: strip Jupyter notebook outputs before they are committed.

Outputs can embed base64 audio (including cloned-voice clips) and absolute local paths. The notebook on disk keeps
its outputs; only the committed copy is stripped. Configured by .gitattributes (`*.ipynb filter=nbstrip`) plus:

    git config filter.nbstrip.clean "python tools/git/nbstrip.py"
    git config filter.nbstrip.smudge cat
"""
import json
import sys

notebook = json.loads(sys.stdin.buffer.read().decode("utf-8"))
for cell in notebook.get("cells", []):
    if cell.get("cell_type") == "code":
        cell["outputs"] = []
        cell["execution_count"] = None
    cell.get("metadata", {}).pop("execution", None)
notebook.get("metadata", {}).pop("widgets", None)
sys.stdout.buffer.write((json.dumps(notebook, indent=1, ensure_ascii=False) + "\n").encode("utf-8"))
