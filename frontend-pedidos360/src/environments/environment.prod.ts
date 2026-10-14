// Valores de PRODUCCION (ng build usa este archivo en lugar de environment.ts).
// Reemplaza los CAMBIAR por los de tu despliegue en AWS y registra la redirectUri en Azure AD
// (App Registration del frontend -> Authentication -> Single-page application).
export const environment = {
  production: true,

  msal: {
    clientId: '6d1e9550-5b4e-4390-aceb-edf4ab9bd2ef',
    authority: 'https://login.microsoftonline.com/726876d2-e741-443b-91af-5309677e4def',
    redirectUri: 'https://CAMBIAR-url-del-frontend/auth/callback',
    postLogoutRedirectUri: 'https://CAMBIAR-url-del-frontend/login'
  },

  apiScope: 'api://c7f6f983-e84b-4d1b-8f5a-da6dcac01435/access_as_user',

  // Invoke URL de AWS API Gateway, sin barra final. Ej: https://abc123.execute-api.us-east-1.amazonaws.com
  apiBaseUrl: 'https://CAMBIAR-id.execute-api.us-east-1.amazonaws.com'
};
