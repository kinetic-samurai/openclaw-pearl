# Copyright 2026 The ODML Authors / OpenClaw Pearl Team
# Optimized LiteRT-LM server for F1-speed performance.

import collections.abc
import datetime
import http.server
import json
import socketserver
import threading
import traceback
from typing import Any, Optional

import click
import litert_lm
from litert_lm_cli import model

_engine_lock = threading.Lock()
_current_engine: Optional[litert_lm.Engine] = None
_current_model_id: Optional[str] = None

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    """Handle requests in separate threads."""
    daemon_threads = True

def get_engine(model_id: str, backend: str = "cpu", max_tokens: int = 8192) -> litert_lm.Engine:
    global _current_engine, _current_model_id
    with _engine_lock:
        if _current_model_id == model_id and _current_engine is not None:
            # Note: We don't re-init if max_tokens changed for the same model in this simple cache
            return _current_engine

        if _current_engine is not None:
            _current_engine.__exit__(None, None, None)
            _current_engine = None

        m = model.Model.from_model_id(model_id)
        if not m.exists():
            raise FileNotFoundError(f"Model {model_id} not found")

        click.echo(click.style(f"🚀 Initializing TURBO engine ({backend}, max_tokens={max_tokens}): {m.model_path}", fg="green", bold=True))
        
        be = litert_lm.Backend.GPU if backend.lower() == "gpu" else litert_lm.Backend.CPU
        new_engine = litert_lm.Engine(m.model_path, backend=be, max_num_tokens=max_tokens)
        new_engine.__enter__()

        _current_engine = new_engine
        _current_model_id = model_id
        return _current_engine

class TurboOpenAIHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1" # Enable Keep-Alive

    def do_GET(self):
        if self.path == "/health" or self.path == "/":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "service": "turbo-litert-lm"}).encode("utf-8"))
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path != "/v1/responses":
            self.send_error(404)
            return

        content_length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(content_length))
        
        model_id = body.get("model")
        prompt = body.get("input")
        stream = body.get("stream", False)
        max_tokens_limit = body.get("max_tokens_limit", getattr(self.server, 'max_tokens_config', 8192))

        try:
            engine = get_engine(model_id, backend=getattr(self.server, 'backend', 'cpu'), max_tokens=max_tokens_limit)
        except Exception as e:
            self.send_error(500, str(e))
            return

        # Inference must be serialized for the single engine instance
        with _engine_lock:
            try:
                with engine.create_conversation(messages=[], automatic_tool_calling=False) as conv:
                    if not stream:
                        response = conv.send_message(prompt)
                        text = "".join(i.get("text", "") for i in response.get("content", []) if i.get("type") == "text")
                        
                        resp_body = {
                            "id": f"resp_{datetime.datetime.now().timestamp()}",
                            "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": text}]}]
                        }
                        
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(json.dumps(resp_body))))
                        self.end_headers()
                        self.wfile.write(json.dumps(resp_body).encode("utf-8"))
                    else:
                        self.send_response(200)
                        self.send_header("Content-Type", "text/event-stream")
                        self.send_header("Cache-Control", "no-cache")
                        self.end_headers()

                        for chunk in conv.send_message_async(prompt):
                            text = "".join(i.get("text", "") for i in chunk.get("content", []) if i.get("type") == "text")
                            if text:
                                data = json.dumps({"delta": {"text": text}})
                                self.wfile.write(f"data: {data}\n\n".encode("utf-8"))
                                self.wfile.flush()
                        
                        self.wfile.write(b"data: [DONE]\n\n")
                        self.wfile.flush()
            except Exception as e:
                click.echo(click.style(f"Inference Error: {e}", fg="red"))

@click.command()
@click.option("--host", default="localhost")
@click.option("--port", default=9379, type=int)
@click.option("--backend", default="cpu", type=click.Choice(["cpu", "gpu"]))
@click.option("--max-tokens", default=8192, type=int, help="Max context window size")
def main(host, port, backend, max_tokens):
    server_address = (host, port)
    httpd = ThreadedHTTPServer(server_address, TurboOpenAIHandler)
    httpd.backend = backend
    httpd.max_tokens_config = max_tokens
    click.echo(click.style(f"🏎️ Turbo LiteRT-LM Server starting on {host}:{port}...", fg="yellow", bold=True))
    httpd.serve_forever()

if __name__ == "__main__":
    main()
