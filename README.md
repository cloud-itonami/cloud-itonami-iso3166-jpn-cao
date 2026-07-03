# cloud-itonami-iso3166-jpn-cao

Open ISO 3166 Agency Blueprint for **JPN-CAO**: Cabinet Office
(内閣府, CAO) — a Japan-agency-level LEAF under
the `cloud-itonami-iso3166-jpn` country-level coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-jpn` for general Japan market entry)
gets a Compliance Advisor + independent **Cross-Ministerial Compliance Governor** to
navigate eligibility screening for Cabinet-Office-coordinated cross-ministerial programs (e.g. regional-revitalization or regulatory-sandbox schemes administered through the Cabinet Office) that span more than one ministry's jurisdiction.

This is the final repo in the Japan agency-level sweep started by
ADR-2607040100 — with this blueprint published, all 19/19 Japan central-
government bodies in `kotoba-lang/iso3166` are `:maturity :blueprint`.

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
Compliance Advisor -> Cross-Ministerial Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated filing / registration / compliance-program submission + audit ledger
```

No automated proposal can submit a filing or registration the governor
refuses, suppress a compliance record, or claim a legal conclusion the
governor has not cleared. `:filing/submit` is never in any phase's `:auto`
set — it always requires human sign-off (mirrors `cloud-itonami-M6910`'s
`filing-submit-never-auto-at-any-phase` invariant).

## What this is NOT

- **Not Cabinet Office (内閣府) itself, and not the
  government of Japan.** See [`docs/business-model.md`](docs/business-model.md)
  for the boundary with `com-etzhayyim-ooyake`, `matsurigoto`,
  `com-etzhayyim-toritsugi`, `legal-entity.etzhayyim.com`,
  `cloud-itonami-M6910`, and the country-level `cloud-itonami-iso3166-jpn`.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official CAO source and route final filings to
  Japan-licensed counsel or a registered agent where the law requires
  licensed representation.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `JPN-CAO`, `:parent "JPN"`, cross-referenced to ooyake's
`gov.jpn.cao`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
