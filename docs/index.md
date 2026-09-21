---
layout: home

hero:
  name: FinMe
  text: Personal finance and credit health, measured
  tagline: Extracts transactions from bank statements and receipt photos, categorises them, and reports credit utilisation — with extraction accuracy measured against a labelled benchmark rather than assumed.
  actions:
    - theme: brand
      text: What it does
      link: /product/overview
    - theme: alt
      text: Run it locally
      link: /getting-started/quick-start
    - theme: alt
      text: Architecture
      link: /development/architecture

features:
  - title: Measured extraction, not assumed
    details: An evaluation harness scores the PDF and photo pipelines on precision, recall and hallucination rate against a labelled golden set. The differentiator is the measurement, not the dashboard.
  - title: Redaction before any external call
    details: Account numbers, ID numbers and personal names are stripped before a single byte reaches an AI provider — independent of, and in addition to, provider-side retention settings.
  - title: Deterministic financial math
    details: Every figure — spend totals, credit utilisation — is computed in code. The AI narrates and prioritises; it never does arithmetic the user relies on.
  - title: A fallback chain, not a single provider
    details: Groq to OpenRouter to Cloudflare Workers AI, each tried in order. One provider rate-limiting does not take the feature down.
---

## Where to start

| You are | Start here |
| --- | --- |
| Evaluating the project | [Product overview](/product/overview) then [Architecture](/development/architecture) |
| Running it locally | [Quick start](/getting-started/quick-start) |
| Joining as a developer | [Introduction](/getting-started/introduction), then [Conventions](/development/conventions) |
| Maintaining it in production | [Deployment](/operations/deployment) and [Troubleshooting](/operations/troubleshooting) |
| Wondering why something is built that way | [Decision records](/decisions/ADR-001-ai-provider-fallback-chain) |

## Live application

<https://finme.me>
