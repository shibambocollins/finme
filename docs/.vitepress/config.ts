import { defineConfig } from "vitepress";

export default defineConfig({
  title: "FinMe",
  description: "Personal finance and credit-health tracker with a measured AI extraction pipeline",
  lang: "en-ZA",
  cleanUrls: true,
  lastUpdated: true,

  // localhost URLs are instructions for the reader's own machine, not links this build could
  // ever resolve. Everything else is still checked, so a genuinely broken internal link
  // still fails the build.
  ignoreDeadLinks: [/^https?:\/\/localhost/],

  // Mermaid blocks are rendered client-side by the theme's markdown-it pipeline. Diagrams
  // live in the Markdown itself rather than as checked-in images, so they stay diffable and
  // cannot drift silently from the prose around them.
  markdown: {
    lineNumbers: true,
  },

  themeConfig: {
    nav: [
      { text: "Product", link: "/product/overview" },
      { text: "Get Started", link: "/getting-started/introduction" },
      { text: "Development", link: "/development/architecture" },
      { text: "API", link: "/api/overview" },
      { text: "Operations", link: "/operations/deployment" },
    ],

    sidebar: [
      {
        text: "Product",
        collapsed: false,
        items: [
          { text: "Overview", link: "/product/overview" },
          { text: "Features", link: "/product/features" },
          { text: "Users", link: "/product/users" },
          { text: "Roadmap", link: "/product/roadmap" },
        ],
      },
      {
        text: "Getting Started",
        collapsed: false,
        items: [
          { text: "Introduction", link: "/getting-started/introduction" },
          { text: "Quick Start", link: "/getting-started/quick-start" },
          { text: "Local Setup", link: "/getting-started/local-setup" },
        ],
      },
      {
        text: "Development",
        collapsed: false,
        items: [
          { text: "Architecture", link: "/development/architecture" },
          { text: "Frontend", link: "/development/frontend" },
          { text: "Backend", link: "/development/backend" },
          { text: "Database", link: "/development/database" },
          { text: "Conventions", link: "/development/conventions" },
          { text: "Known Risks", link: "/development/risks" },
        ],
      },
      {
        text: "API",
        collapsed: false,
        items: [
          { text: "Overview", link: "/api/overview" },
          { text: "Authentication", link: "/api/authentication" },
          { text: "Errors", link: "/api/errors" },
        ],
      },
      {
        text: "Operations",
        collapsed: false,
        items: [
          { text: "Deployment", link: "/operations/deployment" },
          { text: "Environments", link: "/operations/environments" },
          { text: "Monitoring", link: "/operations/monitoring" },
          { text: "Troubleshooting", link: "/operations/troubleshooting" },
        ],
      },
      {
        text: "Contributing",
        collapsed: true,
        items: [
          { text: "Contributing", link: "/contributing/contributing" },
          { text: "Git Workflow", link: "/contributing/git-workflow" },
          { text: "Pull Requests", link: "/contributing/pull-requests" },
        ],
      },
      {
        text: "Decisions",
        collapsed: true,
        items: [
          { text: "ADR-001 AI Provider Fallback Chain", link: "/decisions/ADR-001-ai-provider-fallback-chain" },
          { text: "ADR-002 Redaction Before AI Calls", link: "/decisions/ADR-002-redaction-before-ai-calls" },
          { text: "ADR-003 Deterministic Financial Math", link: "/decisions/ADR-003-deterministic-financial-math" },
          { text: "ADR-004 Asynchronous Ingestion", link: "/decisions/ADR-004-asynchronous-ingestion" },
          { text: "ADR-005 Azure SQL over MySQL", link: "/decisions/ADR-005-azure-sql-over-mysql" },
        ],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/shibambocollins/finme" },
    ],

    search: {
      provider: "local",
    },

    outline: { level: [2, 3] },

    footer: {
      message: "Built by Collins Shibambo",
      copyright: "FinMe",
    },
  },
});
