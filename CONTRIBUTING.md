# Contributing

`cloud-itonami-iso3166-usa-gsa` accepts contributions to the OSS actor, governor tests,
documentation, examples and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, audit or disclosure
behavior.

## Rules

- Do not commit real client or business-registration data.
- Keep production actions behind the GSA Federal-Procurement Compliance
  Governor.
- Never let the advisor state a legal/tax conclusion as fact — every
  regulatory requirement must cite the official SAM.gov/GSA/
  acquisition.gov source, and never cite the retired DUNS identifier as
  current.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
