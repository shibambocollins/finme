import { useEffect } from "react";

/**
 * Sets the browser tab title for as long as this page is mounted. Before this, every page
 * shared the one static title in index.html ("FinMe") - the tab, browser history, and any
 * bookmark all said the same generic thing regardless of which screen was actually open.
 */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title;
  }, [title]);
}
