"""SQL pack runner: python3 run_sql.py <file.sql>  ->  prints PASS if the query returns the EXPECTED rows."""
import json, os, re, sqlite3, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sqlsetup import SETUP

text = open(sys.argv[1]).read()
expected = json.loads(re.search(r"^-- EXPECTED: (.*)$", text, re.M).group(1))
query = "\n".join(l for l in text.split("\n") if not l.strip().startswith("--")).strip()
if not query:
    print("FAIL: no query written"); sys.exit(0)
db = sqlite3.connect(":memory:"); db.executescript(SETUP)
try:
    got = [list(r) for r in db.execute(query).fetchall()]
except Exception as e:
    print("ERROR:", e); sys.exit(0)
print("PASS" if got == expected else f"FAIL: got {got} expected {expected}")
