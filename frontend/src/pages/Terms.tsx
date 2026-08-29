import { Link } from "react-router-dom";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import "./Legal.css";

export function Terms() {
  useDocumentTitle("Terms & Conditions | FinMe");

  return (
    <div className="legal-page">
      <header className="legal-header">
        <Link to="/" className="legal-header__brand">
          Fin<span>Me</span>
        </Link>
        <Link to="/">Back to FinMe</Link>
      </header>

      <div className="legal-content">
        <h1>Terms &amp; Conditions</h1>
        <p className="legal-updated">Last updated: 28 August 2026</p>

        <p className="legal-intro">
          These terms are written in plain language on purpose. If a sentence here doesn't
          match how FinMe actually behaves, treat that as a mistake to report to{" "}
          ntsobokwanec@gmail.com, not the last word on what's true.
        </p>

        <nav className="legal-toc" aria-label="Sections">
          <a href="#acceptance">Acceptance</a>
          <a href="#what">What FinMe is</a>
          <a href="#eligibility">Eligibility</a>
          <a href="#account">Your account</a>
          <a href="#free">It's free</a>
          <a href="#use">Acceptable use</a>
          <a href="#ai">AI-generated content</a>
          <a href="#ownership">Your data</a>
          <a href="#availability">Service availability</a>
          <a href="#termination">Termination</a>
          <a href="#warranties">No warranties</a>
          <a href="#liability">Liability</a>
          <a href="#indemnity">Indemnification</a>
          <a href="#law">Governing law</a>
          <a href="#changes">Changes</a>
          <a href="#contact">Contact</a>
        </nav>

        <section id="acceptance">
          <h2><span>01</span>Acceptance of these terms</h2>
          <p>
            By creating an account or using FinMe, you agree to these terms. If you don't agree
            to them, the only recourse is not to use the app - there's no paid tier or contract
            underneath this, just this agreement.
          </p>
        </section>

        <section id="what">
          <h2><span>02</span>What FinMe is - and isn't</h2>
          <p>
            FinMe is a personal finance and credit-health tracker: it reads statements, receipts,
            and typed entries you give it, organises them, and shows you patterns in your own
            spending and (optionally) your credit position.
          </p>
          <p>FinMe is <strong>not</strong>:</p>
          <ul>
            <li>A bank, a credit provider, or any kind of financial institution.</li>
            <li>Affiliated with, endorsed by, or connected to your bank or any bank.</li>
            <li>Able to hold, move, or transact your money in any way.</li>
            <li>Able to contact a credit bureau, or change your actual credit score.</li>
            <li>A source of financial, credit, tax, or legal advice - the recommendations it shows are narration of figures it calculated, not professional advice.</li>
          </ul>
        </section>

        <section id="eligibility">
          <h2><span>03</span>Eligibility</h2>
          <p>
            You need to be at least 18 to create a FinMe account. By registering, you're
            confirming that's true, and that the information you provide (your email, and any
            financial data you choose to upload or type) is your own.
          </p>
        </section>

        <section id="account">
          <h2><span>04</span>Your account</h2>
          <p>
            You're responsible for keeping your login credentials to yourself and for anything
            that happens under your account. If you registered with a password, choose one you
            don't reuse elsewhere. If you believe someone else has accessed your account, change
            your password (or, if you signed in with Google, secure your Google account) and
            contact ntsobokwanec@gmail.com.
          </p>
        </section>

        <section id="free">
          <h2><span>05</span>It's free</h2>
          <p>
            FinMe does not charge for any feature it currently offers, and does not ask for a
            card or payment number anywhere in the app. If that ever changes for some future
            feature, it will be a clear, separate choice you make - never a silent charge.
          </p>
        </section>

        <section id="use">
          <h2><span>06</span>Acceptable use</h2>
          <p>You agree not to:</p>
          <ul>
            <li>Upload someone else's financial documents or data without their permission.</li>
            <li>Attempt to access another user's account or data, or probe the app for security weaknesses without permission to do so.</li>
            <li>Use FinMe to process data on behalf of a business, in a way that would require business-grade data-handling FinMe doesn't provide - it's built for single-user personal use.</li>
            <li>Upload malicious files, or attempt to overload or disrupt the service.</li>
          </ul>
        </section>

        <section id="ai">
          <h2><span>07</span>AI-generated content</h2>
          <p>
            Category suggestions, spending recommendations, and the credit-utilisation plan are
            written by an AI model narrating figures FinMe calculated in code - the numbers are
            always deterministic, the sentences around them are not guaranteed to be perfectly
            phrased or complete. The credit section carries a standing disclaimer for exactly
            this reason: following its suggestions does not guarantee any change to your actual
            credit score. Treat anything AI-narrated as a starting point for your own judgement,
            not a final answer.
          </p>
        </section>

        <section id="ownership">
          <h2><span>08</span>Your data is yours</h2>
          <p>
            The financial data you upload or enter belongs to you, not FinMe. FinMe's role is to
            store it, process it to extract and categorise it, and show it back to you - not to
            claim any ownership over it. You can export it or delete it at any time, from
            Settings, exactly as described in the Privacy Policy.
          </p>
        </section>

        <section id="availability">
          <h2><span>09</span>Service availability</h2>
          <p>
            FinMe is a personal project under active, ongoing development, run by one developer.
            There is no guaranteed uptime, no service-level agreement, and features may change,
            break, or be temporarily unavailable while being worked on. Statement extraction in
            particular depends on third-party AI providers' own availability and rate limits, and
            can occasionally take longer than expected.
          </p>
          <p>
            Account verification and other emails are sent through a third-party mail relay and
            can be filtered by strict institutional mail systems - university, work, and
            government addresses filter harder than personal email as a general rule, not because
            of anything specific to any one organisation. A personal email address (Gmail,
            Outlook, and similar) is currently the more reliable choice for receiving them.
          </p>
        </section>

        <section id="termination">
          <h2><span>10</span>Termination</h2>
          <p>
            You can stop using FinMe and delete your account at any time from Settings - this is
            immediate and permanent, not a request that has to be processed. FinMe may suspend or
            remove an account that violates the acceptable use section above.
          </p>
        </section>

        <section id="warranties">
          <h2><span>11</span>No warranties</h2>
          <p>
            FinMe is provided "as is" and "as available," without warranties of any kind, express
            or implied - including, to the extent permitted by law, any implied warranty of
            merchantability, fitness for a particular purpose, or non-infringement. Extraction
            accuracy is measured, not assumed, but no extraction pipeline is perfect - always
            check what FinMe pulled from a statement or receipt against the original.
          </p>
        </section>

        <section id="liability">
          <h2><span>12</span>Limitation of liability</h2>
          <p>
            To the fullest extent permitted by law, FinMe and its developer are not liable for
            indirect, incidental, or consequential loss arising from your use of the app,
            including financial decisions made based on figures, categorisations, or
            recommendations it shows you. Nothing in these terms limits liability where the law
            does not allow it to be limited - including for gross negligence, wilful misconduct,
            or any protection South African consumer or data protection law grants you that
            cannot be waived by agreement.
          </p>
        </section>

        <section id="indemnity">
          <h2><span>13</span>Indemnification</h2>
          <p>
            You agree to be responsible for your own misuse of FinMe - for example, uploading
            data you didn't have the right to upload, or using the app in a way that breaches the
            acceptable use section above - to the extent that misuse causes loss to FinMe's
            developer or a third party.
          </p>
        </section>

        <section id="law">
          <h2><span>14</span>Governing law</h2>
          <p>
            These terms are governed by the laws of South Africa. Nothing here is intended to
            exclude any right you have under South African consumer protection law that cannot
            lawfully be excluded.
          </p>
        </section>

        <section id="changes">
          <h2><span>15</span>Changes to these terms</h2>
          <p>
            If these terms change in a way that materially affects your rights, the "Last
            updated" date above will change, and where practical, a note will be shown in the
            app. Continuing to use FinMe after a change means you've seen and accepted it.
          </p>
        </section>

        <section id="contact" className="legal-contact">
          <h2 style={{ border: "none", marginBottom: 8, paddingBottom: 0 }}><span>16</span>Contact</h2>
          <p style={{ marginBottom: 0 }}>
            Questions about these terms: ntsobokwanec@gmail.com.
          </p>
        </section>
      </div>
    </div>
  );
}
