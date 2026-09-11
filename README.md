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

## The source register — [`facts.edn`](facts.edn)

The rule above requires a citation against a set. `facts.edn` is that set:
9 statutes, 11 pages across the three Cabinet Office hosts, the 5 proposal
forms and published plans, and 6 controls. **A regulation not in that table
has no spec-basis here** — extend the table, never invent a law id or a URL.

It is tx-data, so it loads like every other EDN corpus in this workspace:

```clojure
(d/transact conn (edn/read-string (slurp "facts.edn")))
```

Every entry is re-fetched from the live authority by:

```bash
kbb --backend sci --classpath scripts scripts/verify-facts.cljk     # 0 ok / 1 wrong / 2 REFUSED
kbb --backend sci --classpath scripts scripts/break-tests.cljk      # does that script actually go red?
kbb --backend sci --classpath scripts scripts/measure-host.cljk     # regenerate the header's numbers
kbb --backend sci scripts/mutation-check.cljk                       # the fleet gate's in-repo suite (tally)
```

The fleet gate (`scripts/itonami-verify-proposal.cljs`) needs the verifier to run
under **its** bare invocation (`kbb --backend sci scripts/verify-facts.cljk`), so the measuring
functions that were a sibling namespace (`scripts/host_probe.cljk`) are inlined
into `scripts/verify-facts.cljk` as well. Run it WITH or WITHOUT `--classpath scripts` --
the checks are identical either way. `scripts/mutation-check.cljk` restates cao's
own `break-tests.cljs` cases in the gate's `caught=/not-caught=` tally so the gate
can read cao's discrimination before it will land a proposal against this repository.

**Exit 2 is not a pass.** A run that could not answer — an unreadable body, a
404 probe that stopped 404ing, a needle that has drifted into site chrome —
reports differently from a run that checked everything and found a problem,
because collapsing those two is how a check quietly stops being one.

### This repository's subject is served from three hosts, and they disagree

Not thoroughness — forced. `www.cao.go.jp` is the ministry proper,
`www8.cao.go.jp` carries 規制改革 and the 所管省庁 proposal-and-answer channel,
and `www.chisou.go.jp` carries 国家戦略特区 and the route to 主務官庁. A register
written against one of them is silently missing two thirds of the subject.

Four things these hosts do that the checks are shaped around, all measured
and written up in `facts.edn`'s header:

- **内閣府 is on the CAO 404 page, five times, and 地方創生 is on the chisou
  one.** The most obvious needle for each host is the name of the body that
  serves it, and both verify against a page that does not exist. Needles are
  chosen by subtracting the live 404 body **of that page's own host**, and the
  verifier redoes that subtraction every run against the right probe.
- **`www.chisou.go.jp` does not answer 404 — it redirects.** A missing page
  returns 302 to `<path>/index.html` with an empty `application/xml` body, and
  only that answers 404. A client that does not follow sees neither a live
  page nor a missing one.
- **`www.cao.go.jp` and `www8.cao.go.jp` serve a byte-identical 404 and are
  still different hosts.** `www8` reports an accurate `Content-Length` on
  `HEAD`; `www` reports none at all, for a live page or a missing one. Every
  check issues a `GET`.
- **A fabricated law id answers HTTP 200.** The statute checks never read the
  status; identity is `total_count` plus an exact `law_id` match, and the
  repeal fields live in `revision_info` rather than `law_info`, so their
  *presence* is asserted separately from their value.

Nothing asserts a byte size. The regulatory-reform publications index is a
real, current page and it is **285 characters shorter than its own host's
404 body** — a did-I-get-a-substantial-body heuristic passes the missing page
and doubts that one.

### Two controls worth knowing about before you extend the table

The controls are cited for **no proposition**. Deleting one weakens no
citation; it turns the check that depended on it into something that cannot
fail. Two of them are near misses rather than distant ones:

- **`:law/repealed-sandbox-control` is 生産性向上特別措置法** — the Act that
  *created* the regulatory sandbox in 2018, repealed in 2021 when the sandbox
  moved to 産業競争力強化法. It is one of only fourteen laws in 9,550 flagged
  `remain_in_force`, so it fails a repeal check and **passes** an in-force
  check built on that field. An author adding sandbox statutes would reach for
  it by name before reaching for the Act that replaced it.
- **`:law/previous-enforced-control` is 構造改革特別区域法** — squarely in this
  repository's subject matter, not repealed, and the revision the authority
  serves is nevertheless not the one in force. A check reading only
  `repeal_status` hands back superseded text while reporting success. It is a
  control for exactly that reason, and a draft that needs the structural
  reform regime must resolve the enforced revision rather than read this entry.

### One measuring instrument, and why

Every character count above comes from `scripts/host_probe.cljk`, and the
verifier subtracts needles with the same `de-tag`. The first pass of this file
used one ported unchanged from a sibling register, and it silently failed to
strip `<style>` and `<script>` contents: in ClojureScript,
`clojure.string/replace` rebuilds the regex it is handed and carries over only
the `g`, `i`, `m` and `u` flags — **the `s` (dotAll) flag is dropped**, so
`#"(?is)<style.*?</style>"` strips a one-line style element and silently fails
on every multi-line one. On the chisou 404 that was 682 characters measured
against 148 measured correctly: nearly three quarters of what a needle would
have been subtracted from was stylesheet.

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
