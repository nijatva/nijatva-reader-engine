# Security

## Threat model

Input is untrusted HTML and CSS, of unknown provenance, up to a bounded size.

- **Parser limits** are enforced before any allocation proportional to input.
- **No execution.** Script content is never evaluated, interpreted or handed to another
  engine. There is no interpreter in this repository to hand it to.
- **Safe URIs.** Link and image schemes are checked against an allow list; `javascript:`
  and friends are refused, not escaped.
- **Images** are rejected at the resolver boundary unless the host supplied local bytes,
  and declared length and dimensions are checked against `ParserLimits` before decoding.
- **Bounded reports.** `CoverageReport` keys are bounded, so a hostile document cannot
  grow the report without limit.
- **Fail closed.** When a limit is hit, parsing stops with a diagnostic rather than
  continuing on a partial, possibly inconsistent tree.

## Reporting

Open an issue. There is no bug bounty.
