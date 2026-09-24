#!/usr/bin/env python3
"""Exports the in-app wiki manual to Weaverse-Wiki-Manual.md at the repo root.

The in-app wiki (Settings -> Help -> Wiki) is authored as Kotlin data in
app/src/main/java/com/ihy2ln/weaverse/feature/help/WikiContent.kt so it can be
rendered by WikiScreen.kt's small markdown subset (##/### headings, "- "
bullets, | tables |, **bold**, [[Page Title]] links, and {{figure:kind}}
illustration placeholders). This script parses that same Kotlin source and
re-emits every page as one linked Markdown document, so the two can never
drift apart -- run it after editing WikiContent.kt and commit the result.

Usage:
    python tools/export_wiki_manual.py
"""
from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
WIKI_CONTENT_KT = REPO_ROOT / "app/src/main/java/com/ihy2ln/weaverse/feature/help/WikiContent.kt"
OUTPUT_MD = REPO_ROOT / "Weaverse-Wiki-Manual.md"

PAGE_RE = re.compile(
    r'Page\(\s*'
    r'id\s*=\s*"(?P<id>[^"]+)",\s*'
    r'title\s*=\s*"(?P<title>[^"]+)",\s*'
    r'summary\s*=\s*"(?P<summary>[^"]*)",\s*'
    r'markdown\s*=\s*"""(?P<body>.*?)"""\.trimIndent\(\),\s*\),',
    re.S,
)

# Kept in step with WikiFigure's `when (kind)` branches in WikiScreen.kt --
# add an entry here whenever a new figure kind is drawn there.
FIGURE_CAPTIONS = {
    "chatting": "Chatting — a server rail, channel list, and message thread.",
    "rpg": "RPG Adventure — one large scene illustration above the ongoing prose and action bar.",
    "novel": "Novel Write — the manuscript with Plan, Write, Read, Chat, and Review across the top.",
    "brainstorm": "Brainstorm — a threaded AI chat alongside the classic notes board.",
    "prompts": "The prompt dock — drag handle, compact control row, and the template/preset grid.",
}


def kotlin_trim_indent(body: str) -> str:
    """Reproduces Kotlin's String.trimIndent(): strips the common leading
    whitespace margin from every non-blank line, then trims the leading and
    trailing blank line the triple-quoted literal always carries."""
    lines = body.split("\n")
    indents = [len(line) - len(line.lstrip(" ")) for line in lines if line.strip()]
    margin = min(indents) if indents else 0
    dedented = "\n".join(line[margin:] if line.strip() else "" for line in lines)
    return dedented.strip("\n")


def slug(title: str) -> str:
    return re.sub(r"[^a-z0-9\- ]", "", title.lower()).strip().replace(" ", "-")


def convert_links(text: str) -> str:
    """[[Page Title]] -> [Page Title](#page-title), GitHub-anchor style."""
    return re.sub(r"\[\[([^\]]+)\]\]", lambda m: f"[{m.group(1)}](#{slug(m.group(1))})", text)


def bump_headings(text: str) -> str:
    """Each page becomes one `##` section in the combined document, so its
    own `##`/`###` headings drop one level to `###`/`####` to keep a single
    consistent outline."""
    out = []
    for line in text.split("\n"):
        if line.startswith("### "):
            out.append("#### " + line[4:])
        elif line.startswith("## "):
            out.append("### " + line[3:])
        else:
            out.append(line)
    return "\n".join(out)


def convert_figures(text: str) -> str:
    """{{figure:kind}} -> a blockquoted caption; plain Markdown can't render
    WikiFigure's Compose illustration, so this describes it in words."""
    out = []
    for line in text.split("\n"):
        stripped = line.strip()
        if stripped.startswith("{{figure:"):
            kind = stripped.removeprefix("{{figure:").rstrip("}").strip()
            caption = FIGURE_CAPTIONS.get(kind, kind)
            out.append(f"> *[Illustration: {caption}]*")
        else:
            out.append(line)
    return "\n".join(out)


def parse_pages(source: str) -> list[dict]:
    pages = []
    for match in PAGE_RE.finditer(source):
        pages.append(
            {
                "id": match.group("id"),
                "title": match.group("title"),
                "summary": match.group("summary"),
                "body": kotlin_trim_indent(match.group("body")),
            }
        )
    return pages


def render(pages: list[dict]) -> str:
    out = ["# Weaverse Wiki Manual", ""]
    out.append(
        "> This is the full text of the in-app wiki manual (Settings → Help → Wiki), "
        "exported to Markdown for reading outside the app or in a browser. It is "
        "generated from `app/src/main/java/com/ihy2ln/weaverse/feature/help/WikiContent.kt` "
        "by `tools/export_wiki_manual.py` and should be regenerated whenever that file "
        "changes, so the two never drift apart."
    )
    out.append("")
    out.append("## Contents")
    out.append("")
    for page in pages:
        out.append(f"- [{page['title']}](#{slug(page['title'])}) — {page['summary']}")
    out.append("")
    out.append("---")
    out.append("")

    for page in pages:
        out.append(f"## {page['title']}")
        out.append("")
        body = convert_figures(convert_links(bump_headings(page["body"])))
        out.append(body)
        out.append("")
        out.append("---")
        out.append("")

    if out and out[-1] == "---":
        out.pop()
        if out and out[-1] == "":
            out.pop()

    return "\n".join(out) + "\n"


def main() -> None:
    source = WIKI_CONTENT_KT.read_text(encoding="utf-8")
    pages = parse_pages(source)
    if not pages:
        raise SystemExit(f"No Page(...) entries parsed from {WIKI_CONTENT_KT}")
    OUTPUT_MD.write_text(render(pages), encoding="utf-8", newline="\n")
    print(f"Wrote {OUTPUT_MD.relative_to(REPO_ROOT)} from {len(pages)} pages.")


if __name__ == "__main__":
    main()
