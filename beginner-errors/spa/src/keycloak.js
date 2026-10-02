import Keycloak from 'keycloak-js';

export const config = {
  // Error 3, variant c: scripts/break.sh 3c sets this to an outdated http://localhost:8080/auth
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8080',
  realm: 'demo',
  clientId: 'spa',
};

export const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8081';

export const keycloak = new Keycloak(config);

// Initialize once, outside React: StrictMode runs effects twice in dev,
// and a Keycloak instance can only be initialized once.
export const keycloakReady = keycloak.init({
  pkceMethod: 'S256',
  // No session-status iframe: keeps the DevTools console free of unrelated messages
  checkLoginIframe: false,
});
