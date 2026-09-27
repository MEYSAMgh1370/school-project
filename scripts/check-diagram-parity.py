#!/usr/bin/env python3
"""
Assert that a hand-drawn C4 SVG still says the same thing as the Mermaid
source it sits next to in the Markdown.

The SVGs under `openspec/diagrams/assets/` are laid out by hand, so nothing
regenerates them when the Mermaid changes. They have drifted before: the
components SVG once drew an edge from AccessDecisionService to iam.roles (a
dependency that does not exist), pointed both authorization-aspect edges at
IamFacadeImpl instead of the IamFacade interface, and omitted three nodes and
eleven edges the Mermaid had. None of that is visible by skimming the picture.

This check recovers the graph from the SVG *geometry* rather than trusting a
caption: every node rect carries `data-node="<mermaid id>"`, and each edge
path's first and last point is resolved to whichever node box it touches. The
resulting edge set is then diffed against the Mermaid flowchart both ways.

That means it catches an arrow that lands on the wrong box — the failure mode
a human reviewer is worst at spotting, because a 25px error looks correct.

Adding a diagram:
    1. give every node rect in the SVG a `data-node` matching the Mermaid id;
    2. add an entry to DIAGRAMS below.

Node shapes other than <rect> are not resolved yet, which is why only the
components diagram is wired up. The context and container SVGs draw
PostgreSQL as a <path>/<ellipse> cylinder, so they need a bounding-box
fallback before they can be checked the same way.

Usage:
    python3 scripts/check-diagram-parity.py
    python3 scripts/check-diagram-parity.py --diagram iam-components -v

Standard library only. No pip install.
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# name -> (svg path, markdown path, heading whose ```mermaid block to compare)
DIAGRAMS = {
    "iam-components": (
        "openspec/diagrams/assets/iam-c4-components.svg",
        "openspec/diagrams/iam-c4.md",
        "## Level 3: IAM Components",
    ),
}

SVG_NS = "{http://www.w3.org/2000/svg}"
EDGE_CLASSES = {"edge", "edge2", "edge-dotted"}

# How far outside a box an endpoint may sit and still count as touching it.
# Arrowheads stop a few px short of the stroke, so this cannot be zero; keep it
# well under the smallest gap between two boxes (46px here) or edges get
# resolved to a neighbour.
HIT_TOLERANCE = 9


# --------------------------------------------------------------------------
# SVG
# --------------------------------------------------------------------------

def read_svg_nodes(root):
    """data-node rects -> {id: (x0, y0, x1, y1)}."""
    nodes = {}
    for rect in root.iter(SVG_NS + "rect"):
        node_id = rect.get("data-node")
        if not node_id:
            continue
        if node_id in nodes:
            raise ValueError("duplicate data-node %r in SVG" % node_id)
        x, y = float(rect.get("x")), float(rect.get("y"))
        nodes[node_id] = (x, y, x + float(rect.get("width")),
                          y + float(rect.get("height")))
    return nodes


_TOKEN = re.compile(r"([MmHhVvCcLlZz])([^MmHhVvCcLlZz]*)")
_NUM = re.compile(r"-?\d+\.?\d*")


def path_endpoints(d):
    """First and last point of a path. Absolute commands only, which is all
    these diagrams use; a relative command would silently mislocate an edge,
    so refuse rather than guess."""
    x = y = 0.0
    points = []
    for command, argument in _TOKEN.findall(d):
        if command.islower() and command != "z":
            raise ValueError("relative path command %r unsupported: %s" % (command, d))
        values = [float(v) for v in _NUM.findall(argument)]
        if command == "M" or command == "L":
            x, y = values[0], values[1]
        elif command == "H":
            x = values[-1]
        elif command == "V":
            y = values[-1]
        elif command == "C":
            x, y = values[-2], values[-1]
        elif command in ("Z", "z"):
            continue
        points.append((x, y))
    if not points:
        raise ValueError("no points in path: %s" % d)
    return points[0], points[-1]


def node_at(nodes, px, py):
    for node_id, (x0, y0, x1, y1) in nodes.items():
        if (x0 - HIT_TOLERANCE <= px <= x1 + HIT_TOLERANCE
                and y0 - HIT_TOLERANCE <= py <= y1 + HIT_TOLERANCE):
            return node_id
    return None


def read_svg_edges(root, nodes):
    """Returns (edges, unresolved). Direction follows marker-end, i.e. the
    path's last point is the arrowhead."""
    edges, unresolved = set(), []
    for path in root.iter(SVG_NS + "path"):
        if path.get("class") not in EDGE_CLASSES:
            continue
        d = path.get("d")
        (sx, sy), (ex, ey) = path_endpoints(d)
        src, dst = node_at(nodes, sx, sy), node_at(nodes, ex, ey)
        if src is None or dst is None or src == dst:
            unresolved.append((d, src, dst))
        else:
            edges.add((src, dst))
    return edges, unresolved


# --------------------------------------------------------------------------
# Mermaid
# --------------------------------------------------------------------------

# a --> b | a -->|label| b | a -.label.-> b | a -.-> b
_LINK = re.compile(
    r"^\s*(\w+)\s*(?:-->|-\.[^>]*?->)\s*(?:\|[^|]*\|\s*)?(\w+)\s*$")
# node definitions: auth[AuthController], users[(iam.users)]
_NODE_DEF = re.compile(r"^\s*(\w+)[\[\(\{]")


def read_mermaid_block(markdown_path, heading):
    text = open(markdown_path, encoding="utf-8").read()
    if heading not in text:
        raise ValueError("heading %r not found in %s" % (heading, markdown_path))
    after = text.split(heading, 1)[1]
    match = re.search(r"```mermaid\n(.*?)```", after, re.S)
    if not match:
        raise ValueError("no mermaid block under %r" % heading)
    return match.group(1)


def parse_mermaid(block):
    nodes, edges = set(), set()
    for raw in block.splitlines():
        line = raw.split("%%")[0].strip()
        if not line or line.startswith(("subgraph", "end", "flowchart", "graph")):
            continue
        link = _LINK.match(line)
        if link:
            edges.add((link.group(1), link.group(2)))
            nodes.update(link.groups())
            continue
        node = _NODE_DEF.match(line)
        if node:
            nodes.add(node.group(1))
    return nodes, edges


# --------------------------------------------------------------------------

def check(name, verbose):
    svg_rel, md_rel, heading = DIAGRAMS[name]
    svg_path = os.path.join(REPO_ROOT, svg_rel)
    md_path = os.path.join(REPO_ROOT, md_rel)

    root = ET.parse(svg_path).getroot()
    svg_nodes = read_svg_nodes(root)
    svg_edges, unresolved = read_svg_edges(root, svg_nodes)
    mer_nodes, mer_edges = parse_mermaid(read_mermaid_block(md_path, heading))

    problems = []
    if unresolved:
        problems.append(
            "%d edge path(s) do not start and end on two distinct nodes "
            "(an arrow is landing in empty space or on its own source):" % len(unresolved))
        for d, src, dst in unresolved:
            problems.append("    %s\n        start=%s end=%s" % (d, src, dst))

    for label, missing, extra in (
        ("node", mer_nodes - set(svg_nodes), set(svg_nodes) - mer_nodes),
        ("edge", mer_edges - svg_edges, svg_edges - mer_edges),
    ):
        for item in sorted(missing):
            problems.append("    in Mermaid but not SVG (%s): %s" % (
                label, item if label == "node" else "%s -> %s" % item))
        for item in sorted(extra):
            problems.append("    in SVG but not Mermaid (%s): %s" % (
                label, item if label == "node" else "%s -> %s" % item))

    if verbose or problems:
        print("%s: %s" % (name, svg_rel))
        print("  SVG      %d nodes, %d edges" % (len(svg_nodes), len(svg_edges)))
        print("  Mermaid  %d nodes, %d edges" % (len(mer_nodes), len(mer_edges)))

    if problems:
        print("\n  MISMATCH - the SVG and the Mermaid disagree:")
        for line in problems:
            print("  " + line)
        print("\n  The SVG is hand-laid-out and does not regenerate. Fix whichever")
        print("  side is wrong, in %s and %s." % (svg_rel, md_rel))
        return False

    print("%s: parity OK (%d nodes, %d edges)" % (name, len(svg_nodes), len(svg_edges)))
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Check hand-drawn C4 SVGs against their Mermaid source.")
    parser.add_argument("--diagram", choices=sorted(DIAGRAMS), action="append",
                        help="diagram to check (default: all)")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="print counts even when parity holds")
    args = parser.parse_args()

    ok = True
    for name in (args.diagram or sorted(DIAGRAMS)):
        try:
            ok &= check(name, args.verbose)
        except Exception as exc:  # unreadable SVG/Markdown is itself a failure
            print("%s: ERROR %s" % (name, exc))
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
