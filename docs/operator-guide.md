# Operator Guide

Implementation: `src/gsacompliance/` (see README.md's Implementation
section for the module map). "the advisor" below is
`gsacompliance.gsacompliancellm`; "the Governor" is
`gsacompliance.governor`; SAM.gov registration and GSA Multiple Award
Schedule (MAS) offer are separate `:track`s (`:sam-registration` /
`:mas-schedule`) on the same client engagement, assessed and filed
independently. SAM.gov/UEI verification is an engagement-level gate
checked on every `:mas-schedule` `:filing/submit`.

## First Deployment

1. Confirm the client already uses (or has completed the equivalent of)
   `cloud-itonami-iso3166-usa` for general U.S. market-entry; this repo is
   an agency-specific supplement, not a substitute.
2. Register the client's intake: business type, the specific
   GSA-regulated activity involved (SAM.gov registration and/or MAS
   Schedule), prior filing/compliance history in the U.S. if any.
3. Run the advisor in read-only mode against SAM.gov's and GSA's
   published guidance.
4. Compare the checklist against the client's current documentation --
   in particular, confirm the client's identifier on file is the
   SAM-generated Unique Entity Identifier (UEI), never a legacy DUNS
   number (DUNS was fully retired 2022-04-04).
5. If the engagement's offering touches medical products/services, flag
   the VA-delegated-medical-schedule disambiguation before proceeding
   with a `:mas-schedule` filing.
6. Enable gated filing/compliance-draft assistance once the GSA
   Federal-Procurement Compliance Governor contract is trusted; actual
   submission always requires human sign-off.

## Minimum Production Controls

- client-owned data store for compliance documents
- clear provenance (official SAM.gov/GSA/acquisition.gov source citation)
  for every requirement surfaced
- approval workflow for any SAM.gov registration or MAS Schedule offer
  submission
- named referral relationship with U.S.-licensed counsel or a registered
  agent for anything beyond checklist/draft assistance
- monthly audit export

## Certification

Certified operators must prove data provenance, audit traceability, that
automated actions cannot bypass the GSA Federal-Procurement Compliance
Governor, that DUNS is never cited as a current identifier, that FAR
owner-authority is never misattributed to GSA alone, and a working
referral relationship with U.S.-licensed counsel or a registered agent for
whatever licensed representation U.S. law requires for actual SAM.gov/GSA
filings.
