# Security Policy

## Supported Versions

The following active extensions and components in this repository are actively supported with security updates and maintenance patches.

| Component / Extension | Status | Supported |
| --------------------- | ------ | --------- |
| Active Extensions (`master` / `builds`) | Active | :white_check_mark: |
| Extension API Integration | Active | :white_check_mark: |
| Legacy / Deprecated Extensions | End-of-Life | :x: |

---

## Reporting a Vulnerability

We take the security, privacy, and integrity of our extensions and streaming parsers seriously. If you discover a security vulnerability, data leakage, or critical flaw:

1. **Do NOT create a public issue** on GitHub.
2. Report the vulnerability privately via **GitHub Security Advisories** or directly to the project maintainers.
3. Provide as much details as possible to help us reproduce and resolve the issue quickly:
   - Name of the affected extension (e.g., `CastleTvProvider`, `KatMovieHDProvider`, `MovieBoxProvider`).
   - Step-by-step reproduction guide or request payload snippet.
   - Severity and potential impact assessment.

### Vulnerability Response Timeline:
- **Initial Response**: Within 24 to 48 hours.
- **Fix & Deployment**: Once verified, patches are merged to `master` and automatically compiled and deployed to the `builds` branch via GitHub Actions.
