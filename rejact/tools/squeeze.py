#!/usr/bin/env python3
"""Compact the shipped client runtime.

Squeezes whitespace outside string literals (runs of spaces/tabs drop next to unambiguous
punctuation, otherwise collapse to one space) and removes blank lines. String contents are
never touched, and operators keep their spacing, so `i-- > 0` can never become `i-->0`.
"""

import sys

PUNCT = set(',()[]{};:')


def squeeze(line: str) -> str:
    out = []
    i, n, quote = 0, len(line), None
    while i < n:
        c = line[i]
        if quote is not None:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(line[i + 1])
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in ('"', "'", '`'):
            quote = c
            out.append(c)
            i += 1
            continue
        if c in (' ', '\t'):
            j = i
            while j < n and line[j] in (' ', '\t'):
                j += 1
            prev = out[-1] if out else ''
            nxt = line[j] if j < n else ''
            if out and prev not in PUNCT and nxt not in PUNCT:
                out.append(' ')
            i = j
            continue
        out.append(c)
        i += 1
    return ''.join(out).rstrip()


def main() -> None:
    lines = [squeeze(l) for l in sys.stdin]
    sys.stdout.write('\n'.join(l for l in lines if l.strip()) + '\n')


if __name__ == '__main__':
    main()
