"""Apply the project's four-space Java style; this command does not run gameplay checks."""

import argparse
import subprocess
from pathlib import Path


def main():
    """Format owned Java source with a supplied google-java-format 2.0.0 all-deps JAR."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("formatter", type=Path, help="Path to google-java-format 2.0.0 all-deps JAR")
    parser.add_argument("--java", default="java", help="Java executable compatible with the formatter")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    files = sorted(str(p) for folder in (root / "src", root / "tools") for p in folder.rglob("*.java"))
    subprocess.run([args.java, "-jar", str(args.formatter.resolve()), "--aosp", "--replace", *files], check=True)


if __name__ == "__main__":
    main()
