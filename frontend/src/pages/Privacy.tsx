import { Link } from "react-router-dom";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./Legal.css";

/**
 * Written to describe what this app actually does, grounded in docs/02-srs.md (NFR-1 through
 * NFR-4) and the real, shipped behaviour in the codebase - not boilerplate. Two things are
 * deliberately NOT claimed here: HIPAA compliance (this app processes no health information and
 * HIPAA does not apply to it at all - claiming it would be false) and formal POPIA certification
 * (real compliance requires a registered Information Officer, a PAIA manual, and documented
 * processes this solo, dev-stage project does not yet have - so this describes alignment with
 * POPIA's principles, not certified compliance). Fields Collins needs to fill in before this is
 * relied on for anything beyond a portfolio demo are marked inline.
 * <p>
 * Not legal advice, and not reviewed by a lawyer - a good-faith, accurate description of real
 * practices, written the way a careful solo developer's policy should read at this stage.
 */
export function Privacy() {
  useDocumentTitle("Privacy Policy — FinMe");

  return (
    <div className="legal-page">
      <header className="legal-header">
        <Link to="/" className="legal-header__brand">
          Fin<span>Me</span>
        </Link>
        <Link to="/">Back to FinMe</Link>
      </header>

      <div className="legal-content">
        <h1>Privacy Policy</h1>
        <p className="legal-updated">Last updated: 28 August 2026</p>

        <p className="legal-intro">
          FinMe is a personal project, currently in active development. This policy describes
          what the app actually does with your data today, in plain language, rather than
          generic legal boilerplate. If anything here ever stops matching how the app really
          behaves, that's a bug in this document - tell{" "}
          ntsobokwanec@gmail.com and it will be corrected.
        </p>

        <nav className="legal-toc" aria-label="Sections">
          <a href="#who">Who runs FinMe</a>
          <a href="#collect">What FinMe collects</a>
          <a href="#use">How it's used</a>
          <a href="#ai">AI providers</a>
          <a href="#redaction">What gets redacted</a>
          <a href="#cookies">Cookies</a>
          <a href="#security">Storage &amp; security</a>
          <a href="#rights">Your rights</a>
          <a href="#retention">Retention</a>
          <a href="#transfers">International transfers</a>
          <a href="#children">Children</a>
          <a href="#incidents">Security incidents</a>
          <a href="#regulatory">Regulatory posture</a>
          <a href="#liability">Liability</a>
          <a href="#changes">Changes</a>
          <a href="#contact">Contact</a>
        </nav>

        <section id="who">
          <h2><span>01</span>Who runs FinMe</h2>
          <p>
            FinMe is built and operated by Collins Shibambo,
            an individual developer based in South Africa - not a registered company, and not a
            bank, credit provider, or financial institution of any kind. There is no separate
            legal entity behind FinMe; the person you're dealing with when you use it is the
            person who wrote it.
          </p>
        </section>

        <section id="collect">
          <h2><span>02</span>What FinMe collects</h2>
          <p>FinMe collects only what it needs to do the job you're asking it to do:</p>
          <ul>
            <li><strong>Account information</strong>: your email address, a display name you choose, and a securely hashed password (never your actual password) if you register that way.</li>
            <li><strong>Financial data you provide</strong>: transactions extracted from a bank statement you upload, a receipt photo you upload, or a cash purchase you type in - date, merchant, amount, category, description, and payment method.</li>
            <li><strong>Credit data you choose to enter</strong>: entirely optional. If you switch on the credit section, the account names, balances, limits, payment statuses, and score readings you type in yourself.</li>
            <li><strong>Nothing else</strong>: no location tracking, no device fingerprinting, no advertising identifiers, no analytics pixels. FinMe doesn't run any third-party analytics or advertising script.</li>
          </ul>
          <p>
            FinMe never asks for a card number, bank login, or any payment detail, anywhere in
            the app. There is nothing to pay - it's free.
          </p>
        </section>

        <section id="use">
          <h2><span>03</span>How your information is used</h2>
          <p>Strictly to provide the service back to you:</p>
          <ul>
            <li>To extract and categorise transactions from a statement or receipt you upload.</li>
            <li>To show you your own spending, budgets, calendar view, and (if you use it) credit position.</li>
            <li>To generate the plain-language recommendations and credit-utilisation narration you see - written from figures calculated in code, never invented by a model (see the Credit section's disclaimer, always shown alongside any plan).</li>
            <li>To send you the account emails you'd expect - verifying your email address, and a weekly spending summary if that feature is enabled.</li>
          </ul>
          <p>
            FinMe does not use your financial data for advertising, does not build a profile of
            you for any purpose beyond the app itself, and does not sell, rent, or otherwise
            share your data with data brokers or marketers.
          </p>
        </section>

        <section id="ai">
          <h2><span>04</span>AI providers involved</h2>
          <p>
            Reading a statement or receipt and turning it into structured transactions uses an AI
            model - there's no way around that for handwriting-free extraction from a PDF or
            photo. The providers in that chain, and why they were chosen:
          </p>
          <ul>
            <li><strong>Groq</strong> - configured with Zero Data Retention.</li>
            <li><strong>OpenRouter</strong> - configured with account-wide Zero Data Retention.</li>
            <li><strong>Cloudflare Workers AI</strong> - does not train on submitted content without explicit consent, which FinMe does not give.</li>
          </ul>
          <p>
            None of these providers train their models on what you submit, and none of them are
            asked to retain it beyond processing the request. A separate provider, Google Gemini,
            is used only in development, only for generic content with no real user data in it -
            its free tier permits training on submitted content, which is exactly why it's
            excluded from every code path that touches real financial data.
          </p>
          <p>
            If you sign in with Google, that login is handled entirely by Google's own OAuth
            flow - FinMe receives your email address and the display name on your Google account,
            nothing more, and never sees your Google password.
          </p>
        </section>

        <section id="redaction">
          <h2><span>05</span>What gets redacted before any of this happens</h2>
          <p>
            Before any statement text reaches an AI provider, FinMe strips out account numbers,
            ID numbers, and personal names. The model only ever sees the fields it actually needs
            to do its job - date, merchant, amount, description, and payment method - not the
            fuller document you uploaded. This redaction step runs locally, before any external
            request is made, independent of and in addition to the providers' own zero-retention
            settings above.
          </p>
        </section>

        <section id="cookies">
          <h2><span>06</span>Cookies</h2>
          <p>
            FinMe sets exactly one cookie, and only if you sign in with Google: a short-lived,
            strictly necessary session cookie Google's login flow needs to track the handshake.
            It's marked HttpOnly so it can't be read by page scripts. FinMe does not set any
            tracking, analytics, or advertising cookie. Your session itself - staying logged in
            between visits - is handled by your browser's local storage, not a cookie.
          </p>
        </section>

        <section id="security">
          <h2><span>07</span>Storage &amp; security</h2>
          <ul>
            <li>Every user's data is isolated at the database-query level - no request can return another user's records, by design, not just by convention.</li>
            <li>Passwords are hashed, never stored or logged in plain text.</li>
            <li>Sign-in uses short-lived signed tokens, not a server-side session store.</li>
            <li>Data is stored in a MySQL database on Azure (in production) - not on a personal computer or a consumer file-sharing service.</li>
          </ul>
          <p>
            This describes real, implemented practices, not aspirational ones. It is not a claim
            that FinMe is unhackable - see <a href="#incidents">Security incidents</a> below for
            what happens if that were ever tested.
          </p>
        </section>

        <section id="rights">
          <h2><span>08</span>Your rights, and how to use them</h2>
          <p>These aren't promises for the future - every one of these is a real, working feature today:</p>
          <ul>
            <li><strong>Access</strong> - everything FinMe holds about your spending and credit is visible in the app itself; nothing is collected that you can't already see.</li>
            <li><strong>Correction</strong> - edit any transaction, your display name, or any credit account directly, any time.</li>
            <li><strong>Export</strong> - download your transactions as a CSV file from the Dashboard, filtered to whatever you're currently looking at.</li>
            <li><strong>Deletion</strong> - from Settings, either clear all your financial data while keeping your account, or delete the account and everything in it, permanently. Both require typing your email to confirm.</li>
          </ul>
          <p>
            You're also entitled to lodge a complaint with South Africa's Information Regulator
            if you believe your information has been mishandled.
          </p>
        </section>

        <section id="retention">
          <h2><span>09</span>How long data is kept</h2>
          <p>
            Your data is kept for as long as your account exists, so the app can keep showing it
            to you. Deleting a transaction, clearing your data, or deleting your account removes
            it immediately from the database - there is no separate backup archive it lingers in
            once you've deleted it through the app.
          </p>
        </section>

        <section id="transfers">
          <h2><span>10</span>International data transfers</h2>
          <p>
            The AI providers used to read a statement or receipt (see above) run on infrastructure
            outside South Africa. That means the specific transaction fields sent for extraction -
            never the raw statement, and never account numbers, ID numbers, or names - are
            processed abroad for the moments it takes to structure them, under each provider's
            zero/minimal-retention configuration.
          </p>
        </section>

        <section id="children">
          <h2><span>11</span>Children's privacy</h2>
          <p>
            FinMe isn't directed at children and isn't designed for use by anyone under 18. If
            you believe a child has created an account, contact{" "}
            ntsobokwanec@gmail.com and it will be removed.
          </p>
        </section>

        <section id="incidents">
          <h2><span>12</span>Security incidents</h2>
          <p>
            No system is unbreakable, and it would be dishonest to claim otherwise. If a security
            incident were ever to expose your data, you would be told - directly, by email, without
            undue delay, describing what happened and what it means for you - consistent with
            South African data protection law's notification requirements. This is a genuine
            commitment, not a formality: there is currently no dedicated incident-response team
            behind this project (it's one developer), so "without undue delay" means as soon as
            it is discovered and understood well enough to explain honestly, not instantly.
          </p>
        </section>

        <section id="regulatory">
          <h2><span>13</span>Regulatory posture - stated plainly</h2>
          <p>
            FinMe is designed with the core principles of South Africa's Protection of Personal
            Information Act (POPIA) in mind - collecting only what's needed, using it only for
            the purpose it was given for, and applying real security safeguards (see above).
          </p>
          <p>
            What that does <strong>not</strong> mean: FinMe does not currently hold formal POPIA
            certification, has not appointed a registered Information Officer, and does not hold
            any data-protection certification such as ISO 27001 or SOC 2. This is an honest,
            current-stage description, not a claim of certified compliance - if that changes,
            this section will say so specifically, not vaguely.
          </p>
          <p>
            HIPAA (a United States law governing health records) does not apply to FinMe: this app
            collects no health information of any kind, and is not a healthcare provider, health
            plan, or business associate under that law. It is not referenced elsewhere in this
            policy because it has no bearing on this product.
          </p>
        </section>

        <section id="liability">
          <h2><span>14</span>Limitation of liability</h2>
          <p>
            FinMe is provided "as is," free of charge, by an individual developer - not a company
            with a support desk or an insurance policy behind it. To the fullest extent permitted
            by law, FinMe and its developer are not liable for indirect, incidental, or
            consequential loss arising from your use of the app, including decisions made based
            on figures, recommendations, or credit-utilisation estimates it shows you.
          </p>
          <p>
            Nothing in this policy limits liability where the law does not allow it to be
            limited - including for gross negligence, wilful misconduct, or any protection South
            African law grants you that cannot be waived by agreement.
          </p>
        </section>

        <section id="changes">
          <h2><span>15</span>Changes to this policy</h2>
          <p>
            If this policy changes in a way that materially affects how your data is handled,
            the "Last updated" date above will change, and where practical, a note will be shown
            in the app. Continuing to use FinMe after a change means you've seen and accepted it.
          </p>
        </section>

        <section id="contact" className="legal-contact">
          <h2 style={{ border: "none", marginBottom: 8, paddingBottom: 0 }}><span>16</span>Contact</h2>
          <p style={{ marginBottom: 0 }}>
            Questions about this policy, or a request to access, correct, export, or delete your
            data beyond what Settings already lets you do yourself: ntsobokwanec@gmail.com.
          </p>
        </section>
      </div>
    </div>
  );
}
