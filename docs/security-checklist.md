# Security Checklist — OWASP MASVS Alignment

Este checklist documenta cómo el SDK `core-data-platform` cumple con las categorías relevantes del
[OWASP Mobile Application Security Verification Standard (MASVS)](https://mas.owasp.org/MASVS/).

---

## MASVS-NETWORK — Comunicación Segura

| Control | Estado | Implementación |
|---|---|---|
| **N-1**: Solo HTTPS en producción | ✅ Enforced | `NetworkConfig` rechaza URLs `http://` por defecto. `allowInsecureConnections = true` solo para desarrollo local. |
| **N-2**: Certificate Pinning | ✅ Implementado | `KtorHttpEngine.create(config, trustPolicy)` — Android vía `OkHttp CertificatePinner`, iOS vía `handleChallenge` + `SecTrust`. |
| **N-3**: Sin certificados custom aceptados | ✅ By design | `DefaultTrustPolicy` evalúa trust estándar del sistema antes de verificar pins. No se aceptan CA roots custom. |
| **N-4**: Pinning con backup pin | 📋 Recomendado | `CertificatePin` acepta múltiples pins por host. Documentado en quickstarts como práctica obligatoria. |

### Guardrails implementados
- `NetworkConfig.init` lanza `IllegalArgumentException` si `baseUrl` no usa `https://` (a menos que `allowInsecureConnections = true`).
- `TrustPolicy` es parámetro explícito de `KtorHttpEngine.create()` — no hay pinning implícito ni accidental.
- iOS: evaluación estándar de confianza (`SecTrustEvaluateWithError`) + comparación SHA-256 de certificados.
- Android: `CertificatePinner` de OkHttp con formato estándar `sha256/<base64>`.

---

## MASVS-STORAGE — Almacenamiento Seguro de Datos

| Control | Estado | Implementación |
|---|---|---|
| **S-1**: Credenciales en almacenamiento seguro | ✅ Enforced | `SecretStore` es la única vía de persistencia. Android: `EncryptedSharedPreferences` + Keystore. iOS: Keychain Services con `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. |
| **S-2**: No secretos en logs | ✅ Enforced | `Credential.toString()`, `SessionCredentials.toString()`, `HttpRequest.toString()`, `RawResponse.toString()` — todos redactan valores sensibles con `██`. |
| **S-3**: No secretos en crash reports | ✅ By design | Los `toString()` redactados aseguran que las excepciones y stack traces no contengan tokens. |
| **S-4**: No secretos en backups | ✅ Platform | Android: `EncryptedSharedPreferences` cifra el contenido. iOS: Keychain items con `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` no se incluyen en backups no cifrados. |

### Tipos con `toString()` redactado
| Clase | Output |
|---|---|
| `Credential.Bearer` | `Bearer(token=██)` |
| `Credential.ApiKey` | `ApiKey(headerName=X-API-Key, key=██)` |
| `Credential.Basic` | `Basic(username=██, password=██)` |
| `Credential.Custom` | `Custom(type=oauth, properties=[keys only])` |
| `SessionCredentials` | `SessionCredentials(credential=Bearer(token=██), refreshToken=██, ...)` |
| `HttpRequest` | `HttpRequest(method=GET, path=/users, headers=[Authorization], ...)` |
| `RawResponse` | `RawResponse(statusCode=200, headers=[Set-Cookie], body=1234 bytes)` |

---

## MASVS-PRIVACY — Protección de Datos Sensibles en Logging

| Control | Estado | Implementación |
|---|---|---|
| **P-1**: Header sanitization por defecto | ✅ Enforced | `LoggingObserver` usa `REDACT_ALL` por defecto — todos los valores de headers se reemplazan con `██`. El consumidor debe explícitamente proveer un sanitizer para ver valores. |
| **P-2**: DefaultLogSanitizer | ✅ Disponible | `security-core` provee `DefaultLogSanitizer` que redacta selectivamente headers y keys sensibles (`authorization`, `cookie`, `token`, `password`, etc.). |
| **P-3**: Query params no en métricas/traces | ✅ Enforced | `MetricsObserver` y `TracingObserver` strip query parameters de los paths antes de emitir tags (`sanitizePath`). |
| **P-4**: NetworkLogger NOOP por defecto | ✅ By design | El SDK es silencioso por defecto — `NetworkLogger.NOOP` no imprime nada. Solo loguea si el consumidor configura un logger. |

### Configuración recomendada
```kotlin
// SEGURO: usa DefaultLogSanitizer para logging selectivo
val sanitizer = DefaultLogSanitizer()
val observer = LoggingObserver(
    logger = myLogger,
    headerSanitizer = { key, value -> sanitizer.sanitize(key, value) }
)

// INSEGURO: nunca hacer esto en producción
val unsafeObserver = LoggingObserver(
    logger = myLogger,
    headerSanitizer = { _, value -> value } // ⚠️ expone tokens
)
```

---

## MASVS-AUTH — Manejo de Credenciales

| Control | Estado | Implementación |
|---|---|---|
| **A-1**: Credential lifecycle controlado | ✅ Enforced | `SessionController` gestiona el ciclo de vida completo: `start → refresh → end/invalidate`. |
| **A-2**: Invalidación en 401 | ✅ Habilitado | `CredentialProvider.invalidate()` delega a `SessionController.invalidate()`. Diseñado para uso desde auth interceptors. |
| **A-3**: Refresh token protegido | ✅ Enforced | Almacenado exclusivamente en `SecretStore` (cifrado). Nunca expuesto en `toString()`. |
| **A-4**: Credential nunca en headers de log | ✅ Enforced | `HttpRequest.toString()` solo muestra keys de headers. `LoggingObserver` redacta values por defecto. |
| **A-5**: Limpieza al logout | ✅ Enforced | `endSession()` e `invalidate()` eliminan todas las claves de credenciales de `SecretStore`. |

---

## MASVS-CRYPTO — Criptografía

| Control | Estado | Implementación |
|---|---|---|
| **C-1**: Algoritmos estándar | ✅ Platform | Android: AES-256-GCM vía `EncryptedSharedPreferences`. iOS: protección de hardware vía Secure Enclave. |
| **C-2**: SHA-256 para cert pinning | ✅ Implementado | Hash de certificados DER con SHA-256 (puro Kotlin en iOS, OkHttp en Android). |
| **C-3**: No crypto custom | ✅ By design | El SDK delega toda la criptografía a las APIs de plataforma (Keystore, Keychain). |

---

## Tests de Seguridad

Los siguientes tests validan que los guardrails funcionan correctamente:

### `security-core` — `SecurityGuardrailsTest`
- `bearerToString_doesNotExposeToken`
- `apiKeyToString_doesNotExposeKey`
- `basicToString_doesNotExposeUsernameOrPassword`
- `customToString_doesNotExposePropertyValues`
- `sessionCredentialsToString_doesNotExposeRefreshToken`
- `sessionCredentialsToString_showsNullRefreshToken`

### `network-core` — `NetworkSecurityGuardrailsTest`
- `networkConfig_rejectsPlaintextHttp`
- `networkConfig_acceptsHttps`
- `networkConfig_allowsInsecureWhenExplicitlyOptedIn`
- `networkConfig_rejectsBlankUrl`
- `httpRequestToString_doesNotExposeHeaderValues`
- `httpRequestToString_doesNotExposeQueryParamValues`
- `rawResponseToString_doesNotExposeHeaderValues`
- `loggingObserver_defaultSanitizer_redactsAllHeaders`

---

## Reglas para contribuidores

1. **Nunca agregar `toString()` a clases que contengan secretos sin redacción explícita.**
2. **Nunca usar `http://` en `NetworkConfig` sin `allowInsecureConnections = true`.**
3. **Nunca crear un `LoggingObserver` con `headerSanitizer = { _, v -> v }` en producción.**
4. **Todo nuevo observer debe usar `sanitizePath()` para strip query params.**
5. **Todo nuevo tipo que contenga credentials debe override `toString()` con redacción.**
6. **Los tests de seguridad deben pasar antes de merge — son guardrails, no opcionales.**

---

## Referencias

- [OWASP MASVS v2.0](https://mas.owasp.org/MASVS/)
- [OWASP MASTG](https://mas.owasp.org/MASTG/)
- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)
