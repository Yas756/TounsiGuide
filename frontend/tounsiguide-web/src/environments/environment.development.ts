// Development configuration (used by `ng serve`).
// The frontend only talks to the API Gateway, never to a service port such as 8081.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8085',
};
