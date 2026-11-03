"""Android entry point: run the upstream local web console in app-private storage."""
from pathlib import Path
import shutil


def start(data_dir: str) -> None:
    root = Path(data_dir)
    root.mkdir(parents=True, exist_ok=True)
    config = root / "config.yaml"
    if not config.exists():
        # The Android host copies config.example.yaml into filesDir before startup.
        raise FileNotFoundError(f"Missing config.yaml: {config}")
    from miyouqian.service.web import serve
    serve(config, "127.0.0.1", 5890)
