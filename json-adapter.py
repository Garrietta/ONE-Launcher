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
     navigation (<nav> / <aside>, plus a <div data-slot="sidebar">
     for SvelteKit-style sidebars) is harvested for internal
     category URLs; external domains and anchor links (#) are
     filtered out. The root page itself is never parsed for files.
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

Minerva magnet crawling (v1.8.0, --minerva): the Minerva browse
page already carries the magnets. Each <div class="entry"> on a
browse page holds the name (a <strong> inside the first <a>), a
<span> with the size, and a magnet button whose onclick is
downloadMagnet('magnet:?xt=urn:btih:<hash>&dn=Minerva_Myrient').
The dn is the same generic string for every entry, so the real name
comes from the entry, and each entry has its own infohash — one
magnet per entry. The adapter rebuilds the magnet with the real
name as dn plus the three trackers and emits one record per entry:
{name, url: <full magnet URI>, kind: "magnet", category, size?}.
A folder page gives one magnet per system; a system page gives one
magnet per game. The root browse page (/browse/) recurses one level
into its Redump and No-Intro subfolders and emits their systems.
--minerva is a single Minerva browse URL (or "all" for the root);
the page URL itself selects the scope. A magnet entry is marked
"kind": "magnet" so the app can tell it apart from a direct
download; the app only ever COPIES the magnet link, never downloads
or resolves it. Without --minerva the adapter is r-roms only,
exactly as before. (The CDN .torrent approach is dead:
https://cdn.minerva-archive.org/torrents/ 404s — verified
2026-11-16. No bencodepy, no .torrent decoding.)

Standard library only. Python 3.8+.
"""

import argparse
import json
import os
import re
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
    """Collects (href, link text) for links inside <nav> / <aside> and
    inside <div data-slot="sidebar"> (SvelteKit-style sidebar).

    The live site's sidebar is a <div data-slot="sidebar">, not a
    <nav>/<aside>, so nav/aside alone found 0 category pages.
    Sidebar depth counts nested <div>s so a closing </div> inside
    the sidebar does not end the region early."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.nav_depth = 0
        self.sidebar_depth = 0
        self.current_href = None
        self.current_text = []
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag in ("nav", "aside"):
            self.nav_depth += 1
        if tag == "div":
            if self.sidebar_depth > 0:
                self.sidebar_depth += 1
            else:
                slots = dict(attrs).get("data-slot", "")
                if "sidebar" in [t.lower() for t in slots.split()]:
                    self.sidebar_depth = 1
        if (self.nav_depth > 0 or self.sidebar_depth > 0) and tag == "a":
            href = dict(attrs).get("href")
            if href:
                self.current_href = href
                self.current_text = []

    def handle_endtag(self, tag):
        if tag in ("nav", "aside") and self.nav_depth > 0:
            self.nav_depth -= 1
        elif tag == "div" and self.sidebar_depth > 0:
            self.sidebar_depth -= 1
        if tag == "a" and self.current_href is not None:
            text = "".join(self.current_text).strip()
            self.links.append((self.current_href, text))
            self.current_href = None
            self.current_text = []

    def handle_data(self, data):
        if self.current_href is not None:
            self.current_text.append(data)


def discover_category_urls(base_url, index_html):
    """Internal category URLs from the index sidebar (<nav>/<aside>
    plus <div data-slot="sidebar">), in page order."""
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
# Minerva magnet crawling: browse page -> magnet entries
# ---------------------------------------------------------------------------

# The Minerva browse page already carries the magnets: each
# <div class="entry"> holds the name (a <strong> inside the first
# <a>), a <span> with the size, and a magnet button whose onclick is
# downloadMagnet('magnet:?xt=urn:btih:<hash>&dn=Minerva_Myrient').
# The dn is the same generic string for every entry, so the real name
# comes from the entry, and each entry has its own infohash — one
# magnet per entry, no per-file indices. (The CDN .torrent approach
# is dead: https://cdn.minerva-archive.org/torrents/ 404s, so there
# is no .torrent listing to decode — verified 2026-11-16. No
# bencodepy, no CDN fetch, no .torrent decoding.)

TRACKERS = [
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
]

# The root browse page's game-system groups. The root lists more
# collections than these (bitsavers, MAME, TOSEC, ...), but a root
# crawl is for the game systems, so only these two subfolders are
# recursed into.
ROOT_SYSTEM_GROUPS = ("redump", "no-intro")

_SIZE_RE = re.compile(r"^\s*([\d.]+)\s*([KMGT]?i?B)\s*$", re.IGNORECASE)
_SIZE_MULT = {
    "B": 1,
    "KB": 1024, "MB": 1024 ** 2, "GB": 1024 ** 3, "TB": 1024 ** 4,
    "KIB": 1024, "MIB": 1024 ** 2, "GIB": 1024 ** 3, "TIB": 1024 ** 4,
}
_INFOHASH_RE = re.compile(r"urn:btih:([0-9a-fA-F]+)")


def parse_minerva_size(text):
    """A size string like '21.21 GB' -> bytes, or None when it does
    not parse. Informational only — the app shows it when present."""
    if not text:
        return None
    match = _SIZE_RE.match(text.strip())
    if not match:
        return None
    try:
        value = float(match.group(1))
    except ValueError:
        return None
    mult = _SIZE_MULT.get(match.group(2).upper())
    if mult is None:
        return None
    return int(value * mult)


def build_minerva_magnet(infohash, name):
    """The full magnet URI for one entry: its own infohash, the real
    (entry) name as dn, and the three trackers."""
    dn = urllib.parse.quote(name, safe="")
    magnet = f"magnet:?xt=urn:btih:{infohash.lower()}&dn={dn}"
    for tracker in TRACKERS:
        magnet += f"&tr={tracker}"
    return magnet


class _MinervaEntryParser(HTMLParser):
    """Parse <div class='entry'> elements from a Minerva browse page.

    Each entry yields a dict with name (the <strong> text, trailing
    '/' stripped, HTML entities unescaped), href (the first <a>'s
    browse URL, for the root recursion), size (the <span> text), and
    hashes (the infohashes from the magnet onclick — usually one;
    a collection split across several torrents, e.g. IBM - PC
    compatible, has one per torrent via downloadMagnets([...])).
    Entries are collected in page order. Only <div> depth is tracked,
    so void elements inside an entry cannot end it early.
    """

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.entries = []
        self._entry = None
        self._div_depth = 0
        self._in_strong = False
        self._name_done = False
        self._in_span = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if self._entry is None:
            if tag == "div" and "entry" in attrs.get("class", "").split():
                self._entry = {"name": "", "href": None,
                               "size": None, "hashes": []}
                self._div_depth = 1
                self._name_done = False
            return
        if tag == "div":
            self._div_depth += 1
        if tag == "a" and self._entry["href"] is None and attrs.get("href"):
            self._entry["href"] = attrs["href"]
        if tag == "strong" and not self._name_done:
            self._in_strong = True
        if tag == "span":
            self._in_span = True
        onclick = attrs.get("onclick", "")
        if "downloadMagnet" in onclick:
            # downloadMagnet('magnet:?xt=...') for one torrent, or
            # downloadMagnets(["magnet:...", ...]) for a collection
            # split across several. The parser already unescaped
            # &amp; to & (convert_charrefs). Only the infohashes
            # matter — the magnet is rebuilt with the real name.
            self._entry["hashes"].extend(
                _INFOHASH_RE.findall(onclick)
            )

    def handle_endtag(self, tag):
        if self._entry is None:
            return
        if tag == "strong":
            self._in_strong = False
            self._name_done = True
        if tag == "span":
            self._in_span = False
        if tag == "div":
            self._div_depth -= 1
            if self._div_depth <= 0:
                self.entries.append(self._entry)
                self._entry = None

    def handle_data(self, data):
        if self._entry is None:
            return
        if self._in_strong:
            self._entry["name"] += data
        elif self._in_span and self._entry["size"] is None:
            text = data.strip()
            if text:
                self._entry["size"] = text


def minerva_page_records(page_url, category):
    """One Minerva browse page -> magnet records. Each entry becomes
    one record {name, url: full magnet URI, kind: 'magnet',
    category, size?}; a collection split across several torrents
    becomes one record per torrent, named '<name> (part N of M)'.
    Entries without a magnet onclick (no infohash) are skipped —
    there is no magnet to copy. Returns [] when the page cannot be
    fetched or has no usable entries."""
    html = fetch(page_url)
    if html is None:
        return []
    parser = _MinervaEntryParser()
    try:
        parser.feed(html)
    except Exception:
        return []
    records = []
    for entry in parser.entries:
        name = (entry["name"] or "").strip()
        if name.endswith("/"):
            name = name[:-1].strip()
        if not name:
            continue
        hashes = entry.get("hashes") or []
        if not hashes:
            continue
        # The <span> size is the whole collection's total; it only
        # belongs on a single-magnet entry, not on one part of a
        # split collection.
        size = parse_minerva_size(entry["size"]) if len(hashes) == 1 else None
        for idx, infohash in enumerate(hashes):
            if len(hashes) == 1:
                rec_name = name
            else:
                rec_name = f"{name} (part {idx + 1} of {len(hashes)})"
            record = {
                "name": rec_name,
                "url": build_minerva_magnet(infohash, rec_name),
                "kind": "magnet",
                "category": category,
            }
            if size is not None:
                record["size"] = size
            records.append(record)
    return records


def _minerva_path_parts(page_url):
    """The browse path as segments, with '.' and empty segments
    removed, so /browse/./Redump/ reads as [browse, Redump]."""
    path = urllib.parse.unquote(
        urllib.parse.urlparse(page_url).path
    )
    return [p for p in path.split("/") if p and p != "."]


def _minerva_is_root(page_url):
    """True when the URL is the root browse directory (/browse/ with
    nothing after it)."""
    parts = _minerva_path_parts(page_url)
    if "browse" not in parts:
        return False
    return len(parts) - parts.index("browse") - 1 == 0


def _minerva_category_from_url(page_url):
    """The folder/system name for a browse page: the last path
    segment after /browse/ (e.g. 'Redump', 'Microsoft - Xbox 360')."""
    parts = _minerva_path_parts(page_url)
    if "browse" in parts:
        rest = parts[parts.index("browse") + 1:]
        if rest:
            return rest[-1]
    return parts[-1] if parts else "Minerva"


def collect_minerva_records(minerva_arg):
    """Minerva magnet records for --minerva (a single browse URL, or
    'all' for the root). The root browse page (/browse/) recurses one
    level into its Redump and No-Intro subfolders and emits their
    systems; a folder or system page emits that page's entries
    directly. Returns [] when Minerva is not requested or a page
    cannot be reached. A fetch failure warns and skips, never fails
    the whole run."""
    if not minerva_arg:
        return []
    arg = minerva_arg.strip()
    if arg.lower() == "all":
        arg = "https://minerva-archive.org/browse/"
    if not is_http_url(arg):
        print(f"warning: --minerva is not a URL, skipping Minerva: {arg}",
              file=sys.stderr)
        return []
    if _minerva_is_root(arg):
        # The root page lists the top-level collections; recurse one
        # level into the game-system groups and emit their systems.
        html = fetch(arg)
        if html is None:
            print("warning: Minerva root page unreachable; skipping "
                  "Minerva", file=sys.stderr)
            return []
        parser = _MinervaEntryParser()
        try:
            parser.feed(html)
        except Exception:
            return []
        records = []
        for entry in parser.entries:
            name = (entry["name"] or "").strip()
            if name.endswith("/"):
                name = name[:-1].strip()
            if name.lower() not in ROOT_SYSTEM_GROUPS:
                continue
            href = entry["href"]
            if not href:
                continue
            sub_url = urllib.parse.urljoin(arg, href).replace("/./", "/")
            sub = minerva_page_records(sub_url, name)
            print(f"  Minerva {name}: {len(sub)} record(s)",
                  file=sys.stderr)
            records.extend(sub)
        print(f"Minerva: {len(records)} record(s) from the root "
              f"browse page", file=sys.stderr)
        return records
    category = _minerva_category_from_url(arg)
    records = minerva_page_records(arg, category)
    print(f"Minerva: {len(records)} record(s) from {arg}",
          file=sys.stderr)
    return records
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
    arg_parser.add_argument(
        "--minerva",
        default=os.environ.get("ADAPTER_MINERVA_URL", ""),
        help="Minerva magnet crawl: a Minerva browse URL (e.g. "
        "https://minerva-archive.org/browse/Redump/), or 'all' for "
        "the root browse page, or set ADAPTER_MINERVA_URL. "
        "Empty/absent skips Minerva.",
    )
    args = arg_parser.parse_args(argv)
    base_url = args.base_url.strip()
    if not base_url:
        arg_parser.error(
            "no base URL: pass --base-url or set ADAPTER_BASE_URL"
        )
    if not base_url.endswith("/"):
        base_url += "/"

    minerva_arg = (args.minerva or "").strip()

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

    # Minerva magnet records, if requested: one entry per entry on
    # the browse page, each carrying its full magnet URI. The r-roms
    # records above are untouched by this step.
    minerva_records = []
    if minerva_arg:
        minerva_records = collect_minerva_records(minerva_arg)
        records.extend(minerva_records)

    # Deduplicate by URL (first occurrence wins), then sort by name.
    seen_urls = set()
    unique = []
    for record in records:
        url = record.get("url")
        if url in seen_urls:
            continue
        seen_urls.add(url)
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
