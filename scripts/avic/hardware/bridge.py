#!/usr/bin/env python3
"""Temporary ADB-reverse bridge: synthetic receiver source and metadata-only results."""
import http.server
import json
import pathlib
import re
import sys
import urllib.parse
from datetime import datetime, timezone

evidence = pathlib.Path(sys.argv[1])
source = pathlib.Path(__file__).with_name("receiver.sh")

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path != "/receiver.sh":
            self.send_error(404); return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(source.read_bytes())

    def do_POST(self):
        size = int(self.headers.get("Content-Length", "0"))
        if self.path != "/result" or size > 512:
            self.send_error(400); return
        values = urllib.parse.parse_qs(self.rfile.read(size).decode())
        item = {key: values.get(key, [""])[0] for key in ["case", "result", "length"]}
        if not re.fullmatch(r"[a-z_0-9]{1,40}", item["case"]) or item["result"] not in ["PASS", "FAIL"] or not re.fullmatch(r"\d{1,5}", item["length"]):
            self.send_error(400); return
        item["utc"] = datetime.now(timezone.utc).isoformat()
        with evidence.open("a") as stream:
            stream.write(json.dumps(item) + "\n")
        print(json.dumps(item), flush=True)
        self.send_response(200)
        self.end_headers()

http.server.HTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
