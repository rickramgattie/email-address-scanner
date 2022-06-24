# Email Address Scanner
Email Address Scanner

## Scan
- [x] Is Valid Email Address
- [x] Is Not a Temporary Email Address Domain
- [x] Has either an MX or A DNS Record

# Temporary Email Domains
The list of temporary email domains came from [Disposable-Emails](https://disposable-emails.github.io/).

# Build
1. `./gradlew clean`
2. `./gradlew shadowJar`
3. Upload output jar from `build/` to Lambda
