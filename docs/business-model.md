# Business Model: Independent GSA/SAM.gov Federal Procurement Registration Compliance Service — United States

Implementation: `src/gsacompliance/` — see README.md's Implementation
section. The Trust Controls below are enforced in code by
`gsacompliance.governor` (spec-basis/no-fabrication HARD check,
legacy-DUNS-citation HARD check, FAR-owner-misattribution HARD check,
evidence-incomplete check, SAM-registration-missing HARD check,
medical-schedule-misattribution HARD check, engagement-fee-mismatch
check, confidence-floor/actuation gate, double-draft/double-submit
guards) and `gsacompliance.phase` (`:filing/submit` absent from every
phase's `:auto` set).

## Classification

- Repository: `cloud-itonami-iso3166-usa-gsa`
- ISO 3166 (agency-level): `USA-GSA`, parent `USA`
- Ooyake cross-reference: `gov.usa.gsa` (General Services Administration)
- Activity: SAM.gov entity registration + Unique Entity Identifier (UEI)
  verification, and GSA Multiple Award Schedule (MAS, "GSA Schedule")
  offer navigation

## Customer

- an operator already using `cloud-itonami-iso3166-usa` whose contract
  touches General Services Administration rules or buying channels
- a foreign SME entering the GSA Multiple Award Schedule program for the
  first time
- an operator confirming their SAM.gov registration relies on the current
  Unique Entity Identifier (UEI), not the retired DUNS number

## Offer

- SAM.gov entity registration + UEI walkthrough and evidence checklist
- GSA Multiple Award Schedule (MAS) offer-readiness checklist, including
  the SAM.gov-registration prerequisite and the VA-delegated-medical-
  schedule disambiguation
- ongoing regulatory-change monitoring for GSA's/SAM.gov's public sources
- compliance-audit export package

## Revenue

- per-engagement compliance-review fee
- recurring regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- any actual SAM.gov registration or MAS Schedule offer submission
  requires GSA Federal-Procurement Compliance Governor clearance and
  always escalates to human sign-off (`:filing/submit` is never automated
  at any phase)
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — this specifically
  includes citing the retired DUNS number as a current identifier
  (replaced by the UEI on 2022-04-04) or claiming GSA is the sole
  owner-authority of the Federal Acquisition Regulation itself (the FAR
  is jointly governed by the FAR Council: DoD + GSA + NASA)
- this service does **not** provide legal or tax advice; characterization
  and filing on the client's behalf beyond checklist/draft assistance
  routes to U.S.-licensed counsel or a registered agent
- every requirement cites the official SAM.gov/GSA/acquisition.gov
  source, never invented

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-usa`**: the COUNTRY-level coordinator (general
  U.S. public-sector market entry). This repo is a narrower, deeper
  AGENCY-level leaf — most operators need the country-level blueprint plus
  only the agency-level blueprints that actually apply to their contract.
- **`cloud-itonami-iso3166-usa-dod`**: a sibling AGENCY-level leaf for the
  Department of Defense's own regulatory surface (DFARS, CMMC). This
  blueprint deliberately does not cite DFARS or CMMC — those are DoD's
  scope, not GSA's.
- **`cloud-itonami-iso3166-usa-dol`**: a sibling AGENCY-level leaf for the
  Department of Labor's own regulatory surface (SCA, DBRA, OFCCP). This
  blueprint deliberately does not cite those — they are DOL's scope, not
  GSA's.
- **`com-etzhayyim-ooyake`** (etzhayyim/root): read-only civic-wayfinding
  mirror of government structure, non-commercial, barred from acting as or
  for the government (G3 impersonation ban). This blueprint is commercial
  and never claims to be the General Services Administration or an
  official channel.
