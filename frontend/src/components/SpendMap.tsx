import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

const MAPBOX_TOKEN = import.meta.env.VITE_MAPBOX_TOKEN as string | undefined;
const ACCENT = "#aa3bff";

export interface SpendLocation {
  merchant: string;
  amount: number;
  latitude: number;
  longitude: number;
}

interface SpendMapProps {
  locations: SpendLocation[];
}

/**
 * Only ever plots transactions the backend actually geocoded from a receipt's printed
 * address (see DashboardSummaryResponse.locations) - statement-sourced spend has no address
 * to geocode, so most transactions never appear here. That's expected sparsity, not a bug.
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
      const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(
        `<strong>${escapeHtml(location.merchant)}</strong><br/>R${location.amount.toFixed(2)}`
      );
      const marker = new mapboxgl.Marker({ color: ACCENT })
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
    return <p>No geocoded spend locations yet - upload a receipt with a printed address to see it here.</p>;
  }

  return <div ref={containerRef} className="spend-map" />;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
