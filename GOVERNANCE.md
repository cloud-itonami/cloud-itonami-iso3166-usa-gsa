# Governance

`cloud-itonami-iso3166-usa-gsa` is an OSS open-business blueprint. Governance covers both code and
the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the advisor cannot directly submit a SAM.gov registration, MAS
  Schedule offer, or commit a public record.
- the GSA Federal-Procurement Compliance Governor remains independent of
  the advisor.
- a fabricated or stale regulatory-requirement claim cannot be overridden
  by human approval alone — this includes citing the retired DUNS
  identifier as current, or misattributing sole FAR ownership to GSA.
- every commit, hold and approval path is auditable.
- real client/registration data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit, support and
data-flow review, INCLUDING proof of a working referral relationship with
U.S.-licensed counsel or a registered agent for whatever licensed
representation the law of the United States requires for GSA filings.

Certified operators can lose certification for:

- bypassing governor checks
- mishandling client or registration data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- presenting an uncited claim as a legal/tax conclusion
