// End-to-end tests (S3-T01, see e2e/README.md): the app talks to the isolated test Gateway on 9080,
// never to the everyday stack on 8080. Built with `ng build --configuration e2e`.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:9080',
};
