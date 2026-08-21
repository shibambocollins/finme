import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

const MAPBOX_TOKEN = import.meta.env.VITE_MAPBOX_TOKEN as string | undefined;
const EXACT_COLOR = "#aa3bff";
const APPROXIMATE_COLOR = "#e0a33b";

export interface SpendLocation {
  merchant: string;
  amount: number;
  latitude: number;
  longitude: number;
  approximate: boolean;
}

interface SpendMapProps {
  locations: SpendLocation[];
}

/**
 * approximate=true pins came from TransactionGeocoder's merchant-name+locationHint fallback
 * (a plausible branch, not necessarily the one visited) rather than an exact receipt address -
 * shown in a visibly different color so the map never overstates what it actually knows.
 */
export function SpendMap({ locations }: SpendMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);

  useEffect(() => {
    if (!MAPBOX_TOKEN || !containerRef.current || locations.length === 0) {
      return;
    }

    mapboxgl.accessToken = MAPBOX_TOKEN;
    const map = new mapboxgl.Map({
      container: containerRef.current,
      style: "mapbox://styles/mapbox/dark-v11",
      center: [locations[0].longitude, locations[0].latitude],
      zoom: 11,
    });
    mapRef.current = map;

    const bounds = new mapboxgl.LngLatBounds();
    markersRef.current = locations.map((location) => {
      const approximateNote = location.approximate
        ? '<br/><span style="opacity:0.7">Approximate location</span>'
        : "";
      const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(
        `<strong>${escapeHtml(location.merchant)}</strong><br/>R${location.amount.toFixed(2)}${approximateNote}`
      );
      const marker = new mapboxgl.Marker({ color: location.approximate ? APPROXIMATE_COLOR : EXACT_COLOR })
        .setLngLat([location.longitude, location.latitude])
        .setPopup(popup)
        .addTo(map);
      bounds.extend([location.longitude, location.latitude]);
      return marker;
    });

    if (locations.length > 1) {
      map.fitBounds(bounds, { padding: 48, maxZoom: 14 });
    }

    return () => {
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
      map.remove();
      mapRef.current = null;
    };
  }, [locations]);

  if (!MAPBOX_TOKEN) {
    return (
      <p className="form-error">
        Map unavailable: VITE_MAPBOX_TOKEN is not configured.
      </p>
    );
  }

  if (locations.length === 0) {
    return <p>No geocoded spend locations yet - upload a receipt or statement to see it here.</p>;
  }

  const hasApproximate = locations.some((l) => l.approximate);

  return (
    <>
      <div ref={containerRef} className="spend-map" />
      {hasApproximate && (
        <p className="spend-map-legend">
          <span className="legend-dot" style={{ background: EXACT_COLOR }} /> Exact (receipt address)
          <span className="legend-dot" style={{ background: APPROXIMATE_COLOR }} /> Approximate (merchant + area)
        </p>
      )}
    </>
  );
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
