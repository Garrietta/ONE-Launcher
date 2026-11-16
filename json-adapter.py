#!/usr/bin/env python3
"""json-adapter.py -- Static JSON adapter for the Portside/ONE remote folder.

Reads a category site -- an index page whose sidebar links to category
subpages, each grouping files into HTML tables -- and emits a flat JSON
array in the exact schema the app's remote-folder browser already
understands:

    [{"name": "<title>", "url": "<direct link>", "category": "<section>"}, ...]

The app reads `name` and `url` (an optional numeric `size` is also
accepted); `category` is extra context the app ignores.

This script runs OUTSIDE the app, on your own server or in CI. The app
itself is never changed and stays a generic browser. Run it on a
schedule (cron, GitHub Actions) or by hand, host the single output
.json file (GitHub Pages, Cloudflare Workers, or your local server),
and point a Remote folder in the app at that JSON URL.

Usage:
    python3 json-adapter.py --base-url http://localhost:8080
    python3 json-adapter.py --base-url http://localhost:8080 --output remote.json

    --base-url can also come from the ADAPTER_BASE_URL environment
    variable. --output defaults to remote.json in the current directory.

Parsing (per the site's own layout):
  1. Route discovery, crawl depth 1: the index page's sidebar
     navigation (<nav> / <aside>) is harvested for internal category
     URLs; external domains and anchor links (#) are filtered out. The
     root page itself is never parsed for files.
  2. Main-content scoping: all DOM queries are restricted to <main>;
     <nav>, <aside>, <header> and <footer> are ignored, so sidebar
     links never pollute the dataset. Files are read row by row
     (<tbody> <tr>), never from global <a> tags.
  3. Row & column parsing: each table's header row (<thead> <th>) is
     inspected. The title is the first cell's inner text (trimmed).
     The direct-download column is the last column whose header does
     not mention torrent / P2P / magnet. Rows with no link in that
     column are skipped; .torrent and magnet: links are excluded.
  4. Output: each record is tagged with its section (the nearest
     preceding <h2>/<h3> heading, falling back to the sidebar category
     name), deduplicated by URL, and sorted by name.

Standard library only -- no pip installs. Python 3.8+.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser

USER_AGENT = "portside-json-adapter/1.0 (+https://github.com/Garrietta/ONE-Launcher)"
FETCH_TIMEOUT_S = 30

# A header or link that marks a torrent / P2P column or link.
TORRENT_WORDS = ("torrent", "p2p", "magnet")

# Elements whose subtree is never file content (navigation chrome).
SKIP_TAGS = ("nav", "aside", "header", "footer")


def is_torrent_text(text):
    lowered = text.lower()
    return any(word in lowered for word in TORRENT_WORDS)


def is_torrent_href(href):
    lowered = href.strip().lower()
    return ".torrent" in lowered or lowered.startswith("magnet:")


def is_http_url(url):
    return url.startswith("http://") or url.startswith("https://")


# ---------------------------------------------------------------------------
# Fetching
# ---------------------------------------------------------------------------

def fetch(url):
    """GET a URL, return its decoded body text, or None on any failure."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=FETCH_TIMEOUT_S) as response:
            charset = response.headers.get_content_charset() or "utf-8"
            return response.read().decode(charset, errors="replace")
    except Exception as exc:  # HTTPError, URLError, timeout, decode errors
        print(f"warning: could not fetch {url}: {exc}", file=sys.stderr)
        return None


# ---------------------------------------------------------------------------
# Route discovery: harvest category URLs from the sidebar navigation
# ---------------------------------------------------------------------------

class NavParser(HTMLParser):
    """Collects (href, link text) for links inside <nav> / <aside>."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.nav_depth = 0
        self.current_href = None
        self.current_text = []
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag in ("nav", "aside"):
            self.nav_depth += 1
        if self.nav_depth > 0 and tag == "a":
            href = dict(attrs).get("href")
            if href:
                self.current_href = href
                self.current_text = []

    def handle_endtag(self, tag):
        if tag in ("nav", "aside") and self.nav_depth > 0:
            self.nav_depth -= 1
        if tag == "a" and self.current_href is not None:
            text = "".join(self.current_text).strip()
            self.links.append((self.current_href, text))
            self.current_href = None
            self.current_text = []

    def handle_data(self, data):
        if self.current_href is not None:
            self.current_text.append(data)


def discover_category_urls(base_url, index_html):
    """Internal category URLs from the index sidebar, in page order."""
    parser = NavParser()
    parser.feed(index_html)
    base = urllib.parse.urlparse(base_url)
    seen = set()
    urls = []
    for href, text in parser.links:
        href = href.strip()
        if not href or href.startswith("#"):
            continue
        if href.lower().startswith(("javascript:", "mailto:")):
            continue
        resolved = urllib.parse.urljoin(base_url, href)
        parsed = urllib.parse.urlparse(resolved)
        if parsed.scheme not in ("http", "https"):
            continue
        if parsed.netloc.lower() != base.netloc.lower():
            continue  # external domain
        resolved = urllib.parse.urldefrag(resolved).url
        if resolved.rstrip("/") == base_url.rstrip("/"):
            continue  # the index page itself
        if resolved in seen:
            continue
        seen.add(resolved)
        urls.append((resolved, text))
    return urls


# ---------------------------------------------------------------------------
# Category page parsing: tables inside <main>, row by row
# ---------------------------------------------------------------------------

class _Cell:
    __slots__ = ("text", "links")

    def __init__(self):
        self.text = ""
        self.links = []


class CategoryParser(HTMLParser):
    """Parses one category page into {name, url, category} records."""

    def __init__(self, page_url, fallback_category=""):
        super().__init__(convert_charrefs=True)
        self.page_url = page_url
        self.fallback_category = fallback_category
        self.records = []

        self.main_depth = 0
        self.skip_depth = 0
        self.in_heading = None  # "h2" or "h3" while accumulating
        self.heading_text = []
        self.last_heading = ""
        self.table_section = ""

        self.in_table = False
        self.in_thead = False
        self.current_row = None  # list of _Cell
        self.current_row_is_header = False
        self.current_cell = None
        self.current_cell_is_header = False
        self.table_headers = []  # list of header texts (from the header row)
        self.table_rows = []  # list of (cells, is_header_row)

    # -- tag tracking ------------------------------------------------------

    def handle_starttag(self, tag, attrs):
        if tag in SKIP_TAGS:
            self.skip_depth += 1
            return
        if self.skip_depth > 0:
            return
        if tag == "main":
            self.main_depth += 1
            return
        if self.main_depth == 0:
            return
        if tag in ("h2", "h3"):
            self.in_heading = tag
            self.heading_text = []
        elif tag == "table":
            self.in_table = True
            self.table_section = self.last_heading
            self.table_headers = []
            self.table_rows = []
        elif tag == "thead" and self.in_table:
            self.in_thead = True
        elif tag == "tr" and self.in_table:
            self.current_row = []
            self.current_row_is_header = False
        elif tag in ("th", "td") and self.current_row is not None:
            self.current_cell = _Cell()
            self.current_cell_is_header = tag == "th"
            if tag == "th":
                self.current_row_is_header = True
        elif tag == "a" and self.current_cell is not None:
            href = dict(attrs).get("href")
            if href:
                self.current_cell.links.append(href)

    def handle_endtag(self, tag):
        if tag in SKIP_TAGS and self.skip_depth > 0:
            self.skip_depth -= 1
            return
        if self.skip_depth > 0:
            return
        if tag == "main" and self.main_depth > 0:
            self.main_depth -= 1
            return
        if self.main_depth == 0:
            return
        if tag in ("h2", "h3") and self.in_heading == tag:
            text = "".join(self.heading_text).strip()
            if text:
                self.last_heading = text
            self.in_heading = None
            self.heading_text = []
        elif tag == "thead":
            self.in_thead = False
        elif tag in ("th", "td") and self.current_cell is not None:
            self.current_row.append(self.current_cell)
            self.current_cell = None
        elif tag == "tr" and self.current_row is not None:
            if self.current_row_is_header and not self.table_headers:
                self.table_headers = [
                    cell.text.strip() for cell in self.current_row
                ]
            else:
                self.table_rows.append(
                    (self.current_row, self.current_row_is_header)
                )
            self.current_row = None
        elif tag == "table" and self.in_table:
            self.in_table = False
            self._finish_table()

    def handle_data(self, data):
        if self.skip_depth > 0 or self.main_depth == 0:
            return
        if self.in_heading is not None:
            self.heading_text.append(data)
        elif self.current_cell is not None:
            self.current_cell.text += data

    # -- table processing --------------------------------------------------

    def _direct_column(self, num_cols):
        """Index of the direct-download column, or None."""
        if num_cols < 2:
            return None
        # Candidate columns are everything after the title column (0).
        # The direct column is the LAST one whose header does not
        # mention torrent / P2P / magnet. With no headers at all, it
        # is simply the last column.
        for index in range(num_cols - 1, 0, -1):
            header = (
                self.table_headers[index]
                if index < len(self.table_headers)
                else ""
            )
            if not is_torrent_text(header):
                return index
        return None

    def _finish_table(self):
        data_rows = [cells for cells, _ in self.table_rows]
        if not data_rows:
            return
        num_cols = max(len(row) for row in data_rows)
        if self.table_headers:
            num_cols = max(num_cols, len(self.table_headers))
        direct_col = self._direct_column(num_cols)
        if direct_col is None:
            return
        section = self.table_section or self.fallback_category
        for cells in data_rows:
            if not cells:
                continue
            title = cells[0].text.strip()
            if not title:
                continue
            if direct_col >= len(cells):
                continue
            link = None
            for href in cells[direct_col].links:
                href = href.strip()
                if not href or is_torrent_href(href):
                    continue
                resolved = urllib.parse.urljoin(self.page_url, href)
                if not is_http_url(resolved):
                    continue
                link = resolved
                break
            if link is None:
                continue  # no direct link in the target cell
            self.records.append(
                {"name": title, "url": link, "category": section}
            )


def parse_category_page(page_url, html, fallback_category=""):
    parser = CategoryParser(page_url, fallback_category)
    parser.feed(html)
    return parser.records


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(argv=None):
    arg_parser = argparse.ArgumentParser(
        description="Build a flat JSON file list for the Portside/ONE "
        "remote folder from a category site."
    )
    arg_parser.add_argument(
        "--base-url",
        default=os.environ.get("ADAPTER_BASE_URL", ""),
        help="Site root, e.g. http://localhost:8080 "
        "(or set ADAPTER_BASE_URL)",
    )
    arg_parser.add_argument(
        "--output",
        default="remote.json",
        help="Output JSON file (default: remote.json)",
    )
    args = arg_parser.parse_args(argv)
    base_url = args.base_url.strip()
    if not base_url:
        arg_parser.error(
            "no base URL: pass --base-url or set ADAPTER_BASE_URL"
        )
    if not base_url.endswith("/"):
        base_url += "/"

    index_html = fetch(base_url)
    if index_html is None:
        print(f"error: could not fetch the index page {base_url}",
              file=sys.stderr)
        return 1

    categories = discover_category_urls(base_url, index_html)
    print(f"found {len(categories)} category page(s) on the index",
          file=sys.stderr)

    records = []
    for url, link_text in categories:
        html = fetch(url)
        if html is None:
            continue
        page_records = parse_category_page(url, html, link_text)
        print(f"  {url}: {len(page_records)} record(s)", file=sys.stderr)
        records.extend(page_records)

    # Deduplicate by URL (first occurrence wins), then sort by name.
    seen_urls = set()
    unique = []
    for record in records:
        if record["url"] in seen_urls:
            continue
        seen_urls.add(record["url"])
        unique.append(record)
    unique.sort(key=lambda r: r["name"].lower())

    with open(args.output, "w", encoding="utf-8") as out:
        json.dump(unique, out, ensure_ascii=False, indent=2)
        out.write("\n")

    print(
        f"wrote {len(unique)} record(s) "
        f"({len(records) - len(unique)} duplicate(s) removed) "
        f"to {args.output}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
