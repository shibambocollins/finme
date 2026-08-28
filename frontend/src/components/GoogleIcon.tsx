/** Google's standard multicolour "G" mark, inlined as SVG - no external asset request, no font
 *  icon dependency, and it survives the artifact/build pipeline exactly like any other markup. */
export function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      <path
        fill="#4285F4"
        d="M17.64 9.2045c0-.6381-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2581h2.9087c1.7018-1.5668 2.6836-3.8741 2.6836-6.615z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.4673-.806 5.9564-2.1805l-2.9087-2.2581c-.8059.5405-1.8368.8618-3.0477.8618-2.3441 0-4.3286-1.5832-5.0364-3.7104H.9573v2.3318C2.4382 15.9832 5.4818 18 9 18z"
      />
      <path
        fill="#FBBC05"
        d="M3.9636 10.7118C3.7827 10.1618 3.6818 9.5741 3.6818 8.9727s.1009-1.1891.2818-1.7391V4.9018H.9573C.3477 6.1132 0 7.4759 0 8.9727s.3477 2.8595.9573 4.0709l3.0063-2.3318z"
      />
      <path
        fill="#EA4335"
        d="M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.3459l2.5814-2.5814C13.4632.8918 11.4259 0 9 0 5.4818 0 2.4382 2.0168.9573 4.9018l3.0063 2.3318C4.6714 5.1627 6.6559 3.5795 9 3.5795z"
      />
    </svg>
  );
}
