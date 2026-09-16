// Reemplaza los 3 valores marcados con <> por los de tu App Registration en Azure AD.
export const environment = {
  production: false,

  msal: {
    clientId: '6d1e9550-5b4e-4390-aceb-edf4ab9bd2ef',
    authority: 'https://login.microsoftonline.com/726876d2-e741-443b-91af-5309677e4def',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200/login'
  },

  // Scope de la API protegida (el que expusiste en el App Registration de la API)
  apiScope: 'api://c7f6f983-e84b-4d1b-8f5a-da6dcac01435/access_as_user',

  // En produccion apunta al invoke URL de AWS API Gateway; en local al BFF directo.
  apiBaseUrl: 'http://localhost:8080'
};
