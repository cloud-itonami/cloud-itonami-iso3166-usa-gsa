# cloud-itonami-iso3166-usa-gsa

Open ISO 3166 Agency Blueprint for **USA-GSA**: General Services Administration —
a United States agency-level LEAF under the `cloud-itonami-iso3166-usa`
country-level coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-usa` for general U.S. market entry)
gets a Compliance Advisor + independent **GSA Federal-Procurement Compliance
Governor** to navigate SAM.gov entity registration and the Unique Entity
Identifier (UEI), and GSA Multiple Award Schedule (MAS, "GSA Schedule")
offer submission.

## No robotics premise — digital/data service exemption

Agency-specific compliance navigation is a pure data/software service with
no physical-domain work — the same exemption class as `cloud-itonami-6310`
and `cloud-itonami-gtin-*`. `blueprint.edn` sets
`:itonami.blueprint/robotics false` and `:required-technologies` lists only
real capabilities (`:identity`, `:forms`, `:dmn`, `:bpmn`, `:audit-ledger`),
no `:robotics`.

## Core Contract

```text
operator intake + prior filing/compliance history
        |
        v
GSA Compliance Advisor -> GSA Federal-Procurement Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated SAM.gov registration / MAS Schedule offer submission + audit ledger
```

No automated proposal can submit a SAM.gov registration or MAS Schedule
offer the governor refuses, suppress a compliance record, or claim a
regulatory-requirement fact the governor has not cleared. `:filing/submit`
is never in any phase's `:auto` set — it always requires human sign-off.

## Implementation

`src/gsacompliance/` — a langgraph-clj StateGraph actor, same containment
shape as the Japan agency-leaf siblings (`cloud-itonami-iso3166-jpn-mod`'s
`defensecompliance.*`, `-jpn-mof`'s `mofcompliance.*`, `-jpn-moe`'s
`greenprocurement.*`, `-jpn-audit`'s `auditreadiness.*`): advisor sealed to
proposals-only, independent governor, append-only ledger, `Store` protocol
swap, phase gate.

- `facts.cljc` — the SAM.gov/UEI registration + GSA Multiple Award Schedule
  + GSAM-as-FAR-supplement + FAR-Council joint-ownership catalog, the ONLY
  source of regulatory-requirement facts the actor may cite. Four entries:
  `:sam-registration`, `:mas-schedule` (both real filing tracks),
  `:gsam-supplement` and `:far-council` (citation-only disambiguation
  entries, never themselves the subject of a filing).
- `governor.cljc` — the GSA Federal-Procurement Compliance Governor: a
  spec-basis/no-fabrication HARD check, a **legacy-DUNS-citation** HARD
  check (DUNS was fully retired 2022-04-04 — the SAM-generated Unique
  Entity Identifier (UEI) is the only current identifier; citing DUNS as
  current is treated as fabrication, unconditional), a **FAR-owner-
  misattribution** HARD check (the FAR is jointly governed by the FAR
  Council — Secretary of Defense + GSA Administrator + NASA Administrator
  — GSA is one of three co-owners of the FAR itself, not its sole owner;
  only the GSAM supplement is exclusively GSA's own document), an
  evidence-incomplete check, a **SAM-registration-missing** HARD check
  (`:mas-schedule` `:filing/submit` — an active SAM.gov entity registration
  + UEI is a practical prerequisite to any MAS offer), a **medical-
  schedule-misattribution** HARD check (`:mas-schedule` `:filing/submit` —
  the Department of Veterans Affairs holds authority DELEGATED FROM GSA to
  run a parallel schedule for medical products/services; this actor never
  silently claims that offering under GSA's own MAS), an
  independently-recomputed engagement-fee-mismatch check (three revenue
  lines: base fee + monitoring subscription + optional audit-export
  package), a confidence-floor/actuation gate, and double-draft/
  double-submit guards, per track.
- `store.cljc` — `MemStore`/`DatomicStore` (via `kotoba-lang/langchain-
  store`, not a hand-rolled `enc`/`dec*`) for the `engagement` entity,
  which tracks the `:sam-registration` and `:mas-schedule` tracks'
  filing state independently, plus the engagement-level
  `:sam-registration-verified?`, `:medical-schedule-offering?`, and
  `:va-delegation-acknowledged?` gates.
- `registry.cljc` — pure-function filing-draft/filing-submit record
  construction, one sequence per track.
- `gsacompliancellm.cljc` — the GSA Compliance Advisor (mock LLM,
  proposals only).
- `operation.cljc` — the StateGraph: intake → advise → govern → decide
  → [request-approval →] commit/hold, `interrupt-before` on human
  approval.
- `phase.cljc` — phase 0→3 rollout; `:filing/draft`/`:filing/submit`
  are permanently absent from every phase's `:auto` set.

Ops: `:engagement/intake`, `:compliance/assess` (per-track evidence
checklist), `:filing/draft`, `:filing/submit` — the latter three take a
`:track` (`:sam-registration`, `:mas-schedule`, `:gsam-supplement`, or
`:far-council`) in the request, since one engagement runs both filing
tracks independently. `:gsam-supplement` and `:far-council` are
citation-only spec-basis entries for owner-authority-attribution checks,
not tracks this actor drafts/submits filings for.

### Out of scope (by design)

This actor is deliberately narrow to GSA/SAM.gov's own regulatory surface:
it does **not** cite DFARS or CMMC (DoD-specific acquisition/cybersecurity
regimes — `cloud-itonami-iso3166-usa-dod`'s scope) and does **not** cite
SCA, DBRA, or OFCCP (Department of Labor-specific labor-standards regimes
— `cloud-itonami-iso3166-usa-dol`'s scope). `test/gsacompliance/facts_test.clj`
asserts these tracks return no spec-basis from this repo's own catalog.

## What this is NOT

- **Not the General Services Administration itself, and not the
  government of the United States.** Commercial compliance navigation
  only.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official GSA/SAM.gov/acquisition.gov source and route final filings to
  U.S.-licensed counsel or a registered agent where the law requires
  licensed representation.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `USA-GSA`, `:parent "USA"`, cross-referenced to ooyake's
`gov.usa.gsa`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
